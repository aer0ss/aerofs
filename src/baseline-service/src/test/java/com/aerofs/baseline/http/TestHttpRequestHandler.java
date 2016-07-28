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

import com.aerofs.baseline.Environment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.ServiceConfiguration;
import com.google.common.net.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

public final class TestHttpRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestHttpRequestHandler.class);

    private final Service<ServiceConfiguration> server = new Service<ServiceConfiguration>("test") {

        // add all the resources
        // unused resources will *not* interfere with the used resources (they are independent)

        @Override
        public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
            environment.addResource(BasicResource.class);
            environment.addResource(EmptyEntityResource.class);
            environment.addResource(ChunkedDownloadResource.class);
            environment.addResource(ChunkedUploadResource.class);
            environment.addResource(HandsOnChunkedUploadResource.class);
            environment.addResource(PollingResource.class);
            environment.addResource(ThrowingResource.class);
            environment.addResource(TimeoutResource.class);
        }
    };

    @Rule
    public final HttpClientResource client = new HttpClientResource();

    @Before
    public void setup() throws Exception {
        server.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);
    }

    @After
    public void teardown() {
        server.shutdown();
    }

    @Test
    public void shouldSuccessfullyGetAndReceiveResponse() throws Exception {
        HttpGet get = new HttpGet(ServiceConfiguration.SERVICE_URL + "/" + Resources.BASIC_RESOURCE);

        Future<HttpResponse> future = client.getClient().execute(get, null);
        HttpResponse response = future.get(10, TimeUnit.SECONDS);

        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readStreamToString(response.getEntity().getContent()), equalTo("success"));
    }

    @Test
    public void shouldSuccessfullyPostAndReceiveEmptyResponse() throws Exception {
        HttpPost post = new HttpPost(ServiceConfiguration.SERVICE_URL + "/" + Resources.BASIC_RESOURCE);
        post.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
        post.setEntity(HttpUtils.writeStringToEntity("data"));

        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get(10, TimeUnit.SECONDS);

        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_NO_CONTENT));
        assertThat(response.getEntity(), nullValue());
    }

    @Test
    public void shouldSuccessfullyPostAndReceiveResponse() throws Exception {
        HttpPost post = new HttpPost(ServiceConfiguration.SERVICE_URL + "/" + Resources.BASIC_RESOURCE + "/data1");
        post.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
        post.setEntity(HttpUtils.writeStringToEntity("data2"));

        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get(10, TimeUnit.SECONDS);

        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readStreamToString(response.getEntity().getContent()), equalTo("data1-data2"));
    }

    @Test
    public void shouldSuccessfullyPostAndReceiveResponseAfterMakingUnsuccessfulPost() throws Exception {
        // unsuccessful
        // how does this test work, you ask?
        // well, there's only one request processing
        // thread on the server so if that thread locks up waiting
        // for bytes that never come, even if the stream is closed
        // then the *second* request will time out.
        try {
            HttpPost post0 = new HttpPost(ServiceConfiguration.SERVICE_URL + "/" + Resources.BASIC_RESOURCE + "/data1");
            post0.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
            BasicHttpEntity basic = new BasicHttpEntity();
            basic.setChunked(true);
            basic.setContentLength(-1);
            basic.setContent(new InputStream() {

                private int counter = 0;

                @Override
                public int read() throws IOException {
                    if (counter < (3 * 1024 * 1024)) {
                        counter++;
                        return 'a';
                    } else {
                        throw new IOException("read failed");
                    }
                }
            });
            post0.setEntity(basic);

            Future<HttpResponse> future0 = client.getClient().execute(post0, null);
            future0.get();
        } catch (Exception e) {
            // noop
        }

        // successful
        HttpPost post = new HttpPost(ServiceConfiguration.SERVICE_URL + "/" + Resources.BASIC_RESOURCE + "/data1");
        post.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
        post.setEntity(HttpUtils.writeStringToEntity("data2"));

        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get(10, TimeUnit.SECONDS);

        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readStreamToString(response.getEntity().getContent()), equalTo("data1-data2"));
    }

    @Test
    public void shouldSuccessfullyMakeMultipleSequentialGetsAndReceiveMultipleEmptyResponses() throws Exception {
        // get 0
        HttpGet get0 = new HttpGet(ServiceConfiguration.SERVICE_URL + "/" + Resources.EMPTY_ENTITY_RESOURCE);

        Future<HttpResponse> future0 = client.getClient().execute(get0, null);
        HttpResponse response0 = future0.get(10, TimeUnit.SECONDS);

        assertThat(response0.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_NO_CONTENT));
        assertThat(response0.getEntity(), nullValue());

        // get 1
        HttpGet get1 = new HttpGet(ServiceConfiguration.SERVICE_URL + "/" + Resources.EMPTY_ENTITY_RESOURCE);

        Future<HttpResponse> future1 = client.getClient().execute(get1, null);
        HttpResponse response1 = future1.get(10, TimeUnit.SECONDS);

        assertThat(response1.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_NO_CONTENT));
        assertThat(response1.getEntity(), nullValue());
    }

    @Test
    public void shouldSuccessfullyMakeMultipleSequentialPostsWithNoContentAndReceiveMultipleEmptyResponses() throws Exception {
        // post 0
        HttpPost post0 = new HttpPost(ServiceConfiguration.SERVICE_URL + "/" + Resources.EMPTY_ENTITY_RESOURCE);

        Future<HttpResponse> future0 = client.getClient().execute(post0, null);
        HttpResponse response0 = future0.get(10, TimeUnit.SECONDS);

        assertThat(response0.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_NO_CONTENT));
        assertThat(response0.getEntity(), nullValue());

        // post 1
        HttpPost post1 = new HttpPost(ServiceConfiguration.SERVICE_URL + "/" + Resources.EMPTY_ENTITY_RESOURCE);

        Future<HttpResponse> future1 = client.getClient().execute(post1, null);
        HttpResponse response1 = future1.get(10, TimeUnit.SECONDS);

        assertThat(response1.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_NO_CONTENT));
        assertThat(response0.getEntity(), nullValue());
    }

    @Test
    public void shouldSuccessfullyDownloadChunkedData() throws Exception {
        HttpGet get = new HttpGet(ServiceConfiguration.SERVICE_URL + "/" + Resources.CHUNKED_DOWNLOAD_RESOURCE);

        Future<HttpResponse> future = client.getClient().execute(get, null);
        HttpResponse response = future.get(60, TimeUnit.SECONDS);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));

        byte[] content = HttpUtils.readStreamToBytes(response.getEntity().getContent());
        String hexContentDigest = Resources.getHexDigest(content);
        String hexChunkedDigest = Resources.getHexDigest(ChunkedDownloadResource.RESOURCE_BYTES);
        assertThat(hexContentDigest, equalTo(hexChunkedDigest));
    }

    @Test
    public void shouldSuccessfullyUploadChunkedData() throws Exception {
        // create random bytes and compute its digest
        byte[] random = Resources.getRandomBytes(4 * 1024 * 1024);
        String hex = Resources.getHexDigest(random);

        // post the data
        HttpPost post = newChunkedPost(Resources.CHUNKED_UPLOAD_RESOURCE, random);
        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get(60, TimeUnit.SECONDS);

        // check the response
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readStreamToString(response.getEntity().getContent()), equalTo(hex));

        LOGGER.info("hex digest:{}", hex);
    }

    @Test
    public void shouldSuccessfullyUploadChunkedDataToResourceThatReadsManuallyFromInputStream() throws Exception {
        // create random bytes and compute its digest
        byte[] random = Resources.getRandomBytes(4 * 1024 * 1024);
        String hex = Resources.getHexDigest(random);

        // post the data
        HttpPost post = newChunkedPost(Resources.HANDS_ON_CHUNKED_UPLOAD_RESOURCE, random);
        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get(60, TimeUnit.SECONDS);

        // check the response
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readStreamToString(response.getEntity().getContent()), equalTo(hex));

        LOGGER.info("hex digest:{}", hex);
    }

    private static HttpPost newChunkedPost(String resource, byte[] bytes) {
        BasicHttpEntity basic = new BasicHttpEntity();
        basic.setChunked(true);
        basic.setContentLength(-1);
        basic.setContent(new ByteArrayInputStream(bytes));

        HttpPost post = new HttpPost(ServiceConfiguration.SERVICE_URL + "/" + resource);
        post.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM);
        post.setEntity(basic);
        return post;
    }

    @Test
    public void shouldSuccessfullyLongPoll() throws Exception {
        HttpGet get = new HttpGet(ServiceConfiguration.SERVICE_URL + "/" + Resources.POLLING_RESOURCE);

        long startTime = System.currentTimeMillis();
        Future<HttpResponse> future = client.getClient().execute(get, null);
        HttpResponse response = future.get(10, TimeUnit.SECONDS);
        long finishTime = System.currentTimeMillis();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readStreamToString(response.getEntity().getContent()), equalTo("success"));
        assertThat(finishTime - startTime, greaterThanOrEqualTo(6000L));
        assertThat(finishTime - startTime, lessThanOrEqualTo(10000L));
    }

    @Test
    public void shouldTimeoutDuringLongPoll() throws Exception {
        HttpGet get = new HttpGet(ServiceConfiguration.SERVICE_URL + "/" + Resources.TIMEOUT_RESOURCE);

        long startTime = System.currentTimeMillis();
        Future<HttpResponse> future = client.getClient().execute(get, null);
        HttpResponse response = future.get(10, TimeUnit.SECONDS);
        long finishTime = System.currentTimeMillis();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_SERVICE_UNAVAILABLE));
        assertThat(finishTime - startTime, greaterThanOrEqualTo(7000L));
        assertThat(finishTime - startTime, lessThanOrEqualTo(8000L));
    }

    @Test
    public void shouldReceiveAFailureHttpResponseCodeWhenServerThrowsInGet() throws Exception {
        HttpGet get = new HttpGet(ServiceConfiguration.SERVICE_URL + "/" + Resources.THROWING_RESOURCE);

        Future<HttpResponse> future = client.getClient().execute(get, null);
        HttpResponse response = future.get(10, TimeUnit.SECONDS);

        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_INTERNAL_SERVER_ERROR));
    }

    @Test
    public void shouldReceiveAFailureHttpResponseCodeWhenServerThrowsInPost() throws Exception {
        HttpPost post = new HttpPost(ServiceConfiguration.SERVICE_URL + "/" + Resources.THROWING_RESOURCE);
        post.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
        post.setEntity(HttpUtils.writeStringToEntity("data"));

        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get(10, TimeUnit.SECONDS);

        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_INTERNAL_SERVER_ERROR));
    }
}
