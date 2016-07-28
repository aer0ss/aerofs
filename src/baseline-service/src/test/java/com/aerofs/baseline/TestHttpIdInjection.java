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

package com.aerofs.baseline;

import com.aerofs.baseline.errors.BaseExceptionMapper;
import com.aerofs.baseline.errors.ServiceError;
import com.aerofs.baseline.http.ChannelId;
import com.aerofs.baseline.http.Headers;
import com.aerofs.baseline.http.HttpClientResource;
import com.aerofs.baseline.http.HttpUtils;
import com.aerofs.baseline.http.RequestId;
import com.aerofs.baseline.metrics.MetricRegistries;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

public final class TestHttpIdInjection {

    @Path("/consuming")
    @Singleton
    public static final class ConsumingResource {

        @GET
        public String get(@Context @NotNull ChannelId channelId, @Context @NotNull RequestId requestId) {
            return channelId.getValue() + "-" + requestId.getValue();
        }
    }

    @Path("/exception")
    @Singleton
    public static final class ExceptionResource {

        @GET
        public String get(@Context @NotNull ChannelId channelId, @Context @NotNull RequestId requestId) {
            throw new RuntimeException("BROKEN");
        }
    }

    private final Service<ServiceConfiguration> service = new Service<ServiceConfiguration>("test") {

        @Override
        public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
            environment.addResource(ConsumingResource.class);
            environment.addResource(ExceptionResource.class);
            environment.addServiceProvider(new BaseExceptionMapper<RuntimeException>(BaseExceptionMapper.ErrorResponseEntity.NO_STACK_IN_RESPONSE, BaseExceptionMapper.StackLogging.DISABLE_LOGGING) {

                @Override
                protected int getErrorCode(RuntimeException throwable) {
                    return 7777;
                }

                @Override
                protected String getErrorName(RuntimeException throwable) {
                    return "RUNTIME";
                }

                @Override
                protected String getErrorText(RuntimeException throwable) {
                    return channelIdProvider.get().getValue() + "-" + requestIdProvider.get().getValue();
                }

                @Override
                protected Response.Status getHttpResponseStatus(RuntimeException throwable) {
                    return Response.Status.CONFLICT;
                }
            });
        }
    };

    @Rule
    public final HttpClientResource client = new HttpClientResource();

    @Before
    public void setup() throws Exception {
        try {
            service.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);
        }  catch (Exception e) {
            MetricRegistries.unregisterMetrics();
            throw e;
        }
    }

    @After
    public void teardown() {
        service.shutdown();
    }

    @Test
    public void shouldHaveChannelIdAndRequestIdInjectedProperly() throws Exception {
        HttpGet get = new HttpGet(ServiceConfiguration.SERVICE_URL + "/consuming");

        Future<HttpResponse> future = client.getClient().execute(get, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));

        String body = HttpUtils.readResponseEntityToString(response);
        String tokens[] = body.split("-");
        assertThat(tokens.length, equalTo(2));

        // there's a channel id
        assertThat(tokens[0].length(), greaterThan(0));

        // the request id in the body matches the one that's added by the underlying pipeline
        String requestId = tokens[1];
        assertThat(requestId, equalTo(response.getFirstHeader(Headers.REQUEST_TRACING_HEADER).getValue()));
    }

    @Test
    public void shouldHaveChannelIdAndRequestIdInjectedIntoExceptionMapper() throws Exception {
        HttpGet get = new HttpGet(ServiceConfiguration.SERVICE_URL + "/exception");

        Future<HttpResponse> future = client.getClient().execute(get, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_CONFLICT));

        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
        ServiceError error = mapper.readValue(response.getEntity().getContent(), ServiceError.class);

        String tokens[] = error.getErrorText().split("-");
        assertThat(tokens.length, equalTo(2));
        assertThat(tokens[0].length(), greaterThan(0));
        assertThat(tokens[1].length(), greaterThan(0));

        // there's a channel id
        assertThat(tokens[0].length(), greaterThan(0));

        // the request id in the body matches the one that's added by the underlying pipeline
        String requestId = tokens[1];
        assertThat(requestId, equalTo(response.getFirstHeader(Headers.REQUEST_TRACING_HEADER).getValue()));
    }

}