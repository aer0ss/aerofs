package com.aerofs.tunnel;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.AbstractNettyReconnectingClient;
import com.aerofs.base.net.NettyUtil;
import com.aerofs.base.ssl.SSLEngineFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.jboss.netty.util.Timer;

/**
 * Tunnel client: connects to a Tunnel server and generates virtual server channels
 */
public class TunnelClient extends AbstractNettyReconnectingClient
{
    private final UserID _user;
    private final DID _did;
    private final SSLEngineFactory _sslEngineFactory;
    private final ChannelPipelineFactory _pipelineFactory;

    public TunnelClient(String host, int port, UserID user, DID did,
            ClientSocketChannelFactory channelFactory, SSLEngineFactory sslEngineFactory,
            ChannelPipelineFactory pipelineFactory, Timer timer)
    {
        super(host, port, timer, channelFactory);
        _user = user;
        _did = did;
        _sslEngineFactory = sslEngineFactory;
        _pipelineFactory = pipelineFactory;
    }

    @Override
    protected ChannelPipelineFactory pipelineFactory()
    {
        return new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                TunnelHandler handler = new TunnelHandler(null, _pipelineFactory);
                return Channels.pipeline(_sslEngineFactory.newSslHandler(),
                        TunnelHandler.newFrameDecoder(),
                        TunnelHandler.newLengthFieldPrepender(),
                        NettyUtil.newCNameVerificationHandler(handler, _user, _did),
                        new ChunkedWriteHandler(),
                        TunnelHandler.newIdleStateHandler(_timer),
                        handler);
            }
        };
    }
}
