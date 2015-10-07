package com.aerofs.auth.server.cert;

import com.aerofs.auth.Server;
import com.aerofs.auth.ServerResource;
import com.aerofs.auth.server.AeroPrincipal;
import com.aerofs.auth.server.AeroPrincipalBinder;
import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.AeroUserDevicePrincipalBinder;
import com.aerofs.auth.server.Roles;
import com.aerofs.baseline.Environment;
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

public final class TestAeroDeviceCertAuthenticator {

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
    // 1. get an injected AeroDeviceCertPrincipal
    // 2. be allowed to access a method with multiple roles
    @Path("/r2")
    @Singleton
    public static final class TestResource2 {

        @RolesAllowed({Roles.USER, Roles.SERVICE})
        @GET
        public String get(@Context @Nullable AeroDeviceCertPrincipal principal) {
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
    // 1. Defines a single Authenticator that only accepts Aero-Device-Cert
    // 2. Defines three binders that supply instances of either AeroPrincipal, AeroUserDevicePrincipal and AeroDeviceCertPrincipal
    private static final class TestServer extends Server {

        @Override
        public void init(Server.ServerConfiguration configuration, Environment environment) throws Exception {
            super.init(configuration, environment);

            environment.addAuthenticator(new AeroDeviceCertAuthenticator());

            environment.addServiceProvider(new AeroPrincipalBinder());
            environment.addServiceProvider(new AeroUserDevicePrincipalBinder());
            environment.addServiceProvider(new AeroDeviceCertPrincipalBinder());

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
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(USERID, DEVICE));
        get.addHeader(AeroDeviceCert.AERO_VERIFY_HEADER, AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE);
        get.addHeader(AeroDeviceCert.AERO_DNAME_HEADER, getDNameHeaderValue(USERID, DEVICE));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readResponseEntityToString(response), equalTo(String.format("%s:%s", USERID, DEVICE)));
    }

    @Test
    public void shouldGet200AndInjectAeroUserDevicePrincipalIfProperlyAuthenticatedRequestIsMade() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r1");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(USERID, DEVICE));
        get.addHeader(AeroDeviceCert.AERO_VERIFY_HEADER, AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE);
        get.addHeader(AeroDeviceCert.AERO_DNAME_HEADER, getDNameHeaderValue(USERID, DEVICE));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readResponseEntityToString(response), equalTo(String.format("%s:%s", USERID, DEVICE)));
    }

    @Test
    public void shouldGet200AndInjectAeroDeviceCertPrincipalIfProperlyAuthenticatedRequestIsMade() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r2");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(USERID, DEVICE));
        get.addHeader(AeroDeviceCert.AERO_VERIFY_HEADER, AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE);
        get.addHeader(AeroDeviceCert.AERO_DNAME_HEADER, getDNameHeaderValue(USERID, DEVICE));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
        assertThat(HttpUtils.readResponseEntityToString(response), equalTo(String.format("%s:%s", USERID, DEVICE)));
    }

    @Test
    public void shouldGet403IfProperlyAuthenticatedRequestIsMadeToAResourceThatShouldOnlyBeAccessedByPrincipalsInServiceRole() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r3");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(USERID, DEVICE));
        get.addHeader(AeroDeviceCert.AERO_VERIFY_HEADER, AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE);
        get.addHeader(AeroDeviceCert.AERO_DNAME_HEADER, getDNameHeaderValue(USERID, DEVICE));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_FORBIDDEN));
    }

    @Test
    public void shouldGet401IfVerifyHeaderDoesNotHaveSuccessValue() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r2");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(USERID, DEVICE));
        get.addHeader(AeroDeviceCert.AERO_VERIFY_HEADER, "FAILED");
        get.addHeader(AeroDeviceCert.AERO_DNAME_HEADER, getDNameHeaderValue(USERID, DEVICE));

        // note that although the values are correct, we trust nginx to properly populate the VERIFY header

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_UNAUTHORIZED));
    }

    @Test
    public void shouldGet401IfUserAndDeviceRetrievedFromCertDoesNotMatchTheOnesInTheHeader0() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue("jon@aerofs.com", DEVICE)); // report that we are jon@aerofs.com
        get.addHeader(AeroDeviceCert.AERO_VERIFY_HEADER, AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE);
        get.addHeader(AeroDeviceCert.AERO_DNAME_HEADER, getDNameHeaderValue(USERID, DEVICE)); // but cname is actually for allen@aerofs.com

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_UNAUTHORIZED));
    }

    @Test
    public void shouldGet401IfUserAndDeviceRetrievedFromCertDoesNotMatchTheOnesInTheHeader1() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(USERID, "acf5cdf7876af1610ddf7fef16d559c2")); // report that we are have a different device than the one in the cname
        get.addHeader(AeroDeviceCert.AERO_VERIFY_HEADER, AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE);
        get.addHeader(AeroDeviceCert.AERO_DNAME_HEADER, getDNameHeaderValue(USERID, DEVICE)); // but cname is for USERID, DEVICE

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_UNAUTHORIZED));
    }

    @Test
    public void shouldGet400IfVerifyHeaderIsMissing() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(USERID, DEVICE));
        get.addHeader(AeroDeviceCert.AERO_DNAME_HEADER, getDNameHeaderValue(USERID, DEVICE));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldGet400IfDNameHeaderIsMissing() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r2");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(USERID, DEVICE));
        get.addHeader(AeroDeviceCert.AERO_VERIFY_HEADER, AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE);

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldGet400IfAuthorizationHeaderInRequestHasInvalidFormat() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r0");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(USERID, DEVICE) + " param0 param1"); // add random junk
        get.addHeader(AeroDeviceCert.AERO_VERIFY_HEADER, AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE);
        get.addHeader(AeroDeviceCert.AERO_DNAME_HEADER, getDNameHeaderValue(USERID, DEVICE));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldGet400IfUserInRequestHasInvalidFormat() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r1");
        get.addHeader(HttpHeaders.AUTHORIZATION, String.format("Aero-Device-Cert %s %s", USERID, DEVICE)); // userid is *not* base64 encoded
        get.addHeader(AeroDeviceCert.AERO_VERIFY_HEADER, AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE);
        get.addHeader(AeroDeviceCert.AERO_DNAME_HEADER, getDNameHeaderValue(USERID, DEVICE));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldGet400IfDeviceRequestHasInvalidFormat() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r1");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(USERID, "notahexdeviceid")); // obviously, not a hex device id
        get.addHeader(AeroDeviceCert.AERO_VERIFY_HEADER, AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE);
        get.addHeader(AeroDeviceCert.AERO_DNAME_HEADER, getDNameHeaderValue(USERID, DEVICE));

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldGet400IfDNameInRequestHasInvalidFormat0() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r1");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(USERID, DEVICE));
        get.addHeader(AeroDeviceCert.AERO_VERIFY_HEADER, AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE);
        get.addHeader(AeroDeviceCert.AERO_DNAME_HEADER, AeroDeviceCert.getCertificateCName(USERID, DEVICE)); // has a cname, but it is not prefixed by 'CN='

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldGet400IfDNameInRequestHasInvalidFormat1() throws IOException {
        HttpGet get = new HttpGet(server.getServiceURL() + "/r1");
        get.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeaderValue(USERID, DEVICE));
        get.addHeader(AeroDeviceCert.AERO_VERIFY_HEADER, AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE);
        get.addHeader(AeroDeviceCert.AERO_DNAME_HEADER, "CN="); // has the CN= tag, but absolutely no value

        HttpResponse response = client.execute(get);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    private static String getDNameHeaderValue(String user, String device) {
        return "CN=" + AeroDeviceCert.getCertificateCName(user, device);
    }

    private static String getAuthHeaderValue(String user, String device) {
        return com.aerofs.auth.client.cert.AeroDeviceCert.getHeaderValue(user, device);
    }
}