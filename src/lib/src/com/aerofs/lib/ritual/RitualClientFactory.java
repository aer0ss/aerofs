package com.aerofs.lib.ritual;

import com.aerofs.lib.C;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static com.aerofs.lib.Param.Ritual.LENGTH_FIELD_SIZE;
import static com.aerofs.lib.Param.Ritual.MAX_FRAME_LENGTH;

public class RitualClientFactory
{
    /**
     * Return a new instance to a blocking (ie: synchronous) client to Ritual.
     * See doc in RitualBlockingClient for more information.
     */
    public static RitualBlockingClient newBlockingClient()
    {
        Channel channel = getConnectedChannel();
        RitualClientHandler handler = (RitualClientHandler) channel.getPipeline().getLast();
        return new RitualBlockingClient(handler);
    }

    /**
     * Return a new instance to a future-based, asynchronous client to Ritual.
     * This is the preferred interface to Ritual.
     * See doc in RitualClient for more information.
     */
    public static RitualClient newClient()
    {
        Channel channel = getConnectedChannel();
        RitualClientHandler handler = (RitualClientHandler) channel.getPipeline().getLast();
        return new RitualClient(handler);
    }

    private static final ChannelFactory _factory = new NioClientSocketChannelFactory(
            Executors.newCachedThreadPool(),
            Executors.newCachedThreadPool());

    private static final ClientBootstrap _bootstrap = new ClientBootstrap(_factory);
    static {
        _bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
            {
                return Channels.pipeline(
                        new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, LENGTH_FIELD_SIZE, 0,
                                LENGTH_FIELD_SIZE), new LengthFieldPrepender(LENGTH_FIELD_SIZE),
                        new RitualClientHandler());
            }
        });
    }

    /**
     * Creates a new Netty Channel connected to the Ritual server on localhost.
     * This is non-blocking, and you can start doing requests right after calling it.
     * RitualClientHandler will queue up requests until we are actually connected.
     */
    private static Channel getConnectedChannel()
    {
        ChannelFuture future = _bootstrap.connect(
                new InetSocketAddress(C.LOCALHOST_ADDR, Cfg.port(PortType.RITUAL)));

        return future.getChannel();
    }
}
