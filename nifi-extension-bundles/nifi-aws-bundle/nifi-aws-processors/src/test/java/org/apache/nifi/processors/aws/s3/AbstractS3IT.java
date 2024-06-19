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

import com.amazonaws.client.builder.AwsClientBuilder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3ExceptionClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.ObjectListing;
import software.amazon.awssdk.services.s3.model.ObjectMetadata;
import software.amazon.awssdk.services.s3.model.ObjectTagging;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3ObjectSummary;
import software.amazon.awssdk.services.s3.model.Tag;
import org.apache.nifi.processors.aws.testutil.AuthUtils;
import org.apache.nifi.processors.aws.util.RegionUtilV1;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Base class for S3 Integration Tests. Establishes a bucket and helper methods for creating test scenarios
 *
 * @see ITDeleteS3Object
 * @see ITFetchS3Object
 * @see ITPutS3Object
 * @see ITListS3
 */
public abstract class AbstractS3IT {
    private static final Logger logger = LoggerFactory.getLogger(AbstractS3IT.class);

    protected final static String SAMPLE_FILE_RESOURCE_NAME = "/hello.txt";
    protected final static String BUCKET_NAME = "test-bucket-" + System.currentTimeMillis();

    private static S3Client client;
    private static KmsClient kmsClient;
    private final List<String> addedKeys = new ArrayList<>();

    private static final DockerImageName localstackImage = DockerImageName.parse("localstack/localstack:latest");

    private static final LocalStackContainer localstack = new LocalStackContainer(localstackImage)
            .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.KMS);

    @BeforeAll
    public static void oneTimeSetup() {
        localstack.start();

        client = S3Client.builder()
                .endpointOverride(new AwsClientBuilder.EndpointConfiguration(localstack.getEndpoint().toString(), localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();

        kmsClient = KmsClient.builder()
                .endpointOverride(new AwsClientBuilder.EndpointConfiguration(localstack.getEndpoint().toString(), localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();

        final CreateBucketRequest request = CreateBucketRequest.builder().build();
        client.createBucket(request);
        client.deleteBucketEncryption(BUCKET_NAME);
    }

    @BeforeEach
    public void clearKeys() {
        addedKeys.clear();
    }

    @AfterEach
    public void emptyBucket() {
        if (!client.doesBucketExistV2(BUCKET_NAME)) {
            return;
        }

        ObjectListing objectListing = client.listObjects(BUCKET_NAME);
        while (true) {
            for (S3ObjectSummary objectSummary : objectListing.objectSummaries()) {
                client.deleteObject(BUCKET_NAME, objectSummary.key());
            }

            if (objectListing.isTruncated()) {
                objectListing = client.listNextBatchOfObjects(objectListing);
            } else {
                break;
            }
        }
    }

    @AfterAll
    public static void oneTimeTearDown() {
        try {
            if (client == null || !client.doesBucketExistV2(BUCKET_NAME)) {
                return;
            }

            DeleteBucketRequest dbr = DeleteBucketRequest.builder().build();
            client.deleteBucket(dbr);
        } catch (final S3ExceptionClient e) {
            logger.error("Unable to delete bucket {}", BUCKET_NAME, e);
        }
    }

    protected S3Client getClient() {
        return client;
    }

    protected KmsClient getKmsClient() {
        return kmsClient;
    }

    protected String getEndpointOverride() {
        return localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString();
    }

    protected static String getRegion() {
        return localstack.getRegion();
    }

    protected static void setSecureProperties(final TestRunner runner) throws InitializationException {
        AuthUtils.enableAccessKey(runner, localstack.getAccessKey(), localstack.getSecretKey());
    }

    protected void putTestFile(String key, File file) throws S3ExceptionClient {
        PutObjectRequest putRequest = PutObjectRequest.builder().build();
        client.putObject(putRequest);
    }

    protected void putTestFileEncrypted(String key, File file) throws S3ExceptionClient, FileNotFoundException {
        ObjectMetadata objectMetadata = ObjectMetadata.builder().build();
        objectMetadata.sseAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        PutObjectRequest putRequest = PutObjectRequest.builder().build();

        client.putObject(putRequest);
    }

    protected void putFileWithUserMetadata(String key, File file, Map<String, String> userMetadata) throws S3ExceptionClient, FileNotFoundException {
        ObjectMetadata objectMetadata = ObjectMetadata.builder().build();
        objectMetadata.userMetadata(userMetadata);
        PutObjectRequest putRequest = PutObjectRequest.builder().build();

        client.putObject(putRequest);
    }

    protected void waitForFilesAvailable() {
        for (final String key : addedKeys) {
            final long maxWaitTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10L);
            while (System.currentTimeMillis() < maxWaitTimestamp) {
                try {
                    client.getObject(BUCKET_NAME, key);
                } catch (final Exception e) {
                    try {
                        Thread.sleep(100L);
                    } catch (final InterruptedException ie) {
                        throw new AssertionError("Interrupted while waiting for files to become available", e);
                    }
                }
            }
        }
    }

    protected void putFileWithObjectTag(String key, File file, List<Tag> objectTags) {
        PutObjectRequest putRequest = PutObjectRequest.builder().build();
        putRequest.tagging(ObjectTagging.builder().build());
        client.putObject(putRequest);
    }

    protected Path getResourcePath(String resourceName) {
        Path path = null;

        try {
            path = Paths.get(getClass().getResource(resourceName).toURI());
        } catch (URISyntaxException e) {
           fail("Resource: " + resourceName + " does not exist" + e.getLocalizedMessage());
        }

        return path;
    }

    protected File getFileFromResourceName(String resourceName) {
        URI uri = null;
        try {
            uri = this.getClass().getResource(resourceName).toURI();
        } catch (URISyntaxException e) {
            fail("Cannot proceed without File : " + resourceName);
        }

        return new File(uri);
    }

    protected static String getKMSKey() {
        CreateKeyRequest cmkRequest = CreateKeyRequest.builder().description("CMK for unit tests").build();
        CreateKeyResponse cmkResult = kmsClient.createKey(cmkRequest);

        GenerateDataKeyRequest dekRequest = GenerateDataKeyRequest.builder().keyId(cmkResult.keyMetadata().keyId()).keySpec("AES_128").build();
        GenerateDataKeyResponse dekResult = kmsClient.generateDataKey(dekRequest);

        return dekResult.keyId();
    }


    protected TestRunner initRunner(final Class<? extends AbstractS3Processor> processorClass) {
        TestRunner runner = TestRunners.newTestRunner(processorClass);

        try {
            setSecureProperties(runner);
        } catch (InitializationException e) {
            Assertions.fail("Could not set security properties");
        }

        runner.setProperty(RegionUtilV1.S3_REGION, getRegion());
        runner.setProperty(AbstractS3Processor.ENDPOINT_OVERRIDE, getEndpointOverride());
        runner.setProperty(AbstractS3Processor.BUCKET_WITHOUT_DEFAULT_VALUE, BUCKET_NAME);

        return runner;
    }

}