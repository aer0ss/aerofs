/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap;

import com.aerofs.daemon.tng.base.EventQueueBasedEventLoop;
import com.aerofs.base.C;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.base.async.FutureUtil;
import com.aerofs.proto.Tap;
import com.aerofs.proto.Tap.ITapService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import org.slf4j.Logger;
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
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class TapProgram implements IProgram
{
    private static final Logger l = Util.l(TapProgram.class);

    private Injector _tapInjector;

    @Override
    public void launch_(String rtRoot, String prog, String[] args)
            throws Exception
    {
        final int port;

        if (args.length < 1) {
            l.info("Using default port 3001");
            port = 3001;
        } else {
            port = Integer.valueOf(args[0]);
        }

        _tapInjector = Guice.createInjector(Stage.PRODUCTION, new TapModule());

        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {

            @Override
            public ChannelPipeline getPipeline()
                    throws Exception
            {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder",
                        new LengthFieldBasedFrameDecoder(C.MB, 0, Integer.SIZE / Byte.SIZE, 0,
                                Integer.SIZE / Byte.SIZE));
                pipeline.addLast("encoder", new LengthFieldPrepender(Integer.SIZE / Byte.SIZE));
                pipeline.addLast("service", new ServiceHandler());
                return pipeline;
            }

        });

        // This will return a singleton instance, so it is ok to do this to start
        // the event loop
        _tapInjector.getInstance(EventQueueBasedEventLoop.class).start_();

        bootstrap.bind(new InetSocketAddress(port));
    }

    private class ServiceHandler extends SimpleChannelUpstreamHandler
    {
        private final Tap.TapServiceReactor _reactor = new Tap.TapServiceReactor(
                _tapInjector.getInstance(ITapService.class));

        @Override
        public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception
        {
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
                    SystemUtil.fatal(throwable);
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception
        {
            l.error(Util.e(e.getCause()));
        }
    }

}
