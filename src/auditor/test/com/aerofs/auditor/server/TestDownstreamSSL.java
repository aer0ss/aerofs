/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.auditor.server;

import com.aerofs.auditor.server.Downstream.IAuditChannel;
import com.aerofs.base.BaseParam.Audit;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.SimpleSslEngineFactory;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.util.CharsetUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 */
public class TestDownstreamSSL extends AbstractTest
{
    private SSLContext _sslContext;
    private Channel _serverChannel;
    private LineServerHandler _handler;
    private SimpleSslEngineFactory _sslEngineFactory;

    @Before
    public void startListener()
            throws Exception
    {
        ReconnectingClientHandler.init();
        ReconnectingClientHandler.resetDelay();

        _handler = new LineServerHandler();

        ChannelFactory factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool());

        ServerBootstrap bootstrap = new ServerBootstrap(factory);
        _sslEngineFactory = new SimpleSslEngineFactory();
        _sslContext = _sslEngineFactory.getSSLContext();


        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
            {
                SSLEngine sslEngine = _sslContext.createSSLEngine();
                sslEngine.setUseClientMode(false);
                return Channels.pipeline(
                        new SslHandler(sslEngine),
                        new DelimiterBasedFrameDecoder(1024, false, Delimiters.lineDelimiter()),
                        new StringDecoder(CharsetUtil.UTF_8), _handler);
            }
        });

        bootstrap.setOption("child.keepAlive", true);

        _serverChannel = bootstrap.bind(new InetSocketAddress(0));
        Audit.CHANNEL_HOST = "localhost";
        Audit.AUDIT_ENABLED = true;
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
    public void shouldSendSslData()
            throws Exception
    {
        String testValue = "{\"This is a thing right\":\"Yep\"}";
        Audit.CHANNEL_SSL = true;
        Audit.CHANNEL_CERT = _sslEngineFactory.getCertificate();

        IAuditChannel iAuditChannel = Downstream.create();

        waitForConnectState(iAuditChannel, true);

        iAuditChannel.doSend(testValue);
        _handler.waitForMessage(testValue);
    }

    @Test
    public void shouldFailWithBadCert()
            throws Exception
    {
        String testValue = "{\"This is a thing right\":\"Yep\"}";
        Audit.CHANNEL_SSL = true;
        Audit.CHANNEL_CERT = "";

        IAuditChannel iAuditChannel = Downstream.create();
        iAuditChannel.doSend(testValue);

        ThreadUtil.sleepUninterruptable(1000);
        Assert.assertEquals(_handler.recd.size(), 0);
    }

    // this test works but a physical delay is the only easy way to establish that the SSL
    // connection was refused.
    @Test
    public void nonSslShouldFail() throws Exception
    {
        Downstream.create().doSend("hi mom");

        ThreadUtil.sleepUninterruptable(3000);
        Assert.assertEquals(_handler.recd.size(), 0);
    }

    private void waitForConnectState(IAuditChannel channel, boolean expected)
    {
        int attempts = 0;
        while (attempts < LineServerHandler.MAX_ATTEMPTS) {
            if (channel.isConnected() == expected) return;
            attempts++;
            ThreadUtil.sleepUninterruptable(LineServerHandler.DELAY);
        }
        Assert.fail("Still " + (expected ? "not" : "") + " connected after " +
                (LineServerHandler.DELAY * attempts) + "uS.");
    }
}
