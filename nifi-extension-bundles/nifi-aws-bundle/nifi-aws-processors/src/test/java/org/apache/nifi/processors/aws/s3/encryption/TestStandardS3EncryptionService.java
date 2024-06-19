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

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.ObjectMetadata;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.processors.aws.s3.AmazonS3EncryptionService;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.MockPropertyValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


public class TestStandardS3EncryptionService {
    private StandardS3EncryptionService service;
    private ConfigurationContext context;
    private String strategyName;
    private String keyIdOrMaterial;
    private String kmsRegion;

    @BeforeEach
    public void setup() throws InitializationException {
        service = new StandardS3EncryptionService();
        context = Mockito.mock(ConfigurationContext.class);

        strategyName = AmazonS3EncryptionService.STRATEGY_NAME_NONE;
        keyIdOrMaterial = "test-key-id";
        kmsRegion = "us-west-1";

        Mockito.when(context.getProperty(StandardS3EncryptionService.ENCRYPTION_STRATEGY)).thenReturn(new MockPropertyValue(strategyName));
        Mockito.when(context.getProperty(StandardS3EncryptionService.ENCRYPTION_VALUE)).thenReturn(new MockPropertyValue(keyIdOrMaterial));
        Mockito.when(context.getProperty(StandardS3EncryptionService.KMS_REGION)).thenReturn(new MockPropertyValue(kmsRegion));
        service.onConfigured(context);
    }

    @Test
    public void testServiceProperties() {
        assertEquals(service.getKmsRegion(), kmsRegion);
        assertEquals(service.getStrategyName(), strategyName);
    }

    @Test
    public void testCreateClientReturnsNull() {
        assertNull(service.createEncryptionClient(null));
    }

    @Test
    public void testRequests() {
        final ObjectMetadata metadata = ObjectMetadata.builder().build();
        final GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket("").key("").build();
        final CreateMultipartUploadRequest initUploadRequest = CreateMultipartUploadRequest.builder().build();
        final PutObjectRequest putObjectRequest = PutObjectRequest.builder().build();
        final UploadPartRequest uploadPartRequest = UploadPartRequest.builder().build();

        service.configureGetObjectRequest(getObjectRequest, metadata);
        assertNull(getObjectRequest.sseCustomerKey());
        assertNull(metadata.sseAlgorithm());

        service.configureUploadPartRequest(uploadPartRequest, metadata);
        assertNull(uploadPartRequest.sseCustomerKey());
        assertNull(metadata.sseAlgorithm());

        service.configurePutObjectRequest(putObjectRequest, metadata);
        assertNull(putObjectRequest.sseCustomerKey());
        assertNull(metadata.sseAlgorithm());

        service.configureCreateMultipartUploadRequest(initUploadRequest, metadata);
        assertNull(initUploadRequest.sseCustomerKey());
        assertNull(metadata.sseAlgorithm());
    }

    @Test
    public void testProperties() {
        List<PropertyDescriptor> properties = service.getSupportedPropertyDescriptors();
        assertEquals(3, properties.size());

        assertEquals(properties.get(0).getName(), StandardS3EncryptionService.ENCRYPTION_STRATEGY.getName());
        assertEquals(properties.get(1).getName(), StandardS3EncryptionService.ENCRYPTION_VALUE.getName());
        assertEquals(properties.get(2).getName(), StandardS3EncryptionService.KMS_REGION.getName());
    }
}
