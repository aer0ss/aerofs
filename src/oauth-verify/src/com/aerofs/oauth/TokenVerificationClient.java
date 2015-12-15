package com.aerofs.oauth;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.oauth.OAuthVerificationHandler.VerifyTokenRequest;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSessionContext;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * Direct asynchronous verification of OAuth tokens
 */
public class TokenVerificationClient extends IdleStateAwareChannelHandler
{
    private final static Logger l = Loggers.getLogger(TokenVerificationClient.class);

    private final URI _endpoint;
    private final ClientBootstrap _bootstrap;
    private final SSLEngineFactory _sslEngineFactory;

    private Channel _channel;
    private long _lastWrite;

    private static class ClientSSLEngineFactory extends SSLEngineFactory
    {
        public ClientSSLEngineFactory(ICertificateProvider trustedCA)
        {
            super(Mode.Client, Platform.Desktop, null, trustedCA, null);
        }

        @Override
        protected void onSSLContextCreated(SSLContext context)
        {
            SSLSessionContext sessionContext = context.getClientSessionContext();
            sessionContext.setSessionCacheSize(1);
            sessionContext.setSessionTimeout((int)C.HOUR);
        }
    }

    public TokenVerificationClient(URI endpoint, final ICertificateProvider cacert,
            ClientSocketChannelFactory clientChannelFactory, final Timer timer)
    {
        _endpoint = fixPort(endpoint);
        _sslEngineFactory = new ClientSSLEngineFactory(cacert);
        _bootstrap = new ClientBootstrap(clientChannelFactory);
        _bootstrap.setPipelineFactory(() -> {
            ChannelPipeline p = Channels.pipeline(
                    new HttpClientCodec(),
                    new HttpChunkAggregator(2 * C.KB),
                    new IdleStateHandler(timer, 0, 0, 5, TimeUnit.SECONDS),
                    new OAuthVerificationHandler<>(_endpoint,
                            VerifyTokenResponse.class),
                    this
            );
            if (_endpoint.getScheme().equals("https")) {
                p.addFirst("ssl", _sslEngineFactory.newSslHandler());
            }
            return p;
        });
    }

    private static URI fixPort(URI uri)
    {
        if (uri.getPort() != -1) return uri;
        Preconditions.checkState(ImmutableSet.of("http", "https").contains(uri.getScheme()));
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(),
                    ("http".equals(uri.getScheme()) ? 80 : 443),
                    uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    public static String makeAuth(String clientId, String clientSecret)
    {
        return "Basic " + Base64.encodeBytes(BaseUtil.string2utf(clientId + ":" + clientSecret));
    }

    public ListenableFuture<VerifyTokenResponse> verify(String accessToken, String key, String secret)
    {
        return verify(accessToken, makeAuth(key, secret));
    }

    public ListenableFuture<VerifyTokenResponse> verify(String accessToken, String auth)
    {
        return verify(new VerifyTokenRequest<>(accessToken, auth));
    }

    private ListenableFuture<VerifyTokenResponse> verify(final VerifyTokenRequest<VerifyTokenResponse> req)
    {
        synchronized (this) {
            if (_channel != null) {
                write(req);
                return req.future;
            }
        }

        l.debug("connect");

        _bootstrap.connect(new InetSocketAddress(_endpoint.getHost(), _endpoint.getPort()))
                .addListener(cf -> {
                    if (cf.isSuccess()) {
                        l.debug("connected");
                        onConnect(cf.getChannel(), req);
                    } else {
                        l.info("failed to connect");
                        req.future.setException(cf.getCause());
                    }
                });

        return req.future;
    }

    private void onConnect(Channel c, final VerifyTokenRequest<VerifyTokenResponse> req)
    {
        synchronized (this) {
            _channel = c;
            write(req);
        }

        c.getCloseFuture().addListener(channelFuture -> {
            synchronized (TokenVerificationClient.this) {
                l.debug("closed");
                _channel = null;
            }
        });
    }

    private void write(final VerifyTokenRequest<VerifyTokenResponse> req)
    {
        l.debug("write {}", req.accessToken);
        _lastWrite = System.nanoTime();
        _channel.write(req).addListener(cf -> {
            if (!cf.isSuccess()) {
                l.warn("failed to write");
                req.future.setException(cf.getCause());
                cf.getChannel().close();
            }
        });
    }

    @Override
    public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
    {
        synchronized (this) {
            // IdleStateHandler only resets timer on writeComplete
            // this leaves a window for a write to be enqueued right before the idle timeout fires
            if (System.nanoTime() < _lastWrite + TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS)) {
                return;
            }
            if (_channel == ctx.getChannel()) {
                _channel = null;
            }
        }
        l.debug("timeout");
        ctx.getChannel().close();
    }
}
