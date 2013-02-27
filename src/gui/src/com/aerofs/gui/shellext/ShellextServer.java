package com.aerofs.gui.shellext;

import com.aerofs.base.C;
import com.aerofs.lib.Param;
import com.aerofs.lib.Util;
import org.slf4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * This is the class that handles communication with the shell extension.
 * This class is created and owned by the ShellextService. All methods should be protected or
 * private.
 *
 * ShellextService should be the only public class of this package
 */
class ShellextServer
{
    private final static Logger l = Util.l(ShellextServer.class);
    private final int _port;
    private final ShellextService _service;

    protected ShellextServer(ShellextService service, int port)
    {
        _port = port;
        _service = service;
    }

    protected int getPort()
    {
        return _port;
    }

    private final ChannelGroup _clients = new DefaultChannelGroup();

    private final ChannelPipelineFactory _factory = new ChannelPipelineFactory() {
        @Override
        public ChannelPipeline getPipeline() throws Exception
        {
            final int INT_SIZE = Integer.SIZE / Byte.SIZE;
            return Channels.pipeline(
                    new LengthFieldBasedFrameDecoder(C.MB, 0, INT_SIZE, 0, INT_SIZE),
                    new LengthFieldPrepender(INT_SIZE),
                    new ShellextServerHandler()
            );
        }
    };

    protected void start_()
    {
        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        bootstrap.setPipelineFactory(_factory);
        bootstrap.bind(new InetSocketAddress(Param.LOCALHOST_ADDR, _port));
        l.info("ShellextServer started on port " + _port);
    }

    protected void send(byte[] bytes)
    {
        _clients.write(ChannelBuffers.copiedBuffer(bytes));
    }

    private class ShellextServerHandler extends SimpleChannelUpstreamHandler
    {
        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
        {
            l.info("shell extension connected");
            _clients.add(ctx.getChannel());
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
        {
            try {
                byte[] message = ((ChannelBuffer) e.getMessage()).array();
                _service.react(message);
            } catch (Exception ex) {
                l.warn("ShellextServerHandler: Exception " + Util.e(ex));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        {
            // Close the connection when an exception is raised.
            l.warn("Unexpected exception: " + Util.e(e.getCause()));
            e.getChannel().close();
        }
    }
}
