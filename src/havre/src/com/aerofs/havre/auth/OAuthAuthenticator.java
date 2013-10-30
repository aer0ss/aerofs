package com.aerofs.havre.auth;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.havre.Authenticator;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.oauth.TokenVerifier;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;

import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.Map;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 * OAuth2 authenticator that offloads token verification to bifrost
 */
public class OAuthAuthenticator implements Authenticator
{
    private final static Logger l = Loggers.getLogger(OAuthAuthenticator.class);

    private final TokenVerifier _verifier;

    public OAuthAuthenticator(ICertificateProvider cacert)
    {
        _verifier = new TokenVerifier(
                getStringProperty("havre.oauth.id", ""),
                getStringProperty("havre.oauth.secret", ""),
                URI.create(getStringProperty("havre.oauth.url", "http://localhost:8700/tokeninfo")),
                cacert,
                new NioClientSocketChannelFactory());
    }


    @Override
    public AuthenticatedPrincipal authenticate(HttpRequest request)
            throws UnauthorizedUserException
    {
        QueryStringDecoder decoder = new QueryStringDecoder(request.getUri());
        Map<String, List<String>> params = decoder.getParameters();
        List<String> access = params.get("access_token");
        if (access == null || access.isEmpty()) throw new UnauthorizedUserException();

        try {
            return _verifier.verify(access.get(0)).principal;
        } catch (Exception e) {
            l.error("failed to verify token", BaseLogUtil.suppress(e.getCause(),
                    ClosedChannelException.class));
            throw new UnauthorizedUserException();
        }
    }
}
