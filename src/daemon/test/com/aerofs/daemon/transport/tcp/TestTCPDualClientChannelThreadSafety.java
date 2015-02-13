/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.ids.DID;
import com.aerofs.daemon.transport.ChannelFactories;
import com.aerofs.daemon.transport.LoggingRule;
import com.aerofs.testlib.LoggerSetup;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public final class TestTCPDualClientChannelThreadSafety
{
    static
    {
        LoggerSetup.init();
    }

    private static final Logger l = LoggerFactory.getLogger(TestTCPDualClientChannelThreadSafety.class);

    private static final int WAIT_UNTIL_CLOSE_INTERVAL = 10000;

    //--------------------------------------------------------------------------------------------//

    // Types and Declarations

    private class TCPDevice
    {
        private final DID did = DID.generate();
        private final AtomicReference<Channel> acceptedChannel = new AtomicReference<Channel>(null);
        private ClientBootstrap clientBootstrap;
        private ServerBootstrap serverBootstrap;
    }

    private final TCPDevice device0 = new TCPDevice();
    private final TCPDevice device1 = new TCPDevice();

    private final ChannelHandler handler = new SimpleChannelHandler() {

        private int int0;
        private int int1;

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception
        {
            int0++;
            int1++;

            super.messageReceived(ctx, e);
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception
        {
            assertThat(int0, equalTo(int1));
            super.channelConnected(ctx, e);
        }
    };

    private Thread sendingThread;

    //--------------------------------------------------------------------------------------------//

    // Setup

    @Rule public final LoggingRule loggingRule = new LoggingRule(l);

    @Before
    public void setup()
    {
        setupDevice(device0);
        setupDevice(device1);
    }

    private void setupDevice(final TCPDevice device)
    {
        l.info("starting setup for {}", device.did);

        device.serverBootstrap = new ServerBootstrap(ChannelFactories.newServerChannelFactory());
        device.serverBootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
                    throws Exception
            {
                ChannelPipeline pipeline = new DefaultChannelPipeline();
                pipeline.addLast("acceptor", new SimpleChannelHandler() {

                    @Override
                    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
                            throws Exception
                    {
                        device.acceptedChannel.set(e.getChannel());
                        super.channelConnected(ctx, e);
                    }
                });
                pipeline.addLast("racer", handler);
                return pipeline;
            }
        });

        device.clientBootstrap = new ClientBootstrap(ChannelFactories.newClientChannelFactory());
        device.clientBootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
                    throws Exception
            {
                ChannelPipeline pipeline = new DefaultChannelPipeline();
                pipeline.addLast("placeholder", new SimpleChannelHandler() {

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                            throws Exception
                    {
                        l.warn("caught ", e);
                    }
                });
                return pipeline;
            }
        });

        l.info("completed setup");
    }

    @After
    public void teardown()
            throws Exception
    {
        teardownDevice(device0);
        teardownDevice(device1);

        sendingThread.interrupt();
        sendingThread.join();
    }

    private void teardownDevice(TCPDevice device)
    {
        device.serverBootstrap.releaseExternalResources();
        device.clientBootstrap.releaseExternalResources();
    }

    //--------------------------------------------------------------------------------------------//

    // Test that attempts to trigger the race condition

    @Test
    public void shouldNotCrash()
            throws Exception
    {
        Channel device1ServerChannel = device1.serverBootstrap.bind(new InetSocketAddress(0)); // wait until the recipient has bound
        SocketAddress device1ListenAddress = device1ServerChannel.getLocalAddress();

        ChannelFuture device0ConnectFuture = device0.clientBootstrap.connect(device1ListenAddress);
        device0ConnectFuture.awaitUninterruptibly(); // wait until we've connected to device0

        final Channel sendingChannel = device0ConnectFuture.getChannel();
        sendingThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while(sendingChannel.isOpen()) {
                    sendingChannel.write(ChannelBuffers.wrappedBuffer(new byte[]{0}));
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });
        sendingThread.start();

        Thread.sleep(WAIT_UNTIL_CLOSE_INTERVAL);

        l.info("closing");
        Channel acceptedChannel = device1.acceptedChannel.get();
        acceptedChannel.close().awaitUninterruptibly();
    }
}
