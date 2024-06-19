/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.processors.aws.s3;

import com.amazonaws.auth.Signer;
import com.amazonaws.auth.SignerFactory;
import com.amazonaws.auth.SignerParams;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Region;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import com.amazonaws.services.s3.internal.AWSS3V4Signer;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.StorageClass;
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsRequest;
import software.amazon.awssdk.services.s3.model.MultipartUploadListing;
import software.amazon.awssdk.services.s3.model.ObjectMetadata;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.Tag;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.fileresource.service.api.FileResource;
import org.apache.nifi.fileresource.service.api.FileResourceService;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processors.aws.signer.AwsSignerType;
import org.apache.nifi.processors.aws.testutil.AuthUtils;
import org.apache.nifi.processors.aws.util.RegionUtilV1;
import org.apache.nifi.processors.transfer.ResourceTransferSource;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.nifi.processors.transfer.ResourceTransferProperties.FILE_RESOURCE_SERVICE;
import static org.apache.nifi.processors.transfer.ResourceTransferProperties.RESOURCE_TRANSFER_SOURCE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class TestPutS3Object {

    private TestRunner runner;
    private PutS3Object putS3Object;
    private S3Client mockS3Client;

    @BeforeEach
    public void setUp() {
        mockS3Client = mock(S3Client.class);
        putS3Object = new PutS3Object() {
            @Override
            protected S3Client createClient(final ProcessContext context, final AwsCredentialsProvider credentialsProvider, final Region region, final ClientOverrideConfiguration config,
                                                  final AwsClientBuilder.EndpointConfiguration endpointConfiguration) {
                return mockS3Client;
            }
        };
        runner = TestRunners.newTestRunner(putS3Object);
        AuthUtils.enableAccessKey(runner, "accessKeyId", "secretKey");

        // MockPropertyValue does not evaluate system properties, set it in a variable with the same name
        runner.setEnvironmentVariableValue("java.io.tmpdir", System.getProperty("java.io.tmpdir"));
    }

    @Test
    public void testPutSinglePartFromLocalFileSource() throws Exception {
        prepareTest();

        String serviceId = "fileresource";
        FileResourceService service = mock(FileResourceService.class);
        InputStream localFileInputStream = mock(InputStream.class);
        when(service.getIdentifier()).thenReturn(serviceId);
        long contentLength = 10L;
        when(service.getFileResource(anyMap())).thenReturn(new FileResource(localFileInputStream, contentLength));

        runner.addControllerService(serviceId, service);
        runner.enableControllerService(service);
        runner.setProperty(RESOURCE_TRANSFER_SOURCE, ResourceTransferSource.FILE_RESOURCE_SERVICE.getValue());
        runner.setProperty(FILE_RESOURCE_SERVICE, serviceId);

        runner.run();

        ArgumentCaptor<PutObjectRequest> captureRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3Client).putObject(captureRequest.capture());
        PutObjectRequest putObjectRequest = captureRequest.getValue();
        assertEquals(localFileInputStream, putObjectRequest.inputStream());
        assertEquals(putObjectRequest.metadata().contentLength(), contentLength);

        runner.assertAllFlowFilesTransferred(PutS3Object.REL_SUCCESS, 1);
    }

    @Test
    public void testPutSinglePart() {
        runner.setProperty("x-custom-prop", "hello");
        prepareTest();

        runner.run(1);

        ArgumentCaptor<PutObjectRequest> captureRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3Client, Mockito.times(1)).putObject(captureRequest.capture());
        PutObjectRequest request = captureRequest.getValue();
        assertEquals("test-bucket", request.bucketName());

        runner.assertAllFlowFilesTransferred(PutS3Object.REL_SUCCESS, 1);

        List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(PutS3Object.REL_SUCCESS);
        MockFlowFile ff0 = flowFiles.get(0);

        ff0.assertAttributeEquals(CoreAttributes.FILENAME.key(), "testfile.txt");
        ff0.assertContentEquals("Test Content");
        ff0.assertAttributeEquals(PutS3Object.S3_ETAG_ATTR_KEY, "test-etag");
        ff0.assertAttributeEquals(PutS3Object.S3_VERSION_ATTR_KEY, "test-version");
    }

    @Test
    public void testPutSinglePartException() {
        prepareTest();

        when(mockS3Client.putObject(Mockito.any(PutObjectRequest.class))).thenThrow(S3Exception.builder().build());

        runner.run(1);

        runner.assertAllFlowFilesTransferred(PutS3Object.REL_FAILURE, 1);
    }

    @Test
    public void testSignerOverrideOptions() {
        final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.builder().build();
        final ClientOverrideConfiguration config = ClientOverrideConfiguration.builder().build();
        final PutS3Object processor = new PutS3Object();
        final TestRunner runner = TestRunners.newTestRunner(processor);

        final List<AllowableValue> allowableSignerValues = PutS3Object.SIGNER_OVERRIDE.getAllowableValues();
        final String customSignerValue = AwsSignerType.CUSTOM_SIGNER.getValue(); // Custom Signer is tested separately

        for (AllowableValue allowableSignerValue : allowableSignerValues) {
            String signerType = allowableSignerValue.getValue();
            if (!signerType.equals(customSignerValue)) {
                runner.setProperty(PutS3Object.SIGNER_OVERRIDE, signerType);
                ProcessContext context = runner.getProcessContext();
                assertDoesNotThrow(() -> processor.createClient(context, credentialsProvider,
                        Region.getRegion(Region.DEFAULT_REGION), config, null));
            }
        }
    }

    @Test
    public void testObjectTags() {
        runner.setProperty(PutS3Object.OBJECT_TAGS_PREFIX, "tagS3");
        runner.setProperty(PutS3Object.REMOVE_TAG_PREFIX, "false");
        prepareTest();

        runner.run(1);

        ArgumentCaptor<PutObjectRequest> captureRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3Client, Mockito.times(1)).putObject(captureRequest.capture());
        PutObjectRequest request = captureRequest.getValue();

        List<Tag> tagSet = request.tagging().tagSet();

        assertEquals(1, tagSet.size());
        assertEquals("tagS3PII", tagSet.get(0).key());
        assertEquals("true", tagSet.get(0).value());
    }

    @Test
    public void testStorageClasses() {
        for (StorageClass storageClass : StorageClass.values()) {
            runner.setProperty(PutS3Object.STORAGE_CLASS, storageClass.name());
            prepareTest();

            runner.run(1);

            ArgumentCaptor<PutObjectRequest> captureRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(mockS3Client, Mockito.times(1)).putObject(captureRequest.capture());
            PutObjectRequest request = captureRequest.getValue();

            assertEquals(storageClass.toString(), request.storageClass());

            Mockito.reset(mockS3Client);
        }
    }

    @Test
    public void testFilenameWithNationalCharacters() {
        prepareTest("Iñtërnâtiônàližætiøn.txt");

        runner.run(1);

        ArgumentCaptor<PutObjectRequest> captureRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3Client, Mockito.times(1)).putObject(captureRequest.capture());
        PutObjectRequest request = captureRequest.getValue();

        Map<String, String> objectMetadata = request.metadata();
        assertEquals(URLEncoder.encode("Iñtërnâtiônàližætiøn.txt", UTF_8), objectMetadata.contentDisposition());
    }

    @Test
    public void testRegionFromFlowFileAttribute() {
        runner.setProperty(PutS3Object.OBJECT_TAGS_PREFIX, "tagS3");
        runner.setProperty(PutS3Object.REMOVE_TAG_PREFIX, "false");
        prepareTestWithRegionInAttributes("testfile.txt", "us-east-1");

        runner.run(1);

        runner.assertAllFlowFilesTransferred(PutS3Object.REL_SUCCESS, 1);
    }

    private void prepareTest() {
        prepareTest("testfile.txt");
    }

    private void prepareTest(String filename) {
        runner.setProperty(RegionUtilV1.S3_REGION, "ap-northeast-1");
        runner.setProperty(PutS3Object.BUCKET_WITHOUT_DEFAULT_VALUE, "test-bucket");
        runner.assertValid();

        Map<String, String> ffAttributes = new HashMap<>();
        ffAttributes.put("filename", filename);
        ffAttributes.put("tagS3PII", "true");
        runner.enqueue("Test Content", ffAttributes);

        initMocks();
    }

    private void prepareTestWithRegionInAttributes(String filename, String region) {
        runner.setProperty(RegionUtilV1.S3_REGION, "attribute-defined-region");
        runner.setProperty(PutS3Object.BUCKET_WITHOUT_DEFAULT_VALUE, "test-bucket");
        runner.assertValid();

        Map<String, String> ffAttributes = new HashMap<>();
        ffAttributes.put("s3.region", region);
        ffAttributes.put("filename", filename);
        ffAttributes.put("tagS3PII", "true");
        runner.enqueue("Test Content", ffAttributes);

        initMocks();
    }

    private void initMocks() {
        PutObjectResponse putObjectResult = PutObjectResponse.builder().build();
        putObjectResult.expirationTime(new Date());
        putObjectResult.metadata(ObjectMetadata.builder().build());
        putObjectResult.versionId("test-version");
        putObjectResult.eTag("test-etag");

        when(mockS3Client.putObject(Mockito.any(PutObjectRequest.class))).thenReturn(putObjectResult);

        MultipartUploadListing uploadListing = MultipartUploadListing.builder().build();
        when(mockS3Client.listMultipartUploads(Mockito.any(ListMultipartUploadsRequest.class))).thenReturn(uploadListing);
    }

    @Test
    public void testPersistenceFileLocationWithDefaultTempDir() {
        String dir = System.getProperty("java.io.tmpdir");

        executePersistenceFileLocationTest(StringUtils.appendIfMissing(dir, File.separator) + putS3Object.getIdentifier());
    }

    @Test
    public void testPersistenceFileLocationWithUserDefinedDirWithEndingSeparator() {
        String dir = StringUtils.appendIfMissing(new File("target").getAbsolutePath(), File.separator);
        runner.setProperty(PutS3Object.MULTIPART_TEMP_DIR, dir);

        executePersistenceFileLocationTest(dir + putS3Object.getIdentifier());
    }

    @Test
    public void testPersistenceFileLocationWithUserDefinedDirWithoutEndingSeparator() {
        String dir = StringUtils.removeEnd(new File("target").getAbsolutePath(), File.separator);
        runner.setProperty(PutS3Object.MULTIPART_TEMP_DIR, dir);

        executePersistenceFileLocationTest(dir + File.separator + putS3Object.getIdentifier());
    }

    private void executePersistenceFileLocationTest(String expectedPath) {
        prepareTest();

        runner.run(1);
        File file = putS3Object.getPersistenceFile();

        assertEquals(expectedPath, file.getAbsolutePath());
    }

    @Test
    public void testCustomSigner() {
        final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.builder().build();
        final ClientOverrideConfiguration config = ClientOverrideConfiguration.builder().build();
        final PutS3Object processor = new PutS3Object();
        final TestRunner runner = TestRunners.newTestRunner(processor);

        runner.setProperty(PutS3Object.SIGNER_OVERRIDE, AwsSignerType.CUSTOM_SIGNER.getValue());
        runner.setProperty(PutS3Object.S3_CUSTOM_SIGNER_CLASS_NAME, CustomS3Signer.class.getName());

        ProcessContext context = runner.getProcessContext();
        processor.createClient(context, credentialsProvider, Region.getRegion(Region.DEFAULT_REGION), config, null);

        final String signerName = config.getSignerOverride();
        assertNotNull(signerName);
        final Signer signer = SignerFactory.createSigner(signerName, new SignerParams("s3", "us-west-2"));
        assertNotNull(signer);
        assertSame(CustomS3Signer.class, signer.getClass());
    }

    public static class CustomS3Signer extends AWSS3V4Signer {

    }
}
