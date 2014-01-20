/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.daemon.transport.LoggingRule;
import com.aerofs.daemon.transport.jingle.SignalThread.IIncomingTunnelListener;
import com.aerofs.daemon.transport.jingle.SignalThread.ISignalThreadListener;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.j.Jid;
import com.aerofs.j.SWIGTYPE_p_cricket__Session;
import com.aerofs.j.TunnelSessionClient;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public final class TestJingleClientChannelThreadSafety
{
    static
    {
        System.loadLibrary("aerofsd");
        System.loadLibrary("aerofsj");
    }

    private static final Logger l = LoggerFactory.getLogger(TestJingleClientChannelThreadSafety.class);

    private static final long WAIT_UNTIL_CLOSE_TIME = 60;
    private static final String ARROWFS_ORG = "arrowfs.org";
    private static final String TRANSPORT_ID = "t";

    //--------------------------------------------------------------------------------------------//

    // Types and Declarations

    private class JingleDevice
    {
        private final DID did = DID.generate();
        private JingleChannelWorker channelWorker;
        private SignalThread signalThread;
        private ClientBootstrap clientBootstrap;
        private ServerBootstrap serverBootstrap;
    }

    private final JingleDevice jingleDevice = new JingleDevice();

    private class AllTheSignalThreadListeners implements IUnicastListener, IIncomingTunnelListener, ISignalThreadListener
    {

        @Override
        public void onIncomingTunnel(TunnelSessionClient client, Jid jid, SWIGTYPE_p_cricket__Session session) { }

        @Override
        public void onSignalThreadReady() { }

        @Override
        public void onSignalThreadClosing() { }

        @Override
        public void onUnicastReady() { }

        @Override
        public void onUnicastUnavailable() { }

        @Override
        public void onDeviceConnected(DID did) { }

        @Override
        public void onDeviceDisconnected(DID did) { }
    }

    private final AllTheSignalThreadListeners listener = new AllTheSignalThreadListeners();
    private final AtomicBoolean running = new AtomicBoolean(true);

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
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception
        {
            assertThat(int0, equalTo(int1));
            super.channelClosed(ctx, e);
        }
    };

    private Thread sendingThread;
    private Thread closingThread;

    //--------------------------------------------------------------------------------------------//

    // Setup

    @Rule public final LoggingRule loggingRule = new LoggingRule(l);

    @Before
    public void setup()
    {
        l.info("starting setup for {}", jingleDevice.did);

        jingleDevice.channelWorker = new JingleChannelWorker(jingleDevice.did.toString());
        jingleDevice.signalThread = new SignalThread(
                TRANSPORT_ID,
                new Jid(JabberID.did2FormAJid(jingleDevice.did, ARROWFS_ORG, TRANSPORT_ID)),
                "NONE",
                new InetSocketAddress("localhost", 3482),
                new InetSocketAddress("localhost", 5222),
                "someroot",
                true);

        jingleDevice.signalThread.addSignalThreadListener(listener);
        jingleDevice.signalThread.setIncomingTunnelListener(listener);
        jingleDevice.signalThread.setUnicastListener(listener);

        jingleDevice.clientBootstrap = new ClientBootstrap(new JingleClientChannelFactory(jingleDevice.signalThread, jingleDevice.channelWorker));
        jingleDevice.clientBootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
                    throws Exception
            {
                ChannelPipeline pipeline = new DefaultChannelPipeline();
                pipeline.addLast("racer", handler);
                return pipeline;
            }
        });

        jingleDevice.serverBootstrap = new ServerBootstrap(new JingleServerChannelFactory(jingleDevice.signalThread, jingleDevice.channelWorker));

        jingleDevice.channelWorker.start();
        jingleDevice.signalThread.start();

        l.info("completed setup");
    }

    @After
    public void teardown()
            throws Exception
    {
        jingleDevice.signalThread.shutdown();
        jingleDevice.serverBootstrap.releaseExternalResources();
        jingleDevice.clientBootstrap.releaseExternalResources();

        running.set(false);
        sendingThread.interrupt();
        sendingThread.join();

        closingThread.interrupt();
        closingThread.join();

        jingleDevice.channelWorker.stop();
    }

    //--------------------------------------------------------------------------------------------//

    // Test that attempts to trigger the race condition

    @Ignore
    @Test
    public void shouldNotCrash()
            throws Exception
    {
        final DID remotedid = DID.generate();

        final JingleClientChannel channel =
                (JingleClientChannel) jingleDevice.clientBootstrap
                        .connect(new JingleAddress(remotedid, new Jid(JabberID.did2BareJid(remotedid, ARROWFS_ORG))))
                        .getChannel();

        sendingThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while(running.get()) {
                    try {
                        channel.onIncomingMessage(remotedid, new byte[] {0});
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        l.warn("interrupted while simulating incoming messages");
                        break;
                    }
                }
            }
        });
        sendingThread.start();

        Thread.sleep(WAIT_UNTIL_CLOSE_TIME);

        closingThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                channel.close();
            }
        });
        closingThread.start();

        channel.getCloseFuture().awaitUninterruptibly();
    }
}
