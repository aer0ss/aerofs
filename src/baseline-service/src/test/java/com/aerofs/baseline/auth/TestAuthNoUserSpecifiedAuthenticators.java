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

package com.aerofs.baseline.auth;

import com.aerofs.baseline.Environment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.ServiceConfiguration;
import com.aerofs.baseline.http.HttpClientResource;
import com.aerofs.baseline.http.HttpUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public final class TestAuthNoUserSpecifiedAuthenticators {

    //
    // service definition
    //

    @Path("/restricted")
    public static final class RootResource {

        private static final String SUPER_SECRET = "super_secret";

        @RolesAllowed("admin")
        @GET
        public String getAdminValue() {
            return SUPER_SECRET;
        }
    }

    @Path("/everyone")
    public static final class UserResource {

        private static final String BORING = "boring";

        @GET
        public String getAdminValue() {
            return BORING;
        }
    }

    public static final class TestService extends Service<ServiceConfiguration> {

        public TestService() {
            super("test");
        }

        @Override
        public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
            environment.addResource(UserResource.class);
            environment.addResource(RootResource.class);
        }
    }

    //
    // Tests
    //

    @Rule
    public HttpClientResource client = new HttpClientResource();

    private TestService server;

    @Before
    public void setup() throws Exception {
        server = new TestService();
        server.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);
    }

    @After
    public void teardown() {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void shouldRejectUnauthenticatedAccessToRestrictedResource() throws Exception {
        HttpUriRequest request = new HttpGet(ServiceConfiguration.SERVICE_URL + "/restricted");

        Future<HttpResponse> future = client.getClient().execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(Response.Status.FORBIDDEN.getStatusCode()));
    }

    @Test
    public void shouldAllowAccessToUserResource() throws Exception {
        HttpUriRequest request = new HttpGet(ServiceConfiguration.SERVICE_URL + "/everyone");

        Future<HttpResponse> future = client.getClient().execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(Response.Status.OK.getStatusCode()));
        assertThat(HttpUtils.readResponseEntityToString(response), equalTo(UserResource.BORING));
    }
}
