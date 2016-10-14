package com.aerofs.gui.notif;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.net.NettyUtil;
import com.aerofs.lib.nativesocket.AbstractNativeSocketPeerAuthenticator;
import com.aerofs.lib.nativesocket.NativeSocketHelper;
import java.io.File;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.HeapChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.newsclub.net.unix.NativeSocketAddress;
import org.slf4j.Logger;

/**
 * This is the class that handles communication with the notif extension.
 * This class is created and owned by the NotifService. All methods should be protected or
 * private.
 *
 * NotifService should be the only public class of this package
 */
class NotifServer
{
    private final static Logger l = Loggers.getLogger(NotifServer.class);

    private final File _socketFile;
    private final NotifService _service;
    private final ServerBootstrap _bootstrap;

    protected NotifServer(NotifService service, File socketFile,
            final AbstractNativeSocketPeerAuthenticator authenticator)
    {
        _service = service;
        _socketFile = socketFile;
        ChannelPipelineFactory _pipelineFactory = () -> (Channels.pipeline(
                authenticator,
                new LengthFieldBasedFrameDecoder(C.MB, 0, C.INTEGER_SIZE, 0, C.INTEGER_SIZE),
                new LengthFieldPrepender(C.INTEGER_SIZE),
                new NotifServerHandler()));
        _bootstrap = NativeSocketHelper.createServerBootstrap(_socketFile, _pipelineFactory);
    }

    private final ChannelGroup _clients = new DefaultChannelGroup();

    protected void start_()
    {
        _bootstrap.bind(new NativeSocketAddress(_socketFile));
        l.info("Notif server bound to {}", _socketFile);
    }

    private class NotifServerHandler extends SimpleChannelUpstreamHandler
    {
        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws SocketException
        {
            _clients.add(ctx.getChannel());
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
        {
            try {
                HeapChannelBuffer buffer = (HeapChannelBuffer)e.getMessage();
                byte[] message  = new byte[buffer.readableBytes()];
                buffer.readBytes(message);
                _service.react(new String(message, StandardCharsets.UTF_8));
            } catch (Exception ex) {
                l.warn("failed to handle message", ex);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        {
            // Close the connection when an exception is raised.
            l.warn("Unexpected exception: ", BaseLogUtil.suppress(e.getCause(), SocketException.class));
            e.getChannel().close();
        }

    }
}
