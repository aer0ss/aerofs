package com.aerofs.baseline.http;

import com.aerofs.baseline.http.HttpClientResource;
import org.apache.http.impl.nio.client.CloseableHttpPipeliningClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class HttpPipeliningClientResource extends ExternalResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientResource.class);

    private final CloseableHttpPipeliningClient httpClient = HttpAsyncClients.createPipelining();

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

    public CloseableHttpPipeliningClient getClient() {
        return httpClient;
    }
}
