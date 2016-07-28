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
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.net.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public final class TestAuth {

    private static final Logger LOGGER = LoggerFactory.getLogger("AUTH");

    /**
     * Represents whether an instance of an authenticator
     * should be created manually within a test server (i.e. explicit 'new')
     * or whether we should use HK2.
     */
    enum CreateType {
        /** Manual instance creation. */
        INSTANCE,
        /** HK2-managed injection. */
        INJECTED
    }

    @Parameterized.Parameters
    public static Collection<Object[]> getTestParameters() {
        return Arrays.asList(new Object[][] {{CreateType.INSTANCE}, {CreateType.INJECTED}}); // test manually-created instances followed by HK2 created/injected instances
    }

    private static final Set<String> ROOT_ROLES = ImmutableSet.of("admin", "user");
    private static final Set<String> USER_ROLES = ImmutableSet.of("user");

    //
    // Throwing authenticator
    //

    public static final class ThrowingAuthenticator implements Authenticator {

        @Inject
        public ThrowingAuthenticator() {
            LoggerFactory.getLogger("AUTH").info(">>>>> CREATED THROW");
        }

        @Override
        public String getName() {
            return "THROW";
        }

        @Override
        public AuthenticationResult authenticate(MultivaluedMap<String, String> headers) {
            List<String> results = headers.get("Throw");

            if (results == null) {
                return AuthenticationResult.UNSUPPORTED;
            }

            if (results.size() != 1) {
                return AuthenticationResult.FAILED;
            }

            throw new RuntimeException("THROW HARD");
        }
    }

    //
    // Custom "HeFancy" Authentication
    //

    public static final class HeFancySecurityContext implements SecurityContext {

        public static final String HE_FANCY = "HE_FANCY";

        private final Principal principal;
        private final Set<String> allowedRoles;

        public HeFancySecurityContext(String user, Set<String> allowedRoles) {
            this.principal = () -> user;
            this.allowedRoles = allowedRoles;
        }

        @Override
        public Principal getUserPrincipal() {
            return principal;
        }

        @Override
        public boolean isUserInRole(String role) {
            return allowedRoles.contains(role);
        }

        @Override
        public boolean isSecure() {
            return true; // so true...
        }

        @Override
        public String getAuthenticationScheme() {
            return HE_FANCY;
        }
    }

    public static final class HeFancyAuthenticator implements Authenticator {

        private static final String FANCY_AUTHENTICATION = "Fancy-Authentication";
        private static final String USER = "ABHISHEK";
        private static final String PASS = "PASSWORD";

        @Inject
        public HeFancyAuthenticator() {
            LOGGER.info(">>>>> CREATED FANCY");
        }

        @Override
        public String getName() {
            return HeFancySecurityContext.HE_FANCY;
        }

        @Override
        public AuthenticationResult authenticate(MultivaluedMap<String, String> headers) {
            List<String> results = headers.get(FANCY_AUTHENTICATION);

            if (results == null) {
                return AuthenticationResult.UNSUPPORTED;
            }

            if (results.size() != 1) {
                return AuthenticationResult.FAILED;
            }

            Preconditions.checkArgument(results.size() == 1);
            String authorization = results.get(0);

            // suspiciously similar to http basic authentication
            // coincidence?
            // YES
            authorization = authorization.replaceAll("[Ff]ancy", "");
            authorization = authorization.trim();

            byte[] decoded = BaseEncoding.base64().decode(authorization);
            String authString = new String(decoded, Charsets.US_ASCII);

            String[] authComponents = authString.split(":");

            if (authComponents.length == 2) {
                if (authComponents[0].equals(USER) && authComponents[1].equals(PASS)) {
                    return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new HeFancySecurityContext(USER, USER_ROLES));
                }
            }

            return AuthenticationResult.FAILED;
        }
    }

    //
    // HTTP Basic Authentication
    //

    public static final class BasicSecurityContext implements SecurityContext {

        private final Principal principal;
        private final Set<String> allowedRoles;

        public BasicSecurityContext(String user, Set<String> allowedRoles) {
            this.principal = () -> user;
            this.allowedRoles = allowedRoles;
        }

        @Override
        public Principal getUserPrincipal() {
            return principal;
        }

        @Override
        public boolean isUserInRole(String role) {
            return allowedRoles.contains(role);
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String getAuthenticationScheme() {
            return SecurityContext.BASIC_AUTH;
        }
    }

    public static final class HttpBasicAuthenticator implements Authenticator {

        private static final String USER = "USER";
        private static final String ROOT = "ROOT";
        private static final String PASS = "PASS";

        @Inject
        public HttpBasicAuthenticator() {
            LoggerFactory.getLogger("AUTH").info(">>>>> CREATED BASIC");
        }

        @Override
        public String getName() {
            return SecurityContext.BASIC_AUTH;
        }

        @Override
        public AuthenticationResult authenticate(MultivaluedMap<String, String> headers) {
            List<String> results = headers.get(HttpHeaders.AUTHORIZATION);

            if (results == null) {
                return AuthenticationResult.UNSUPPORTED;
            }

            if (results.size() != 1) {
                return AuthenticationResult.FAILED;
            }

            Preconditions.checkArgument(results.size() == 1);
            String authorization = results.get(0);

            authorization = authorization.replaceAll("[Bb]asic", "");
            authorization = authorization.trim();

            byte[] decoded = BaseEncoding.base64().decode(authorization);
            String authString = new String(decoded, Charsets.US_ASCII);

            String[] authComponents = authString.split(":");

            if (authComponents.length == 2) {
                if (authComponents[0].equals(USER) && authComponents[1].equals(PASS)) {
                    return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new BasicSecurityContext(USER, USER_ROLES));
                } else if (authComponents[0].equals(ROOT) && authComponents[1].equals(PASS)) {
                    return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new BasicSecurityContext(USER, ROOT_ROLES));
                }
            }

            return AuthenticationResult.FAILED;
        }
    }

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

        @RolesAllowed({"admin", "user"})
        @GET
        public String getAdminValue() {
            return BORING;
        }
    }

    public static final class TestService extends Service<ServiceConfiguration> {

        private final CreateType createType;

        public TestService(CreateType createType) {
            super("test");
            this.createType = createType;
        }

        @Override
        public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
            // authenticator chain:
            // 1. first, try fancy authentication
            // 2. then, basic auth
            // 3. finally, we have an authenticator that throws (to simulate a failing authenticator)

            if (createType == CreateType.INSTANCE) {
                environment.addAuthenticator(new HeFancyAuthenticator()); // try fancy authentication first
                environment.addAuthenticator(new HttpBasicAuthenticator()); // then basic auth...
                environment.addAuthenticator(new ThrowingAuthenticator());
            } else {
                environment.addAuthenticator(HeFancyAuthenticator.class);
                environment.addAuthenticator(HttpBasicAuthenticator.class);
                environment.addAuthenticator(ThrowingAuthenticator.class);
                environment.addBinder(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(HeFancyAuthenticator.class).to(HeFancyAuthenticator.class).in(Singleton.class);
                        bind(HttpBasicAuthenticator.class).to(HttpBasicAuthenticator.class).in(Singleton.class);
                        bind(ThrowingAuthenticator.class).to(ThrowingAuthenticator.class).in(Singleton.class);
                    }
                });
            }

            environment.addResource(UserResource.class);
            environment.addResource(RootResource.class);
        }
    }

    //
    // tests
    //

    @Rule
    public HttpClientResource client = new HttpClientResource();

    private final CreateType createType;

    private TestService server;

    public TestAuth(CreateType createType) {
        this.createType = createType;
    }

    @Before
    public void setup() throws Exception {
        server = new TestService(createType);
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
    public void shouldRejectUnauthenticatedAccessToUserResource() throws Exception {
        HttpUriRequest request = new HttpGet(ServiceConfiguration.SERVICE_URL + "/everyone");

        Future<HttpResponse> future = client.getClient().execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(Response.Status.FORBIDDEN.getStatusCode()));
    }

    @Test
    public void shouldRejectUserAccessToRestrictedResource() throws Exception {
        HttpUriRequest request = new HttpGet(ServiceConfiguration.SERVICE_URL + "/restricted");
        request.setHeader("Authorization", "Basic " + getEncodedCredentials(HttpBasicAuthenticator.USER, HttpBasicAuthenticator.PASS));

        Future<HttpResponse> future = client.getClient().execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(Response.Status.FORBIDDEN.getStatusCode()));
    }

    @Test
    public void shouldAllowAuthorizedUserAccessToUserResource() throws Exception {
        HttpUriRequest request = new HttpGet(ServiceConfiguration.SERVICE_URL + "/everyone");
        request.setHeader("Authorization", "Basic " + getEncodedCredentials(HttpBasicAuthenticator.USER, HttpBasicAuthenticator.PASS));

        Future<HttpResponse> future = client.getClient().execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(Response.Status.OK.getStatusCode()));
        assertThat(HttpUtils.readResponseEntityToString(response), equalTo(UserResource.BORING));
    }

    @Test
    public void shouldAllowAuthorizedRootAccessToUserResource() throws Exception {
        HttpUriRequest request = new HttpGet(ServiceConfiguration.SERVICE_URL + "/everyone");
        request.setHeader("Authorization", "Basic " + getEncodedCredentials(HttpBasicAuthenticator.ROOT, HttpBasicAuthenticator.PASS));

        Future<HttpResponse> future = client.getClient().execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(Response.Status.OK.getStatusCode()));
        assertThat(HttpUtils.readResponseEntityToString(response), equalTo(UserResource.BORING));
    }

    @Test
    public void shouldAllowAuthorizedRootAccessToRestrictedResource() throws Exception {
        HttpUriRequest request = new HttpGet(ServiceConfiguration.SERVICE_URL + "/restricted");
        request.setHeader("Authorization", "Basic " + getEncodedCredentials(HttpBasicAuthenticator.ROOT, HttpBasicAuthenticator.PASS));

        Future<HttpResponse> future = client.getClient().execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(Response.Status.OK.getStatusCode()));
        assertThat(HttpUtils.readResponseEntityToString(response), equalTo(RootResource.SUPER_SECRET));
    }

    @Test
    public void shouldAllowFancyAuthorizedUserAccessToUserResource() throws Exception {
        HttpUriRequest request = new HttpGet(ServiceConfiguration.SERVICE_URL + "/everyone");
        request.setHeader("Fancy-Authentication", "Fancy " + getEncodedCredentials(HeFancyAuthenticator.USER, HeFancyAuthenticator.PASS));

        Future<HttpResponse> future = client.getClient().execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(Response.Status.OK.getStatusCode()));
        assertThat(HttpUtils.readResponseEntityToString(response), equalTo(UserResource.BORING));
    }

    @Test
    public void shouldRejectFancyAuthorizedUserAccessToUserResource() throws Exception {
        HttpUriRequest request = new HttpGet(ServiceConfiguration.SERVICE_URL + "/restricted");
        request.setHeader("Fancy-Authentication", "Fancy " + getEncodedCredentials(HeFancyAuthenticator.USER, HeFancyAuthenticator.PASS));

        Future<HttpResponse> future = client.getClient().execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(Response.Status.FORBIDDEN.getStatusCode()));
    }

    @Test
    public void shouldRejectUserAccessWhenFirstAuthenticatorInChainFails() throws Exception {
        HttpUriRequest request = new HttpGet(ServiceConfiguration.SERVICE_URL + "/everyone");
        request.setHeader("Fancy-Authentication", "Fancy " + getEncodedCredentials("NONE", "NONE"));
        request.setHeader("Authorization", "Basic " + getEncodedCredentials(HttpBasicAuthenticator.USER, HttpBasicAuthenticator.PASS));

        Future<HttpResponse> future = client.getClient().execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(Response.Status.UNAUTHORIZED.getStatusCode()));
    }

    @Test
    public void shouldGetInternalServerErrorIfAuthenticatorThrows() throws Exception {
        HttpUriRequest request = new HttpGet(ServiceConfiguration.SERVICE_URL + "/everyone");
        request.setHeader("Throw", "bogus");

        Future<HttpResponse> future = client.getClient().execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
    }

    private static String getEncodedCredentials(String user, String pass) {
        return BaseEncoding.base64().encode(String.format("%s:%s", user, pass).getBytes(Charsets.US_ASCII));
    }
}
