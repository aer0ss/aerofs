package com.aerofs.polaris;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class HttpClientResource extends ExternalResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientResource.class);

    private final CloseableHttpAsyncClient httpClient = HttpAsyncClients.createDefault();

    @Override
    protected void before() throws Throwable {
        httpClient.start();
    }

    @Override
    protected void after() {
        try {
            httpClient.close();
        } catch (IOException e) {
            LOGGER.warn("fail close http client cleanly", e);
        }
    }

    public CloseableHttpAsyncClient getClient() {
        return httpClient;
    }
}
