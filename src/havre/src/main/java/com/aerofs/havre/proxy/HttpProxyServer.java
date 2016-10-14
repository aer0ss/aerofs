package com.aerofs.havre.proxy;

import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.base.net.AbstractNettyServer;
import com.aerofs.havre.Authenticator;
import com.aerofs.havre.EndpointConnector;
import com.aerofs.havre.RequestRouter;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpServerCodec;
import org.jboss.netty.util.Timer;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

import static com.aerofs.base.ssl.SSLEngineFactory.newServerFactory;

/**
 * Transparent HTTP proxy
 *
 *  *-------------------*  == REQUEST ==> *-------* == FWD REQ ==> *---------------*
 *  | downstream caller |                 | proxy |                | upstream host |
 *  *-------------------*  <= FWD RESP == *-------* <= RESPONSE == *---------------*
 */
public class HttpProxyServer extends AbstractNettyServer
{
    private final Timer _timer;
    private final RequestRouter _router;
    private final EndpointConnector _connector;
    private final Authenticator _auth;

    public HttpProxyServer(InetSocketAddress addr, @Nullable IPrivateKeyProvider key, Timer timer,
                           Authenticator auth, EndpointConnector connector, RequestRouter router) {
        super("http_proxy", addr, key != null ? newServerFactory(key, null) : null);
        _auth = auth;
        _timer = timer;
        _router = router;
        _connector = connector;
    }

    @Override
    protected ChannelPipeline getSpecializedPipeline()
    {
        return Channels.pipeline(
                new HttpServerCodec(),
                new HttpRequestProxyHandler(_timer, _auth, _connector, _router, _allChannels));
    }
}
