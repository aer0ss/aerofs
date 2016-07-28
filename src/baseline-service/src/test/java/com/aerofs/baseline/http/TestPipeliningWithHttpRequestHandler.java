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
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

public final class TestPipeliningWithHttpRequestHandler {

     private final Service<ServiceConfiguration> server = new Service<ServiceConfiguration>("test") {

         @Override
         public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
            environment.addResource(PipelinedResource.class);
        }
    };

    @Rule
    public HttpPipeliningClientResource client = new HttpPipeliningClientResource();

    @Before
    public void setup() throws Exception {
        server.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);
    }

    @After
    public void teardown() {
        server.shutdown();
    }

    @Test
    public void shouldReceiveResponsesToPipelinedRequestsInRequestOrder() throws Exception {
        List<HttpRequest> posts = Lists.newArrayListWithCapacity(1000);

        for (int i = 0; i < 1000; i++) {
            HttpPost post = new HttpPost(ServiceConfiguration.SERVICE_URL + "/" + Resources.PIPELINED_RESOURCE);
            post.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
            post.setEntity(HttpUtils.writeStringToEntity(Long.toString(i)));
            posts.add(post);
        }

        Future<List<HttpResponse>> future = client.getClient().execute(new HttpHost(ServiceConfiguration.TEST_CONFIGURATION.getService().getHost(), ServiceConfiguration.TEST_CONFIGURATION.getService().getPort()), posts, null);
        List<HttpResponse> responses = future.get(10, TimeUnit.SECONDS);

        assertThat(responses, hasSize(1000));

        int counter = 0;
        for (HttpResponse response : responses) {
            assertThat(HttpUtils.readStreamToString(response.getEntity().getContent()), equalTo(Long.toString(counter)));
            assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
            counter++;
        }
    }
}
