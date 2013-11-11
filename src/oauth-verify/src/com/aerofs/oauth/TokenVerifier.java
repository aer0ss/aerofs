package com.aerofs.oauth;

import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.ICertificateProvider;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.slf4j.Logger;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Synchronous verification of OAuth tokens with an in-memory cache.
 */
public class TokenVerifier extends CacheLoader<String, VerifyTokenResponse>
{
    private final static Logger l = Loggers.getLogger(TokenVerifier.class);

    private final String _auth;
    private final TokenVerificationClient _client;
    private final LoadingCache<String, VerifyTokenResponse> _cache;

    private static CacheBuilder defaultSettings() {
        return CacheBuilder
            .newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(1000L);
    }

    public TokenVerifier(String clientId, String clientSecret, URI endpoint,
            ICertificateProvider cacert, ClientSocketChannelFactory clientChannelFactory)
    {
        this(clientId, clientSecret, defaultSettings(), endpoint, cacert, clientChannelFactory);
    }

    @SuppressWarnings("unchecked")
    public TokenVerifier(String clientId, String clientSecret, CacheBuilder builder,
            URI endpoint, ICertificateProvider cacert, ClientSocketChannelFactory clientChannelFactory)
    {
        _auth = TokenVerificationClient.makeAuth(clientId, clientSecret);
        _client = new TokenVerificationClient(endpoint, cacert,clientChannelFactory);
        _cache = ((CacheBuilder<String, VerifyTokenResponse>)builder).build(this);
    }

    @SuppressWarnings("unchecked")
    public TokenVerifier(String clientId, String clientSecret, TokenVerificationClient client,
            CacheBuilder builder)
    {
        _auth = TokenVerificationClient.makeAuth(clientId, clientSecret);
        _client = client;
        _cache = ((CacheBuilder<String, VerifyTokenResponse>)builder).build(this);
    }

    public VerifyTokenResponse verify(String accessToken) throws Exception
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
            return _client.verify(accessToken, _auth).get();
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
