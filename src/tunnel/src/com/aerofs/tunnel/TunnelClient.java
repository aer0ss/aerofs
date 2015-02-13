package com.aerofs.tunnel;

import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.base.net.AbstractNettyReconnectingClient;
import com.aerofs.base.net.NettyUtil;
import com.aerofs.base.ssl.SSLEngineFactory;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.jboss.netty.util.Timer;

import java.net.InetSocketAddress;

/**
 * Tunnel client: connects to a Tunnel server and generates virtual server channels
 */
public class TunnelClient extends AbstractNettyReconnectingClient
{
    private final UserID _user;
    private final DID _did;
    private final SSLEngineFactory _sslEngineFactory;
    private final ChannelPipelineFactory _pipelineFactory;

    private final ClientBootstrap _bootstrap;
    private final String _host;
    private final int _port;


    public TunnelClient(String host, int port, UserID user, DID did,
            ClientSocketChannelFactory channelFactory, SSLEngineFactory sslEngineFactory,
            ChannelPipelineFactory pipelineFactory, Timer timer)
    {
        super(timer);
        _host = host;
        _port = port;
        _bootstrap = new ClientBootstrap(channelFactory);
        _user = user;
        _did = did;
        _sslEngineFactory = sslEngineFactory;
        _pipelineFactory = pipelineFactory;
    }

    @Override
    protected ChannelFuture connect()
    {
        _bootstrap.setPipelineFactory(pipelineFactory());
        // NB: create a new InetSocketAddress on every connection, otherwise failure to resolve
        // DNS on the first connection will prevent any future connection form ever succeeding
        return _bootstrap.connect(new InetSocketAddress(_host, _port));
    }

    @Override
    protected ChannelPipelineFactory pipelineFactory()
    {
        return () -> {
            TunnelHandler handler = new TunnelHandler(null, _pipelineFactory);
            return Channels.pipeline(_sslEngineFactory.newSslHandler(),
                    TunnelHandler.newFrameDecoder(),
                    TunnelHandler.newLengthFieldPrepender(),
                    NettyUtil.newCNameVerificationHandler(handler, _user, _did),
                    new ChunkedWriteHandler(),
                    TunnelHandler.newIdleStateHandler(_timer),
                    handler);
        };
    }
}
