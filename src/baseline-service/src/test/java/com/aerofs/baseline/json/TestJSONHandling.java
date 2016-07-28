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

package com.aerofs.baseline.json;

import com.aerofs.baseline.Environment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.ServiceConfiguration;
import com.aerofs.baseline.http.HttpClientResource;
import com.aerofs.baseline.http.HttpUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.base.Charsets;
import com.google.common.net.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public final class TestJSONHandling {

    // dummy json object
    public static final class JsonObject {

        @NotNull
        public final String sad;

        @NotNull
        public final String allen;

        @JsonCreator
        public JsonObject(@JsonProperty("sad") String sad, @JsonProperty("allen") String allen) {
            this.sad = sad;
            this.allen = allen;
        }
    }

    // dummy resource class
    @Path("/consumer")
    public static final class Resource {

        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        public String consumeData(JsonObject object) {
            return getResponseValue(object);
        }
    }

    // dummy server
    private final Service<ServiceConfiguration> server = new Service<ServiceConfiguration>("test") {

        @Override
        public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
            environment.addResource(Resource.class);
        }
    };

    private final ObjectMapper mapper = new ObjectMapper();

    @Rule
    public final HttpClientResource client = new HttpClientResource();

    @Before
    public void setup() throws Exception {
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
        server.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);
    }

    @After
    public void teardown() {
        server.shutdown();
    }

    @Test
    public void shouldReceiveErrorOnMakingPostWithEmptyBody() throws ExecutionException, InterruptedException {
        HttpPost post = new HttpPost(ServiceConfiguration.SERVICE_URL + "/consumer");
        post.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));

        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get();
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldReceiveErrorOnMakingPostWithInvalidJsonObject() throws ExecutionException, InterruptedException, JsonProcessingException {
        // noinspection ConstantConditions
        String serialized = mapper.writeValueAsString(new JsonObject(null, "allen")); // yes; I know I'm using 'null'
        ByteArrayInputStream contentInputStream = new ByteArrayInputStream(serialized.getBytes(Charsets.US_ASCII));
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(contentInputStream);

        HttpPost post = new HttpPost(ServiceConfiguration.SERVICE_URL + "/consumer");
        post.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
        post.setEntity(entity);

        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get();
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldSuccessfullyProcessJson() throws ExecutionException, InterruptedException, IOException {
        JsonObject value = new JsonObject("unhappy", "allen");

        String serialized = mapper.writeValueAsString(value);
        ByteArrayInputStream contentInputStream = new ByteArrayInputStream(serialized.getBytes(Charsets.US_ASCII));
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(contentInputStream);

        HttpPost post = new HttpPost(ServiceConfiguration.SERVICE_URL + "/consumer");
        post.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
        post.setEntity(entity);

        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get();
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readStreamToString(response.getEntity().getContent()), equalTo(getResponseValue(value)));
    }

    private static String getResponseValue(JsonObject value) {
        return value.sad + " " + value.allen;
    }
}