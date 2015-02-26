package com.aerofs.auth.server.delegated;

import com.aerofs.auth.Server;
import com.aerofs.auth.ServerResource;
import com.aerofs.auth.server.AeroPrincipal;
import com.aerofs.auth.server.AeroPrincipalBinder;
import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.AeroUserDevicePrincipalBinder;
import com.aerofs.auth.server.Roles;
import com.aerofs.auth.server.SharedSecret;
import com.aerofs.baseline.Environment;
import com.aerofs.baseline.http.HttpUtils;
import com.google.common.base.Preconditions;
import org.apache.http.HttpHeaders;
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

public final class TestAeroDelegatedUserDeviceAuthenticator {

    private static final String SERVICE = "test_service-1";
    private static final String DEPLOYMENT_SECRET = "d5226bec6f9de1a49b171f8df75dad1f";
    private static final String USERID = "allen@aerofs.com";
    private static final String DEVICE = "4beaa8a53f91c9be37a1286116a982fe";

    // ensure that we can:
    // 1. get an injected AeroPrincipal
    @Path("/r0")
    @Singleton
    public static final class TestResource0 {

        @RolesAllowed(Roles.USER)
        @GET
        public String get(@Context @Nullable AeroPrincipal principal) {
            Preconditions.checkArgument(principal != null, "principal not passed in");
            return principal.getName();
        }
    }

    // ensure that we can:
    // 1. get an injected AeroUserDevicePrincipal
    // 2. be allowed to access a method with multiple roles
    @Path("/r1")
    @Singleton
    public static final class TestResource1 {

        @RolesAllowed({Roles.USER, Roles.SERVICE})
        @GET
        public String get(@Context @Nullable AeroUserDevicePrincipal principal) {
            Preconditions.checkArgument(principal != null, "principal not passed in");
            return principal.getName();
        }
    }

    // ensure that we can:
    // 1. get an injected AeroDelegatedUserDevicePrincipal
    // 2. be allowed to access a method with multiple roles
    @Path("/r2")
    @Singleton
    public static final class TestResource2 {

        @RolesAllowed({Roles.USER, Roles.SERVICE})
        @GET
        public String get(@Context @Nullable AeroDelegatedUserDevicePrincipal principal) {
            Preconditions.checkArgument(principal != null, "principal not passed in");
            return principal.getName();
        }
    }

    // ensure that we are:
    // 1. not allowed to access a method with a non-user role
    @Path("/r3")
    @Singleton
    public static final class TestResource3 {

        @RolesAllowed(Roles.SERVICE)
        @GET
        public String get(@Context @Nullable AeroPrincipal principal) {
            Preconditions.checkArgument(principal != null, "principal not passed in");
            return principal.getName();
        }
    }

    // we define an instance of an AuthServer that:
    // 1. Defines a single Authenticator that only accepts Aero-Delegated-User-Device
    // 2. Defines three binders that supply instances of either AeroPrincipal, AeroUserDevicePrincipal and AeroDelegatedUserDevicePrincipal
    private static final class TestServer extends Server {

        @Override
        public void init(Server.ServerConfiguration configuration, Environment environment) throws Exception {
            super.init(configuration, environment);

            environment.addAuthenticator(new AeroDelegatedUserDeviceAuthenticator(new SharedSecret(DEPLOYMENT_SECRET)));

            environment.addServiceProvider(new AeroPrincipalBinder());
            environment.addServiceProvider(new AeroUserDevicePrincipalBinder());
            environment.addServiceProvider(new AeroDelegatedUserDevicePrincipalBinder());

            environment.addResource(TestResource0.class);
            environment.addResource(TestResource1.class);
            environment.addResource(TestResource2.class);
            environment.addResource(TestResource3.class);
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
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, DEPLOYMENT_SECRET, USERID, DEVICE));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readResponseEntityToString(response), equalTo(String.format("%s:%s", USERID, DEVICE)));
    }

    @Test
    public void shouldGet200AndInjectAeroUserDevicePrincipalIfProperlyAuthenticatedRequestIsMade() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r1");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, DEPLOYMENT_SECRET, USERID, DEVICE));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readResponseEntityToString(response), equalTo(String.format("%s:%s", USERID, DEVICE)));
    }

    @Test
    public void shouldGet200AndInjectAeroDelegatedUserDevicePrincipalIfProperlyAuthenticatedRequestIsMade() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r2");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, DEPLOYMENT_SECRET, USERID, DEVICE));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readResponseEntityToString(response), equalTo(String.format("%s:%s", USERID, DEVICE)));
    }

    @Test
    public void shouldGet403IfProperlyAuthenticatedRequestIsMadeToAResourceThatShouldOnlyBeAccessedByPrincipalsInServiceRole() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r3");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, DEPLOYMENT_SECRET, USERID, DEVICE));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_FORBIDDEN));
    }

    @Test
    public void shouldGet403IfDeploymentSecretInRequestIsIncorrect() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, "337c0ef46f232e9f41117eed8c0791a9", USERID, DEVICE)); // deployment secret has correct format, but wrong value

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_FORBIDDEN));
    }

    @Test
    public void shouldGet400IfAuthorizationHeaderInRequestHasInvalidFormat() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, DEPLOYMENT_SECRET, USERID, DEVICE) + " random junk"); // add invalid parameters at the end

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldGet400IfServiceNameInRequestHasInvalidFormat() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue("Invalid+Service_Name", DEPLOYMENT_SECRET, USERID, DEVICE)); // invalid service name

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldGet400IfDeploymentSecretInRequestHasInvalidFormat() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, "invalid_secret", USERID, DEVICE)); // invalid deployment secret

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldGet400IfUserInRequestHasInvalidFormat() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, String.format("Aero-Delegated-User-Device %s %s %s %s", SERVICE, DEPLOYMENT_SECRET, USERID, DEVICE)); // username is not base64 encoded

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldGet400IfDeviceInRequestHasInvalidFormat() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(SERVICE, DEPLOYMENT_SECRET, USERID, "notahexdeviceid")); // obviously, not a hex device id

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    private static String getAuthHeaderValue(String service, String deploymentSecret, String user, String device) {
        return com.aerofs.auth.client.delegated.AeroDelegatedUserDevice.getHeaderValue(service, deploymentSecret, user, device);
    }
}