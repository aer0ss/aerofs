package com.aerofs.oauth;

import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.ICertificateProvider;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synchronous verification of OAuth tokens with an in-memory cache.
 */
public class TokenVerifier extends CacheLoader<String, VerifyTokenResponse>
{
    private final static Logger l = Loggers.getLogger(TokenVerifier.class);

    private final String _auth;
    private final TokenVerificationClient _client;
    private final LoadingCache<String, VerifyTokenResponse> _cache;

    private static CacheBuilder<Object, Object> defaultSettings() {
        return CacheBuilder
            .newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(1000L);
    }

    public TokenVerifier(String clientId, String clientSecret, URI endpoint, Timer timer,
            ICertificateProvider cacert, ClientSocketChannelFactory clientChannelFactory)
    {
        this(clientId, clientSecret, defaultSettings(), endpoint, timer, cacert, clientChannelFactory);
    }

    public TokenVerifier(String clientId, String clientSecret, CacheBuilder<Object, Object> builder,
            URI endpoint, Timer timer, ICertificateProvider cacert,
            ClientSocketChannelFactory clientChannelFactory)
    {
        _auth = TokenVerificationClient.makeAuth(clientId, clientSecret);
        _client = new TokenVerificationClient(endpoint, cacert,clientChannelFactory, timer);
        _cache = builder.build(this);
    }

    public TokenVerifier(String clientId, String clientSecret, TokenVerificationClient client,
            CacheBuilder<Object, Object> builder)
    {
        _auth = TokenVerificationClient.makeAuth(clientId, clientSecret);
        _client = client;
        _cache = builder.build(this);
    }

    public @Nullable AuthenticatedPrincipal getPrincipal(String authorizationHeader) throws Exception
    {
        String token = accessToken(authorizationHeader);
        return token == null ? null : verifyToken(token).principal;
    }

    public @Nullable VerifyTokenResponse verifyHeader(String authorizationHeader) throws Exception
    {
        String token = accessToken(authorizationHeader);
        return token == null ? null : verifyToken(token);
    }

    private final static Pattern BEARER_PATTERN = Pattern.compile("^Bearer ([0-9a-zA-Z-._~+/]+=*)$");
    private static @Nullable String accessToken(@Nullable String authorizationHeader)
    {
        if (authorizationHeader != null) {
            Matcher m = BEARER_PATTERN.matcher(authorizationHeader);
            if (m.matches()) return m.group(1);
        }
        return null;
    }

    public VerifyTokenResponse verifyToken(String accessToken) throws Exception
    {
        try {
            l.debug("verify {}", accessToken);
            return _cache.get(accessToken);
        } catch (ExecutionException e) {
            throw rethrowCause(e);
        }
    }

    @Override
    public VerifyTokenResponse load(String accessToken) throws Exception
    {
        try {
            l.debug("cache miss: {}", accessToken);
            return _client.verify(accessToken, _auth).get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw rethrowCause(e);
        }
    }

    private static <T extends Throwable> T rethrowCause(T t) throws Exception
    {
        if (t.getCause() instanceof Exception) {
            throw (Exception)t.getCause();
        } else if (t instanceof Error) {
            throw (Error)t.getCause();
        } else {
            return t;
        }
    }
}
