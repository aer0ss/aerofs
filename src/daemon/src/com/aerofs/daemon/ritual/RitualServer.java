package com.aerofs.daemon.ritual;

import com.aerofs.lib.Util;
import com.aerofs.lib.async.FutureUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.proto.Ritual.IRitualService;
import com.aerofs.proto.Ritual.RitualServiceReactor;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static com.aerofs.lib.Param.Ritual.LENGTH_FIELD_SIZE;
import static com.aerofs.lib.Param.Ritual.MAX_FRAME_LENGTH;

public class RitualServer
{
    final static Logger l = Util.l(RitualServer.class);
    private final int port = Cfg.port(Cfg.PortType.RITUAL);
    private final String host = Cfg.db().get(Key.RITUAL_BIND_ADDR);

    public RitualServer()
    {
    }

    public void start_()
    {
        // Configure the server.

        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        // Set up the pipeline factory.
        // RitualServerHandler is the class that will receive the bytes from the client

        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
                    throws Exception
            {
                return Channels.pipeline(
                        new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, LENGTH_FIELD_SIZE, 0,
                                LENGTH_FIELD_SIZE),
                        new LengthFieldPrepender(LENGTH_FIELD_SIZE),
                        new RitualServerHandler());
            }
        });

        // Bind and start to accept incoming connections.

        InetSocketAddress address = host.equals("*") ? new InetSocketAddress(port)
                                                     : new InetSocketAddress(host, port);
        bootstrap.bind(address);

        l.info("The ritual has begun on " + address.getHostName() + ":" + address.getPort());
    }

    /**
     * @internal
     * This is the Netty class that receives the data from the client
     * It handles the data to the Reactor, and writes back the reply to the client
     */
    private static class RitualServerHandler extends SimpleChannelUpstreamHandler
    {
        private final IRitualService _service = new RitualService();
        private final RitualServiceReactor _reactor = new RitualServiceReactor(_service);

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
        {
            try {
                byte[] message = ((ChannelBuffer) e.getMessage()).array();

                final Channel channel = e.getChannel();
                ListenableFuture<byte[]> future = _reactor.react(message);

                FutureUtil.addCallback(future, new FutureCallback<byte[]>()
                {
                    @Override
                    public void onSuccess(byte[] bytes)
                    {
                        ChannelBuffer buffer = ChannelBuffers.copiedBuffer(bytes);
                        channel.write(buffer);
                    }

                    @Override
                    public void onFailure(Throwable throwable)
                    {
                        l.warn("Ritual server: received an exception from the reactor. This should never happen. Aborting.");
                        Util.fatal(throwable);
                    }
                });
            } catch (Exception ex) {
                l.warn("RitualServerHandler: Exception " + Util.e(ex));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        {
            // Close the connection when an exception is raised.
            l.warn("Unexpected exception from ritual client. " + Util.e(e.getCause()));
            e.getChannel().close();
        }
    }
}
