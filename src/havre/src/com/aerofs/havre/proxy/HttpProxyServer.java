package com.aerofs.havre.proxy;

import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.base.net.AbstractNettyServer;
import com.aerofs.havre.Authenticator;
import com.aerofs.havre.EndpointConnector;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpServerCodec;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

/**
 * Transparent HTTP proxy
 *
 *  *-------------------*  == REQUEST ==> *-------* == FWD REQ ==> *---------------*
 *  | downstream caller |                 | proxy |                | upstream host |
 *  *-------------------*  <= FWD RESP == *-------* <= RESPONSE == *---------------*
 */
public class HttpProxyServer extends AbstractNettyServer
{
    private final EndpointConnector _connector;
    private final Authenticator _auth;

    public HttpProxyServer(InetSocketAddress addr, @Nullable IPrivateKeyProvider key,
            Authenticator auth, EndpointConnector connector) {
        super("http_proxy", addr, key, null);
        _auth = auth;
        _connector = connector;
    }

    @Override
    protected ChannelPipeline getSpecializedPipeline()
    {
        return Channels.pipeline(
                new HttpServerCodec(),
                new HttpRequestProxyHandler(_auth, _connector, _allChannels));
    }
}
