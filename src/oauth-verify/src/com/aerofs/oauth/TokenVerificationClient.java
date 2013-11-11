package com.aerofs.oauth;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.net.NettyUtil;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.oauth.OAuthVerificationHandler.VerifyTokenRequest;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Direct asynchronous verification of OAuth tokens
 */
public class TokenVerificationClient
{
    private final static Logger l = Loggers.getLogger(TokenVerificationClient.class);

    private final URI _endpoint;
    private final ClientBootstrap _bootstrap;

    public TokenVerificationClient(URI endpoint, final ICertificateProvider cacert,
            ClientSocketChannelFactory clientChannelFactory)
    {
        _endpoint = fixPort(endpoint);
        _bootstrap = new ClientBootstrap(clientChannelFactory);
        _bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                ChannelPipeline p = Channels.pipeline(
                        new HttpClientCodec(),
                        new HttpChunkAggregator(2 * C.KB),
                        new OAuthVerificationHandler<VerifyTokenResponse>(_endpoint,
                                VerifyTokenResponse.class)
                );
                if (_endpoint.getScheme().equals("https")) {
                    p.addFirst("ssl", NettyUtil.newSslHandler(new SSLEngineFactory(
                            Mode.Client, Platform.Desktop, null, cacert, null)));
                }
                return p;
            }
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

    public ListenableFuture<VerifyTokenResponse> verify(String accessToken, String clientId, String clientSecret)
    {
        return verify(accessToken, makeAuth(clientId, clientSecret));
    }

    public ListenableFuture<VerifyTokenResponse> verify(String accessToken, String auth)
    {
        return verify(new VerifyTokenRequest<VerifyTokenResponse>(accessToken, auth));
    }

    private ListenableFuture<VerifyTokenResponse> verify(final VerifyTokenRequest<VerifyTokenResponse> req)
    {
        _bootstrap.connect(new InetSocketAddress(_endpoint.getHost(), _endpoint.getPort()))
                .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture cf) throws Exception
                    {
                        if (cf.isSuccess()) {
                            cf.getChannel().write(req);
                        } else {
                            req.future.setException(cf.getCause());
                        }
                    }
                });
        return req.future;
    }
}
