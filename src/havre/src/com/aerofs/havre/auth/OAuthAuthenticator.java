package com.aerofs.havre.auth;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.havre.Authenticator;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.oauth.TokenVerifier;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.util.List;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 * OAuth2 authenticator that offloads token verification to bifrost
 */
public class OAuthAuthenticator implements Authenticator
{
    private final static Logger l = Loggers.getLogger(OAuthAuthenticator.class);

    private final TokenVerifier _verifier;

    public OAuthAuthenticator(Timer timer, ICertificateProvider cacert)
    {
        _verifier = new TokenVerifier(
                getStringProperty("havre.oauth.id", ""),
                getStringProperty("havre.oauth.secret", ""),
                URI.create(getStringProperty("havre.oauth.url", "http://localhost:8700/tokeninfo")),
                timer,
                cacert,
                new NioClientSocketChannelFactory());
    }

    @Override
    public AuthenticatedPrincipal authenticate(HttpRequest request)
            throws UnauthorizedUserException
    {

        // reject requests with more than one Authorization header
        List<String> authHeaders = request.getHeaders(Names.AUTHORIZATION);
        String authHeader = authHeaders != null && authHeaders.size() == 1 ?
                authHeaders.get(0) : null;

        // reject requests with more than one token query param
        List<String> tokenParams = new QueryStringDecoder(request.getUri())
                .getParameters()
                .get("token");
        String queryToken = tokenParams != null && tokenParams.size() == 1 ?
                tokenParams.get(0) : null;

        // user must include token in either the header or the query params, but not both
        if ((authHeader == null) == (queryToken == null)) throw new UnauthorizedUserException();

        try {
            AuthenticatedPrincipal principal = authHeader != null ?
                    _verifier.getPrincipal(authHeader) :
                    _verifier.verifyToken(queryToken).principal;
            if (principal != null) return principal;
        } catch (Exception e) {
            l.error("failed to verify token", BaseLogUtil.suppress(e.getCause(),
                    ClosedChannelException.class));
        }
        throw new UnauthorizedUserException();
    }
}
