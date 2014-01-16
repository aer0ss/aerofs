/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.auditor.server.Downstream.IAuditChannel;
import com.aerofs.base.BaseParam.Audit;
import com.aerofs.base.C;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.testlib.AbstractTest;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;

/**
 */
public class TestDownstream extends AbstractTest
{
    @Before
    public void startListener()
    {
        ReconnectingClientHandler.init();
        ReconnectingClientHandler.resetDelay();

        Audit.CHANNEL_HOST = "localhost";
        Audit.AUDIT_ENABLED = true;
        _handler = new LineServerHandler();

        ChannelFactory factory = new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool());

        ServerBootstrap bootstrap = new ServerBootstrap(factory);

        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
            {
                return Channels.pipeline(
                        new DelimiterBasedFrameDecoder(1024, false, Delimiters.lineDelimiter()),
                        new StringDecoder(CharsetUtil.UTF_8), _handler);
            }
        });

        bootstrap.setOption("child.keepAlive", true);

        _serverChannel = bootstrap.bind(new InetSocketAddress(0));
        Audit.CHANNEL_PORT = ((InetSocketAddress)_serverChannel.getLocalAddress()).getPort();
    }

    @After
    public void stopListener()
    {
        if (_serverChannel != null) {
            _serverChannel.close().awaitUninterruptibly();
            _serverChannel = null;
        }
    }

    @After
    public void resetConfigs()
    {
        ReconnectingClientHandler.quiesce();
        Audit.CHANNEL_HOST = null;
        Audit.CHANNEL_PORT = 0;
    }

    @Test
    public void streamShouldFrameMessages() throws Exception
    {
        String testValue = "{\"This is a thing right\":\"Yep\"}";
        Downstream.IAuditChannel iAuditChannel = Downstream.create();

        waitForConnectState(iAuditChannel, true);

        iAuditChannel.doSend(testValue);
        _handler.waitForMessage(testValue);
    }

    @Test
    public void streamShouldReconnect() throws Exception
    {
        Downstream.IAuditChannel chan = Downstream.create();

        // prime the pump; make sure channel is ok:
        waitForConnectState(chan, true);
        chan.doSend(_testValue);
        _handler.waitForMessage(_testValue);

        // shut down channel from server side, and block for client disconnect:
        chan.doSend("bye");
        _handler.waitForMessage("bye");

        // the initial reconnect is not synchronous, it's on a 1-ms delay.
        // therefore let's give this guy a moment before we try a send.
        ThreadUtil.sleepUninterruptable(100);

        // check that subsequent sends are queued behind immediate reconnect automatically:
        waitForConnectState(chan, true);
        chan.doSend(_testValue + " 2 ");
        _handler.waitForMessage(_testValue + " 2 ");
    }

    @Ignore // FIXME jP owes getting this working
    @Test
    public void streamShouldDetectDisconnect() throws Exception
    {
        Downstream.IAuditChannel chan = Downstream.create();

        // prime the pump; make sure channel starts off ok:
        chan.doSend(_testValue);
        _handler.waitForMessage(_testValue);

        // shut down channel from server side:
        stopListener();
        chan.doSend("bye");
        _handler.waitForMessage("bye");

        // give it a few tries to detect the failure ... buffers eh?
        int attempts = 0;
        while (attempts < 50) {
            chan.doSend("Are you gone yet?");
            ThreadUtil.sleepUninterruptable(C.SEC / 10);
            if (!chan.isConnected()) return;
            attempts++;
        }

        Assert.fail("Timed out waiting for channel to detect disconnect");
    }

    @Test
    public void streamShouldRetryConnect() throws Exception
    {
        stopListener();
        Downstream.IAuditChannel chan = Downstream.create();
        ThreadUtil.sleepUninterruptable(1 * C.SEC);
        Assert.assertTrue(ReconnectingClientHandler.getNextDelay() > 100L);
    }

    @Test
    public void streamShouldHaveRetryLimit() throws Exception
    {
        ReconnectingClientHandler.resetDelay();
        do {
        } while (ReconnectingClientHandler.getNextDelay() != ReconnectingClientHandler.MAX_DELAY_MS);
        Assert.assertEquals(ReconnectingClientHandler.getNextDelay(), ReconnectingClientHandler.MAX_DELAY_MS);
    }

    @Test
    public void streamShouldDetectConnectFailure() throws Exception
    {
        stopListener();
        Downstream.IAuditChannel chan = Downstream.create();

        Assert.assertTrue(!chan.isConnected());
        chan.doSend("hi"); // no exception from this guy, async...
    }

    // A simple server handler that records line-delimited messages received for later checking
    static class LineServerHandler extends SimpleChannelHandler
    {
        Queue<String> recd  = new ConcurrentLinkedQueue<String>();

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
        {
            String msg = (String)e.getMessage();
            l.info("LSH R: {}", msg);
            if (msg.equals("bye\n")) {
                e.getChannel().disconnect();
            }
            recd.add(msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        {
            e.getCause().printStackTrace();
            Assert.fail();
        }

        /**
         * Block until the expected message has arrived, or 5 seconds, whichever is sooner.
         */
        public void waitForMessage(String expected)
        {
            int attempts = 0;
            while ((attempts < MAX_ATTEMPTS) && (recd.size() < 1)) {
                attempts++;
                ThreadUtil.sleepUninterruptable(DELAY);
            }
            Assert.assertEquals(1, recd.size());
            Assert.assertEquals(expected + '\n', recd.remove());
        }
    }

    private void waitForConnectState(IAuditChannel channel, boolean expected)
    {
        int attempts=0;
        while (attempts < MAX_ATTEMPTS) {
            if (channel.isConnected() == expected) return;
            attempts++;
            ThreadUtil.sleepUninterruptable(DELAY);
        }
        Assert.fail("Still " + (expected? "not" : "")
                + " connected after " + (DELAY * attempts) + "uS.");
    }

    private Channel             _serverChannel;
    private LineServerHandler   _handler;
    private final String        _testValue = "{\"This is a thing amirite\":\"Yep\"}";
    private static final long   DELAY = C.SEC / 10;
    private static final long   MAX_ATTEMPTS = 450;
}
