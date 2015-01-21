package com.aerofs.daemon.ritual;

import com.aerofs.base.Loggers;
import com.aerofs.base.async.FutureUtil;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.net.NettyUtil;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExIndexing;
import com.aerofs.lib.ex.ExUpdating;
import com.aerofs.lib.nativesocket.AbstractNativeSocketPeerAuthenticator;
import com.aerofs.lib.nativesocket.NativeSocketHelper;
import com.aerofs.lib.nativesocket.RitualSocketFile;
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
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.newsclub.net.unix.NativeSocketAddress;
import org.slf4j.Logger;

import java.io.File;

import static com.aerofs.lib.LibParam.Ritual.LENGTH_FIELD_SIZE;
import static com.aerofs.lib.LibParam.Ritual.MAX_FRAME_LENGTH;

public class RitualServer
{
    private static final Logger l = Loggers.getLogger(RitualServer.class);

    private final File _ritualSocketFile;
    private final ServerBootstrap _bootstrap;

    public RitualServer(RitualSocketFile rsf,
            AbstractNativeSocketPeerAuthenticator authenticator)
    {
        _ritualSocketFile = rsf.get();
        ChannelPipelineFactory _pipelineFactory = () -> (Channels.pipeline(
                authenticator,
                new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, LENGTH_FIELD_SIZE, 0,
                        LENGTH_FIELD_SIZE), new LengthFieldPrepender(LENGTH_FIELD_SIZE),
                new RitualServerHandler()));
        _bootstrap = NativeSocketHelper.createServerBootstrap(_ritualSocketFile, _pipelineFactory);
    }

    public void start_()
    {
        _bootstrap.bind(new NativeSocketAddress(_ritualSocketFile));
        l.info("Ritual server bound to {}", _ritualSocketFile);
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
                byte[] message = NettyUtil.toByteArray((ChannelBuffer)e.getMessage());

                final Channel channel = e.getChannel();

                // OK, this is not pretty but duplicating this line in every method of RitualService
                // would be a lot worse. Basically we want to make sure callers are aware that a
                // potentially lengthy first-launch indexing is in progress so that they can convey
                // the need for patience back to the user. To that end, we all accept incoming calls
                // and throw a sufficiently specific exception until the indexing succeeds.
                AbstractExWirable ex = null;
                if (Cfg.hasPendingDPUT()) {
                    ex = new ExUpdating();
                } else if (Cfg.db().getBoolean(Key.FIRST_START)) {
                    ex = new ExIndexing();
                }
                if (ex != null) {
                    l.debug("ritual ex reply: {}", ex.getWireType());
                    channel.write(ChannelBuffers.copiedBuffer(buildErrorReply(ex)));
                    return;
                }

                if (l.isDebugEnabled()) {
                    try {
                        int callType;
                        com.aerofs.proto.RpcService.Payload p = com.aerofs.proto.RpcService.Payload.parseFrom(message);
                        callType = p.getType();
                        ServiceRpcTypes t = ServiceRpcTypes.values()[callType];
                        l.trace("ritual msg rcv: {}", t);
                    } catch (Exception x) {
                        // if there are issues parsing the message, they will be caught and handled properly
                        // in the reactor
                        l.warn("ritual msg rcv: could not get type");
                    }
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
                        l.warn("exception from ritual reactor");
                        SystemUtil.fatal(throwable);
                    }
                });
            } catch (Throwable ex) {
                l.warn("exception from ritual reactor");
                SystemUtil.fatal(ex);
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

        private byte[] buildErrorReply(AbstractExWirable ex)
        {
            return RpcService.Payload.newBuilder()
                    .setType(ServiceRpcTypes.__ERROR__.ordinal())
                    .setPayloadData(_service.encodeError(ex).toByteString())
                    .build()
                    .toByteArray();
        }
    }
}
