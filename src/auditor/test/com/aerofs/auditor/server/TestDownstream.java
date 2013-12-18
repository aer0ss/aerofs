/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.base.BaseParam.Audit;
import com.aerofs.base.C;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.log.LogUtil;
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
import org.junit.BeforeClass;
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
        Audit.CHANNEL_HOST = "localhost";
        Audit.AUDIT_ENABLED = true;
        _handler = new LineServerHandler();

        ChannelFactory factory = new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool());

        ServerBootstrap bootstrap = new ServerBootstrap(factory);

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline()
            {
                return Channels.pipeline(
                        new DelimiterBasedFrameDecoder(1024, false, Delimiters.lineDelimiter()),
                        new StringDecoder(CharsetUtil.UTF_8),
                        _handler);
            }
        });

        bootstrap.setOption("child.tcpNoDelay", true);
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
        Audit.CHANNEL_HOST = null;
        Audit.CHANNEL_PORT = 0;
    }

    @Test
    public void testStreamFraming() throws Exception
    {
        String testValue = "{\"This is a thing right\":\"Yep\"}";
        Downstream.IAuditChannel iAuditChannel = Downstream.create();
        iAuditChannel.doSend(testValue);
        _handler.waitForMessages(1);

        for (String msg : _handler.recd) {
            Assert.assertEquals(testValue + "\n", msg);
        }
    }

    @Test
    public void testReconnect() throws Exception
    {
        Downstream.IAuditChannel chan = Downstream.create();
        // prime the pump; make sure channel is ok:
        chan.doSend(_testValue);
        _handler.waitForMessages(1);
        Assert.assertEquals(_testValue + '\n', _handler.recd.remove());

        // shut down channel from server side
        chan.doSend("bye");
        _handler.waitForMessages(1);
        Assert.assertEquals("bye\n", _handler.recd.remove());

        // give it a few seconds to detect the failure & reconnect
        chan.doSend(_testValue);

        // because of how we tore down the server, sends will fail for a while before we
        // get the channel shutdown notification & reconnect
        for (int i=0; i<30; i++) {
            Thread.sleep(C.SEC / 10);
            try {
                chan.doSend(_testValue);
                break;
            } catch (ExExternalServiceUnavailable expected) { }
        }

        // check that subsequent sends reconnect automatically:
        _handler.waitForMessages(1);
        Assert.assertEquals(_testValue + '\n', _handler.recd.remove());
    }

    @Test
    public void testCannotConnect() throws Exception
    {
        Downstream.IAuditChannel chan = Downstream.create();
        stopListener();

        try {
            chan.doSend(_testValue);
            Assert.fail("send should fail");
        } catch (ExExternalServiceUnavailable expected) { }
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
         * Block until "expected" messages have arrived, or 2.5 seconds, whichever is sooner.
         */
        public void waitForMessages(int expected)
        {
            int attempts = 0;
            while ((attempts < 25) && (recd.size() < expected)) {
                attempts++;
                ThreadUtil.sleepUninterruptable(C.SEC / 10);
            }
            Assert.assertEquals(expected, recd.size());
        }
    }

    private Channel             _serverChannel;
    private LineServerHandler   _handler;
    private final String        _testValue = "{\"This is a thing amirite\":\"Yep\"}";
}
