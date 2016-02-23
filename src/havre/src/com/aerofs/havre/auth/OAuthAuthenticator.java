package com.aerofs.havre.auth;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.havre.Authenticator;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.oauth.TokenVerifier;
import org.slf4j.Logger;

import java.nio.channels.ClosedChannelException;

/**
 * OAuth2 authenticator that offloads token verification to bifrost
 */
public class OAuthAuthenticator implements Authenticator
{
    private final static Logger l = Loggers.getLogger(OAuthAuthenticator.class);

    private final TokenVerifier _verifier;

    public OAuthAuthenticator(TokenVerifier verifier)
    {
        _verifier = verifier;
    }

    @Override
    public AuthenticatedPrincipal authenticate(String token)
            throws UnauthorizedUserException
    {

        try {
            AuthenticatedPrincipal principal = _verifier.verifyToken(token).principal;
            if (principal != null) return principal;
        } catch (Exception e) {
            l.error("failed to verify token", BaseLogUtil.suppress(e.getCause(),
                    ClosedChannelException.class));
        }
        throw new UnauthorizedUserException();
    }
}
