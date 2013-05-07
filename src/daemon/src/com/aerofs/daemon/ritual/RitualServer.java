package com.aerofs.daemon.ritual;

import com.aerofs.base.Loggers;
import com.aerofs.base.async.FutureUtil;
import com.aerofs.daemon.transport.lib.AddressUtils;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExIndexing;
import com.aerofs.proto.Ritual.IRitualService;
import com.aerofs.proto.Ritual.RitualServiceReactor;
import com.aerofs.proto.Ritual.RitualServiceReactor.ServiceRpcTypes;
import com.aerofs.proto.RpcService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.slf4j.Logger;

import java.net.InetSocketAddress;

import static com.aerofs.lib.LibParam.Ritual.LENGTH_FIELD_SIZE;
import static com.aerofs.lib.LibParam.Ritual.MAX_FRAME_LENGTH;

public class RitualServer
{
    private static final Logger l = Loggers.getLogger(RitualServer.class);

    private final int port = Cfg.port(Cfg.PortType.RITUAL);
    private final String host = Cfg.db().get(Key.RITUAL_BIND_ADDR);

    private final ServerSocketChannelFactory _serverChannelFactory;

    public RitualServer(ServerSocketChannelFactory serverChannelFactory)
    {
        _serverChannelFactory = serverChannelFactory;
    }

    public void start_()
    {
        // Configure the server.

        ServerBootstrap bootstrap = new ServerBootstrap(_serverChannelFactory);

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

        InetSocketAddress address = host.equals("*") ?
                new InetSocketAddress(port) : new InetSocketAddress(host, port);
        bootstrap.bind(address);

        // TODO (EK) move log back to debug once arkoot's bug is fixed.
        l.info("The ritual has begun on " + AddressUtils.printaddr(address));
    }

    /**
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

                // OK, this is not pretty but duplicating this line in every method of RitualService
                // would be a lot worse. Basically we want to make sure callers are aware that a
                // potentially lengthy first-launch indexing is in progress so that they can convey
                // the need for patience back to the user. To that end, we all accept incoming calls
                // and throw a sufficiently specific exception until the indexing succeeds.
                if (Cfg.db().getBoolean(Key.FIRST_START)) {
                    RpcService.Payload p = RpcService.Payload.newBuilder()
                            .setType(ServiceRpcTypes.__ERROR__.ordinal())
                            .setPayloadData(_service.encodeError(new ExIndexing()).toByteString())
                            .build();
                    channel.write(ChannelBuffers.copiedBuffer(p.toByteArray()));
                    return;
                }
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
                        SystemUtil.fatal(throwable);
                    }
                });
            } catch (Exception ex) {
                l.warn("RitualServerHandler: Exception " + Util.e(ex));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        {
            SystemUtil.fatalOnUncheckedException(e.getCause());

            // Close the connection when an exception is raised.
            l.warn("ritual server exception: " + Util.e(e.getCause()));
            e.getChannel().close();
        }
    }
}
