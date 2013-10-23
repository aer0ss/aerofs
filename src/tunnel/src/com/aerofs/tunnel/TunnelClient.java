package com.aerofs.tunnel;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.NettyUtil;
import com.aerofs.base.ssl.SSLEngineFactory;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import java.net.SocketAddress;

/**
 * Tunnel client: connects to a Tunnel server and generates virtual server channels
 */
public class TunnelClient
{
    protected static final Logger l = Loggers.getLogger(TunnelClient.class);

    private final ClientBootstrap _bootstrap;

    public TunnelClient(final UserID user, final DID did, ClientSocketChannelFactory channelFactory,
            final SSLEngineFactory sslEngineFactory, final ChannelPipelineFactory pipelineFactory,
            final Timer timer)
    {
        _bootstrap = new ClientBootstrap(channelFactory);
        _bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                TunnelHandler handler = new TunnelHandler(null, pipelineFactory);
                return Channels.pipeline(
                        NettyUtil.newSslHandler(sslEngineFactory),
                        TunnelHandler.newFrameDecoder(),
                        TunnelHandler.newLengthFieldPrepender(),
                        NettyUtil.newCNameVerificationHandler(handler, user, did),
                        new ChunkedWriteHandler(),
                        TunnelHandler.newIdleStateHandler(timer),
                        handler);
            }
        });
    }

    public ChannelFuture connect(SocketAddress addr)
    {
        return _bootstrap.connect(addr);
    }
}
