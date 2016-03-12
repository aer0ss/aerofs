package com.aerofs.auth.server.cert;

import com.aerofs.auth.server.AeroOAuthPrincipal;
import com.aerofs.auth.server.AeroSecurityContext;
import com.aerofs.auth.server.Roles;
import com.aerofs.base.Loggers;
import com.aerofs.baseline.auth.AuthenticationException;
import com.aerofs.baseline.auth.AuthenticationResult;
import com.aerofs.baseline.auth.Authenticator;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.oauth.TokenVerifier;
import com.aerofs.oauth.VerifyTokenResponse;
import org.slf4j.Logger;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public class AeroOAuthAuthenticator implements Authenticator
{
    private final static Logger l = Loggers.getLogger(AeroOAuthAuthenticator.class);
    private TokenVerifier _tokenVerifier;

    public static final String AUTHENTICATION_SCHEME = "Bearer";

    public AeroOAuthAuthenticator(TokenVerifier verifier)
    {
        _tokenVerifier = verifier;
    }

    @Override
    public String getName()
    {
        return AUTHENTICATION_SCHEME;
    }

    @Override
    public AuthenticationResult authenticate(MultivaluedMap<String, String> headers)
            throws AuthenticationException
    {
        List<String> authHeaders = headers.get(io.netty.handler.codec.http.HttpHeaders.Names.AUTHORIZATION);

        // does it even have our auth header?
        if (authHeaders == null || authHeaders.size() == 0) {
            return AuthenticationResult.UNSUPPORTED;
        }

        // check that we only have one instance of that header
        if (authHeaders.size() > 1) {
            throw new AuthenticationException("multiple Authorization headers");
        }

        // it does - check if it's the "Bearer" one
        String authValue = authHeaders.get(0);

        if (!authValue.startsWith(AUTHENTICATION_SCHEME)) {
            return AuthenticationResult.UNSUPPORTED;
        }
        try {
            final VerifyTokenResponse response = _tokenVerifier.verifyHeader(authValue);
            final AuthenticatedPrincipal principal = response == null ? null : response.principal;
            if (principal != null) {
                AeroOAuthPrincipal ouathPrincipal =
                        new AeroOAuthPrincipal(principal.getEffectiveUserID(),
                                principal.getName(), response.mdid, response.audience, response.scopes);
                return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED,
                        new AeroSecurityContext(ouathPrincipal, Roles.USER, AUTHENTICATION_SCHEME));
            }
        } catch (Exception e) {
            l.error("failed to verify token {}", e);
        }
        return AuthenticationResult.FAILED;
    }
}
