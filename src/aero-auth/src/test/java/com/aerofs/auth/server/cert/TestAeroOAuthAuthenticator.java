package com.aerofs.auth.server.cert;

import com.aerofs.auth.server.AeroOAuthPrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.baseline.auth.AuthenticationException;
import com.aerofs.baseline.auth.AuthenticationResult;
import com.aerofs.ids.MDID;
import com.aerofs.ids.UserID;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.oauth.TokenVerifier;
import com.aerofs.oauth.VerifyTokenResponse;
import com.google.common.net.HttpHeaders;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public final class TestAeroOAuthAuthenticator
{
    private TokenVerifier _verifier = mock(TokenVerifier.class);
    private AeroOAuthAuthenticator authenticator = new AeroOAuthAuthenticator(_verifier);

    private static final MDID DEVICE = MDID.generate();

    @Test
    public void shouldAuthenticateWithValidToken() throws Exception
    {
        AuthenticatedPrincipal testPrincipal = new AuthenticatedPrincipal("batman@gotham.city",
                UserID.fromExternal("batman@gotham.city"), OrganizationID.PRIVATE_ORGANIZATION);
        VerifyTokenResponse tokenResponse = new VerifyTokenResponse("oauth-test",
                Sets.newSet("rwtoken"),10L, testPrincipal, DEVICE.toStringFormal());

        MultivaluedMap<String, String> fakeHeaders = new MultivaluedHashMap<>();
        fakeHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer 123");
        when(_verifier.verifyHeader("Bearer 123")).thenReturn(tokenResponse);

        AuthenticationResult result = authenticator.authenticate(fakeHeaders);
        verify(_verifier).verifyHeader("Bearer 123");

        assertEquals(result.getStatus(), AuthenticationResult.Status.SUCCEEDED);

        assertTrue(result.getSecurityContext().getUserPrincipal() instanceof AeroOAuthPrincipal);
        AeroOAuthPrincipal resultPrincipal =
                (AeroOAuthPrincipal) result.getSecurityContext().getUserPrincipal();
        assertEquals(resultPrincipal.getName(), testPrincipal.getName());
        assertEquals(resultPrincipal.getDID(), DEVICE);
        assertEquals(resultPrincipal.getUser(), testPrincipal.getIssuingUserID());

        assertTrue(result.getSecurityContext().isUserInRole(Roles.USER));
    }

    @Test
    public void shouldGetUnsupportedResultWithNoHeader() throws Exception
    {
        MultivaluedMap<String, String> fakeHeaders = new MultivaluedHashMap<>();
        AuthenticationResult result = authenticator.authenticate(fakeHeaders);
        assertEquals(result.getStatus(), AuthenticationResult.Status.UNSUPPORTED);
    }

    @Test(expected = AuthenticationException.class)
    public void shouldThrowWithMultipleAuthHeaders() throws Exception
    {
        MultivaluedMap<String, String> fakeHeaders = new MultivaluedHashMap<>();
        fakeHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer 123");
        fakeHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer 321");
        authenticator.authenticate(fakeHeaders);
    }

    @Test
    public void shouldGetUnsupportedResultWithUnrecognizedAuthHeaders() throws Exception
    {
        MultivaluedMap<String, String> fakeHeaders = new MultivaluedHashMap<>();
        fakeHeaders.add(HttpHeaders.AUTHORIZATION, "Just a beluga");
        AuthenticationResult result = authenticator.authenticate(fakeHeaders);
        assertEquals(result.getStatus(), AuthenticationResult.Status.UNSUPPORTED);
    }

    @Test
    public void shouldGetFailedResultWithUnverifiedToken() throws Exception
    {
        MultivaluedMap<String, String> fakeHeaders = new MultivaluedHashMap<>();
        fakeHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer 123");
        when(_verifier.verifyHeader("Bearer 123")).thenReturn(null);

        AuthenticationResult result = authenticator.authenticate(fakeHeaders);
        verify(_verifier).verifyHeader("Bearer 123");

        assertEquals(result.getStatus(), AuthenticationResult.Status.FAILED);

    }
}
