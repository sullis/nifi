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
package org.apache.nifi.processors.aws.s3.encryption;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.ObjectMetadata;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;


public class TestS3EncryptionStrategies {

    private static final Region REGION = Region.US_EAST_1;
    private static final String KEY_ID = "key-id";

    private String randomKeyMaterial = "";

    private ObjectMetadata metadata = null;
    private PutObjectRequest putObjectRequest = null;
    private CreateMultipartUploadRequest initUploadRequest = null;
    private GetObjectRequest getObjectRequest = null;
    private UploadPartRequest uploadPartRequest = null;

    @BeforeEach
    public void setup() {
        byte[] keyRawBytes = new byte[32];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(keyRawBytes);
        randomKeyMaterial = Base64.encodeBase64String(keyRawBytes);

        metadata = ObjectMetadata.builder().build();
        putObjectRequest = PutObjectRequest.builder().build();
        initUploadRequest = CreateMultipartUploadRequest.builder().build();
        getObjectRequest = GetObjectRequest.builder().bucket("").key("").build();
        uploadPartRequest = UploadPartRequest.builder().build();
    }

    @Test
    public void testClientSideCEncryptionStrategy() {
        S3EncryptionStrategy strategy = new ClientSideCEncryptionStrategy();

        // This shows that the strategy builds a client:
        assertNotNull(strategy.createEncryptionClient(builder -> {
            builder.region(REGION);
        }, null, randomKeyMaterial));

        // This shows that the strategy does not modify the metadata or any of the requests:
        assertNull(metadata.sseAlgorithm());
        assertNull(putObjectRequest.sseAwsKeyManagementParams());
        assertNull(putObjectRequest.sseCustomerKey());

        assertNull(initUploadRequest.sseAwsKeyManagementParams());
        assertNull(initUploadRequest.sseCustomerKey());

        assertNull(getObjectRequest.sseCustomerKey());

        assertNull(uploadPartRequest.sseCustomerKey());
    }

    @Test
    public void testClientSideKMSEncryptionStrategy() {
        S3EncryptionStrategy strategy = new ClientSideKMSEncryptionStrategy();

        // This shows that the strategy builds a client:
        assertNotNull(strategy.createEncryptionClient(builder -> {
            builder.region(REGION);
        }, REGION, KEY_ID));

        // This shows that the strategy does not modify the metadata or any of the requests:
        assertNull(metadata.sseAlgorithm());
        assertNull(putObjectRequest.sseAwsKeyManagementParams());
        assertNull(putObjectRequest.sseCustomerKey());

        assertNull(initUploadRequest.sseAwsKeyManagementParams());
        assertNull(initUploadRequest.sseCustomerKey());

        assertNull(getObjectRequest.sseCustomerKey());

        assertNull(uploadPartRequest.sseCustomerKey());
    }

    @Test
    public void testServerSideCEncryptionStrategy() {
        S3EncryptionStrategy strategy = new ServerSideCEncryptionStrategy();

        // This shows that the strategy does *not* build a client:
        assertNull(strategy.createEncryptionClient(null, null, ""));

        // This shows that the strategy sets the SSE customer key as expected:
        strategy.configurePutObjectRequest(putObjectRequest, metadata, randomKeyMaterial);
        assertEquals(randomKeyMaterial, putObjectRequest.sseCustomerKey().key());
        assertNull(putObjectRequest.sseAwsKeyManagementParams());
        assertNull(metadata.sseAlgorithm());

        // Same for CreateMultipartUploadRequest:
        strategy.configureCreateMultipartUploadRequest(initUploadRequest, metadata, randomKeyMaterial);
        assertEquals(randomKeyMaterial, initUploadRequest.sseCustomerKey().key());
        assertNull(initUploadRequest.sseAwsKeyManagementParams());
        assertNull(metadata.sseAlgorithm());

        // Same for GetObjectRequest:
        strategy.configureGetObjectRequest(getObjectRequest, metadata, randomKeyMaterial);
        assertEquals(randomKeyMaterial, initUploadRequest.sseCustomerKey().key());
        assertNull(metadata.sseAlgorithm());

        // Same for UploadPartRequest:
        strategy.configureUploadPartRequest(uploadPartRequest, metadata, randomKeyMaterial);
        assertEquals(randomKeyMaterial, uploadPartRequest.sseCustomerKey().key());
        assertNull(metadata.sseAlgorithm());
    }

    @Test
    public void testServerSideKMSEncryptionStrategy() {
        S3EncryptionStrategy strategy = new ServerSideKMSEncryptionStrategy();

        // This shows that the strategy does *not* build a client:
        assertNull(strategy.createEncryptionClient(null, null, null));

        // This shows that the strategy sets the SSE KMS key id as expected:
        strategy.configurePutObjectRequest(putObjectRequest, metadata, KEY_ID);
        assertEquals(KEY_ID, putObjectRequest.sseAwsKeyManagementParams().awsKmsKeyId());
        assertNull(putObjectRequest.sseCustomerKey());
        assertNull(metadata.sseAlgorithm());

        // Same for CreateMultipartUploadRequest:
        strategy.configureCreateMultipartUploadRequest(initUploadRequest, metadata, KEY_ID);
        assertEquals(KEY_ID, initUploadRequest.sseAwsKeyManagementParams().awsKmsKeyId());
        assertNull(initUploadRequest.sseCustomerKey());
        assertNull(metadata.sseAlgorithm());
    }

    @Test
    public void testServerSideS3EncryptionStrategy() {
        S3EncryptionStrategy strategy = new ServerSideS3EncryptionStrategy();

        // This shows that the strategy does *not* build a client:
        assertNull(strategy.createEncryptionClient(null, null, null));

        // This shows that the strategy sets the SSE algorithm field as expected:
        strategy.configurePutObjectRequest(putObjectRequest, metadata, null);
        assertEquals(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION, metadata.sseAlgorithm());

        // Same for CreateMultipartUploadRequest:
        strategy.configureCreateMultipartUploadRequest(initUploadRequest, metadata, null);
        assertEquals(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION, metadata.sseAlgorithm());
    }

    @Test
    public void testNoOpEncryptionStrategy() {
        S3EncryptionStrategy strategy = new NoOpEncryptionStrategy();

        // This shows that the strategy does *not* build a client:
        assertNull(strategy.createEncryptionClient(null, "", ""));

        // This shows the request and metadata start with various null objects:
        assertNull(metadata.sseAlgorithm());
        assertNull(putObjectRequest.sseAwsKeyManagementParams());
        assertNull(putObjectRequest.sseCustomerKey());

        // Act:
        strategy.configurePutObjectRequest(putObjectRequest, metadata, "");

        // This shows that the request and metadata were not changed:
        assertNull(metadata.sseAlgorithm());
        assertNull(putObjectRequest.sseAwsKeyManagementParams());
        assertNull(putObjectRequest.sseCustomerKey());
    }
}
