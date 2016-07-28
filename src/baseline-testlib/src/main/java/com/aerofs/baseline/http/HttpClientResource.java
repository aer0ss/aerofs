/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.http;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class HttpClientResource extends ExternalResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientResource.class);

    private final CloseableHttpAsyncClient httpClient = HttpAsyncClients.custom().setMaxConnPerRoute(1).setMaxConnTotal(1).build();

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
