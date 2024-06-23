package org.apache.nifi.processors.aws.wag.client;

import java.io.IOException;
import software.amazon.awssdk.utils.IoUtils;
import software.amazon.awssdk.http.SdkHttpResponse;

public class GenericApiGatewayResponse {
    private final SdkHttpResponse httpResponse;
    private final String body;

    public GenericApiGatewayResponse(SdkHttpResponse httpResponse) throws IOException {
        this.httpResponse = httpResponse;
        if (httpResponse.getContent() != null) {
            this.body = IoUtils.toString(httpResponse.getContent());
        } else {
            this.body = null;
        }
    }

    public SdkHttpResponse getHttpResponse() {
        return httpResponse;
    }

    public String getBody() {
        return body;
    }
}