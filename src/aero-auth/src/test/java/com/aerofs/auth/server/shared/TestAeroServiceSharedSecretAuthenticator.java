package com.aerofs.auth.server.shared;

import com.aerofs.auth.Server;
import com.aerofs.auth.ServerResource;
import com.aerofs.auth.server.AeroPrincipal;
import com.aerofs.auth.server.AeroPrincipalBinder;
import com.aerofs.auth.server.Roles;
import com.aerofs.auth.server.SharedSecret;
import com.aerofs.baseline.AdminEnvironment;
import com.aerofs.baseline.RootEnvironment;
import com.aerofs.baseline.ServiceEnvironment;
import com.aerofs.baseline.http.HttpUtils;
import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import javax.annotation.Nullable;
import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public final class TestAeroServiceSharedSecretAuthenticator {

    private static final String SERVICE = "test_service-0";
    private static final String DEPLOYMENT_SECRET = "d5226bec6f9de1a49b171f8df75ddcff";

    // ensure that we can:
    // 1. get an injected AeroPrincipal
    @Path("/r0")
    @Singleton
    public static final class TestResource0 {

        @RolesAllowed(Roles.SERVICE)
        @GET
        public String get(@Context @Nullable AeroPrincipal principal) {
            Preconditions.checkArgument(principal != null, "principal not passed in");
            return principal.getName();
        }
    }

    // ensure that we can:
    // 1. get an injected AeroServicePrincipal
    // 2. be allowed to access a method with multiple roles
    @Path("/r1")
    @Singleton
    public static final class TestResource1 {

        @RolesAllowed({Roles.SERVICE, Roles.USER})
        @GET
        public String get(@Context @Nullable AeroServicePrincipal principal) {
            Preconditions.checkArgument(principal != null, "principal not passed in");
            return principal.getName();
        }
    }

    // ensure that we are:
    // 1. not allowed to access a method with a non-service role
    @Path("/r2")
    @Singleton
    public static final class TestResource2 {

        @RolesAllowed(Roles.USER)
        @GET
        public String get(@Context @Nullable AeroServicePrincipal principal) {
            Preconditions.checkArgument(principal != null, "principal not passed in");
            return principal.getName();
        }
    }

    // we define an instance of a server that:
    // 1. Defines a single Authenticator that only accepts Aero-Service-Shared-Secret
    // 2. Defines two binders that supply instances of either AeroPrincipal or AeroServicePrincipal
    private static final class TestServer extends Server {

        @Override
        public void init(ServerConfiguration configuration, RootEnvironment root, AdminEnvironment admin, ServiceEnvironment service) throws Exception {
            super.init(configuration, root, admin, service);

            root.addAuthenticator(new AeroServiceSharedSecretAuthenticator(new SharedSecret(DEPLOYMENT_SECRET)));

            service.addProvider(new AeroServiceSharedSecretPrincipalBinder());
            service.addProvider(new AeroPrincipalBinder());

            service.addResource(TestResource0.class);
            service.addResource(TestResource1.class);
            service.addResource(TestResource2.class);
        }
    }

    @Rule
    public final ServerResource server = new ServerResource(new TestServer());

    private final CloseableHttpClient client = HttpClients.createDefault();

    @After
    public void teardown() {
        try {
            client.close();
        } catch (IOException e) {
            // ignore
        }
    }

    @Test
    public void shouldGet403IfUnrecognizedAuthorizationSchemeUsed() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, "Aero-Fancy-Scheme Just-Trust-Me");

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_FORBIDDEN));
    }

    @Test
    public void shouldGet200AndInjectAeroPrincipalIfProperlyAuthenticatedRequestIsMade() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, DEPLOYMENT_SECRET));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readResponseEntityToString(response), equalTo(SERVICE));
    }

    @Test
    public void shouldGet200AndInjectAeroServicePrincipalIfProperlyAuthenticatedRequestIsMade() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r1");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, DEPLOYMENT_SECRET));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readResponseEntityToString(response), equalTo(SERVICE));
    }

    @Test
    public void shouldGet403IfProperlyAuthenticatedRequestIsMadeToAResourceThatShouldOnlyBeAccessedByPrincipalsInUserRole() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r2");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, DEPLOYMENT_SECRET));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_FORBIDDEN));
    }

    @Test
    public void shouldGet403IfDeploymentSecretInRequestIsIncorrect() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, "337c0ef46f232e9f41117eed8c0791a9")); // deployment secret has correct format, but wrong value

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_FORBIDDEN));
    }

    @Test
    public void shouldGet400IfAuthorizationHeaderInRequestHasInvalidFormat() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, DEPLOYMENT_SECRET) + " random junk"); // add invalid parameters at the end

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldGet400IfServiceNameInRequestHasInvalidFormat() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue("Invalid+Service_Name", DEPLOYMENT_SECRET)); // invalid service name

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldGet400IfDeploymentSecretInRequestHasInvalidFormat() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, "invalid_secret")); // invalid deployment secret

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    private static String getAuthHeaderValue(String service, String deploymentSecret) {
        return com.aerofs.auth.client.shared.AeroService.getHeaderValue(service, deploymentSecret);
    }
}