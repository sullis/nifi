package org.apache.nifi.processors.aws.wag.client;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;


public class GenericApiGatewayClientBuilder {
    private String endpoint;
    private Region region;
    private AwsCredentialsProvider credentials;
    private ClientOverrideConfiguration clientConfiguration;
    private String apiKey;
    private SdkHttpClient httpClient;

    public GenericApiGatewayClientBuilder withEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public GenericApiGatewayClientBuilder withRegion(Region region) {
        this.region = region;
        return this;
    }

    public GenericApiGatewayClientBuilder withClientConfiguration(ClientOverrideConfiguration clientConfiguration) {
        this.clientConfiguration = clientConfiguration;
        return this;
    }

    public GenericApiGatewayClientBuilder withCredentials(AwsCredentialsProvider credentials) {
        this.credentials = credentials;
        return this;
    }

    public GenericApiGatewayClientBuilder withApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public GenericApiGatewayClientBuilder withHttpClient(SdkHttpClient client) {
        this.httpClient = client;
        return this;
    }

    public AwsCredentialsProvider getCredentials() {
        return credentials;
    }

    public String getApiKey() {
        return apiKey;
    }

    public SdkHttpClient getHttpClient() {
        return httpClient;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Region getRegion() {
        return region;
    }

    public ClientOverrideConfiguration getClientConfiguration() {
        return clientConfiguration;
    }

    public GenericApiGatewayClient build() {
        Validate.notEmpty(endpoint, "Endpoint");
        Validate.notNull(region, "Region");
        return new GenericApiGatewayClient(clientConfiguration, endpoint, region, credentials, apiKey, httpClient);
    }

}
