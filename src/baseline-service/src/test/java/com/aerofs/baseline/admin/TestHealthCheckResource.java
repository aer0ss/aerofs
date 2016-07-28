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

package com.aerofs.baseline.admin;

import com.aerofs.baseline.Environment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.ServiceConfiguration;
import com.aerofs.baseline.http.HttpClientResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.collect.ImmutableMap;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public final class TestHealthCheckResource {

    //
    // health check definitions
    //

    public static final class SuccessHealthCheck0 implements HealthCheck {

        @Override
        public Status check() {
            return Status.success();
        }
    }

    public static final class SuccessHealthCheck1 implements HealthCheck {

        @Override
        public Status check() {
            return Status.success();
        }
    }

    public static final class FailureHealthCheck0 implements HealthCheck {

        @Override
        public Status check() {
            return Status.failure();
        }
    }

    public static final class FailureHealthCheck1 implements HealthCheck {

        @Override
        public Status check() {
            return Status.failure("unacceptable");
        }
    }

    public static final class FailureHealthCheck2 implements HealthCheck {

        @Override
        public Status check() {
            throw new RuntimeException("failed with exception");
        }
    }

    //
    // instance variables
    //

    private final ObjectMapper mapper = new ObjectMapper();
    {
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
    }

    @Rule
    public final Timeout timeout = new Timeout((int) TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS));

    @Rule
    public final HttpClientResource client = new HttpClientResource();

    //
    // user-defined health checks (bound with hk2)
    //

    @Test
    public void shouldReceive_HTTP_NOT_IMPLEMENTED_WhenNoHealthChecksAreImplemented() throws Exception {
        // setup the server with health checks
        Service<ServiceConfiguration> service = new Service<ServiceConfiguration>("test") {

            @Override
            public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
                // noop
            }
        };

        try {
            // start the server
            service.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);

            // make the request
            HttpGet get = new HttpGet(ServiceConfiguration.ADMIN_URL + "/status");
            Future<HttpResponse> future = client.getClient().execute(get, null);
            HttpResponse response = future.get(10, TimeUnit.SECONDS);

            // check the http response status code
            assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_NOT_IMPLEMENTED));

            // there should be no entity
            assertThat(response.getEntity().getContentLength(), equalTo(0L));
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void shouldReceive_HTTP_OK_WhenHealthCheckSuccessful() throws Exception {
        // setup the server with health checks
        Service<ServiceConfiguration> service = new Service<ServiceConfiguration>("test") {

            @Override
            public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
                environment.registerHealthCheck("test0", SuccessHealthCheck0.class);
                environment.registerHealthCheck("test1", SuccessHealthCheck1.class);
                environment.addAdminProvider(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(SuccessHealthCheck0.class).to(SuccessHealthCheck0.class);
                        bind(SuccessHealthCheck1.class).to(SuccessHealthCheck1.class);
                    }
                });
            }
        };

        // we expect the following ServiceStatus object
        ServiceStatus expected = new ServiceStatus(ImmutableMap.of("test0", Status.success(), "test1", Status.success()));

        try {
            // start the server
            service.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);

            // make the request
            HttpGet get = new HttpGet(ServiceConfiguration.ADMIN_URL + "/status");
            Future<HttpResponse> future = client.getClient().execute(get, null);
            HttpResponse response = future.get(10, TimeUnit.SECONDS);

            // check the http response status code
            assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));

            // verify the entity
            ServiceStatus actual = mapper.readValue(response.getEntity().getContent(), ServiceStatus.class);
            assertThat(actual, equalTo(expected));
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void shouldReceive_HTTP_SERVICE_UNAVAILABLE_WhenHealthCheckFails() throws Exception {
        // setup the server with health checks
        Service<ServiceConfiguration> service = new Service<ServiceConfiguration>("test") {

            @Override
            public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
                environment.registerHealthCheck("test0", SuccessHealthCheck0.class);
                environment.registerHealthCheck("test1", FailureHealthCheck0.class);
                environment.addAdminProvider(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(SuccessHealthCheck0.class).to(SuccessHealthCheck0.class);
                        bind(FailureHealthCheck0.class).to(FailureHealthCheck0.class);
                    }
                });
            }
        };

        // we expect the following ServiceStatus object
        ServiceStatus expected = new ServiceStatus(ImmutableMap.of("test0", Status.success(), "test1", Status.failure()));

        try {
            // start the server
            service.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);

            // make the request
            HttpGet get = new HttpGet(ServiceConfiguration.ADMIN_URL + "/status");
            Future<HttpResponse> future = client.getClient().execute(get, null);
            HttpResponse response = future.get(10, TimeUnit.SECONDS);

            // check the http response status code
            assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_SERVICE_UNAVAILABLE));

            // verify the entity
            ServiceStatus actual = mapper.readValue(response.getEntity().getContent(), ServiceStatus.class);
            assertThat(actual, equalTo(expected));
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void shouldReceive_HTTP_SERVICE_UNAVAILABLE_WithDetailedFailureInformationWhenHealthCheckFails() throws Exception {
        // setup the server with health checks
        Service<ServiceConfiguration> service = new Service<ServiceConfiguration>("test") {

            @Override
            public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
                environment.registerHealthCheck("test0", SuccessHealthCheck0.class);
                environment.registerHealthCheck("test1", FailureHealthCheck1.class);
                environment.addAdminProvider(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(SuccessHealthCheck0.class).to(SuccessHealthCheck0.class);
                        bind(FailureHealthCheck1.class).to(FailureHealthCheck1.class);
                    }
                });
            }
        };

        // we expect the following ServiceStatus object
        ServiceStatus expected = new ServiceStatus(ImmutableMap.of("test0", Status.success(), "test1", Status.failure("unacceptable")));

        try {
            // start the server
            service.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);

            // make the request
            HttpGet get = new HttpGet(ServiceConfiguration.ADMIN_URL + "/status");
            Future<HttpResponse> future = client.getClient().execute(get, null);
            HttpResponse response = future.get(10, TimeUnit.SECONDS);

            // check the http response status code
            assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_SERVICE_UNAVAILABLE));

            // verify the entity
            ServiceStatus actual = mapper.readValue(response.getEntity().getContent(), ServiceStatus.class);
            assertThat(actual, equalTo(expected));
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void shouldReceive_HTTP_SERVICE_UNAVAILABLE_WithExceptionMessageWhenHealthCheckFails() throws Exception {
        // setup the server with health checks
        Service<ServiceConfiguration> service = new Service<ServiceConfiguration>("test") {

            @Override
            public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
                environment.registerHealthCheck("test0", SuccessHealthCheck0.class);
                environment.registerHealthCheck("test1", FailureHealthCheck2.class);
                environment.addAdminProvider(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(SuccessHealthCheck0.class).to(SuccessHealthCheck0.class);
                        bind(FailureHealthCheck2.class).to(FailureHealthCheck2.class);
                    }
                });
            }
        };

        // we expect the following ServiceStatus object
        ServiceStatus expected = new ServiceStatus(ImmutableMap.of("test0", Status.success(), "test1", Status.failure("failed with exception")));

        try {
            // start the server
            service.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);

            // make the request
            HttpGet get = new HttpGet(ServiceConfiguration.ADMIN_URL + "/status");
            Future<HttpResponse> future = client.getClient().execute(get, null);
            HttpResponse response = future.get(10, TimeUnit.SECONDS);

            // check the http response status code
            assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_SERVICE_UNAVAILABLE));

            // verify the entity
            ServiceStatus actual = mapper.readValue(response.getEntity().getContent(), ServiceStatus.class);
            assertThat(actual, equalTo(expected));
        } finally {
            service.shutdown();
        }
    }

    //
    // health checks (registered as instances, or a mix of hk2 bound and instances)
    //

    @Test
    public void shouldReceive_HTTP_OK_WhenMultipleHealthCheckInstancesSuccessful() throws Exception {
        // setup the server with health checks
        Service<ServiceConfiguration> service = new Service<ServiceConfiguration>("test") {

            @Override
            public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
                environment.registerHealthCheck("test0", new SuccessHealthCheck0());
                environment.registerHealthCheck("test1", new SuccessHealthCheck1());
            }
        };

        // we expect the following ServiceStatus object
        ServiceStatus expected = new ServiceStatus(ImmutableMap.of("test0", Status.success(), "test1", Status.success()));

        try {
            // start the server
            service.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);

            // make the request
            HttpGet get = new HttpGet(ServiceConfiguration.ADMIN_URL + "/status");
            Future<HttpResponse> future = client.getClient().execute(get, null);
            HttpResponse response = future.get(10, TimeUnit.SECONDS);

            // check the http response status code
            assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));

            // verify the entity
            ServiceStatus actual = mapper.readValue(response.getEntity().getContent(), ServiceStatus.class);
            assertThat(actual, equalTo(expected));
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void shouldReceive_HTTP_OK_WhenMixOfHealthCheckInstanceAndHK2BoundHealthChecksSuccessful() throws Exception {
        // setup the server with health checks
        Service<ServiceConfiguration> service = new Service<ServiceConfiguration>("test") {

            @Override
            public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
                environment.registerHealthCheck("test0", new SuccessHealthCheck0());
                environment.registerHealthCheck("test1", SuccessHealthCheck1.class);
                environment.addAdminProvider(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(SuccessHealthCheck1.class).to(SuccessHealthCheck1.class);
                    }
                });
            }
        };

        // we expect the following ServiceStatus object
        ServiceStatus expected = new ServiceStatus(ImmutableMap.of("test0", Status.success(), "test1", Status.success()));

        try {
            // start the server
            service.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);

            // make the request
            HttpGet get = new HttpGet(ServiceConfiguration.ADMIN_URL + "/status");
            Future<HttpResponse> future = client.getClient().execute(get, null);
            HttpResponse response = future.get(10, TimeUnit.SECONDS);

            // check the http response status code
            assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));

            // verify the entity
            ServiceStatus actual = mapper.readValue(response.getEntity().getContent(), ServiceStatus.class);
            assertThat(actual, equalTo(expected));
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void shouldReceive_HTTP_SERVICE_UNAVAILABLE_WithDetailedFailureInformationWhenHealthCheckInstanceFails() throws Exception {
        // setup the server with health checks
        Service<ServiceConfiguration> service = new Service<ServiceConfiguration>("test") {

            @Override
            public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
                environment.registerHealthCheck("test0", SuccessHealthCheck0.class);
                environment.registerHealthCheck("test1", new FailureHealthCheck1());
                environment.addAdminProvider(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(SuccessHealthCheck0.class).to(SuccessHealthCheck0.class);
                    }
                });
            }
        };

        // we expect the following ServiceStatus object
        ServiceStatus expected = new ServiceStatus(ImmutableMap.of("test0", Status.success(), "test1", Status.failure("unacceptable")));

        try {
            // start the server
            service.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);

            // make the request
            HttpGet get = new HttpGet(ServiceConfiguration.ADMIN_URL + "/status");
            Future<HttpResponse> future = client.getClient().execute(get, null);
            HttpResponse response = future.get(10, TimeUnit.SECONDS);

            // check the http response status code
            assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_SERVICE_UNAVAILABLE));

            // verify the entity
            ServiceStatus actual = mapper.readValue(response.getEntity().getContent(), ServiceStatus.class);
            assertThat(actual, equalTo(expected));
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void shouldReceive_HTTP_SERVICE_UNAVAILABLE_WithExceptionMessageWhenHealthCheckInstanceFails() throws Exception {
        // setup the server with health checks
        Service<ServiceConfiguration> service = new Service<ServiceConfiguration>("test") {

            @Override
            public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
                environment.registerHealthCheck("test0", SuccessHealthCheck0.class);
                environment.registerHealthCheck("test1", new FailureHealthCheck2());
                environment.addAdminProvider(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(SuccessHealthCheck0.class).to(SuccessHealthCheck0.class);
                    }
                });
            }
        };

        // we expect the following ServiceStatus object
        ServiceStatus expected = new ServiceStatus(ImmutableMap.of("test0", Status.success(), "test1", Status.failure("failed with exception")));

        try {
            // start the server
            service.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);

            // make the request
            HttpGet get = new HttpGet(ServiceConfiguration.ADMIN_URL + "/status");
            Future<HttpResponse> future = client.getClient().execute(get, null);
            HttpResponse response = future.get(10, TimeUnit.SECONDS);

            // check the http response status code
            assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_SERVICE_UNAVAILABLE));

            // verify the entity
            ServiceStatus actual = mapper.readValue(response.getEntity().getContent(), ServiceStatus.class);
            assertThat(actual, equalTo(expected));
        } finally {
            service.shutdown();
        }
    }
}