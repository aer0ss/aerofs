/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.base.BaseParam.Audit;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.TimerUtil;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.base.ssl.StringBasedCertificateProvider;
import com.google.common.base.Strings;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;

/**
 * Class to manage downstream connections. Currently only a simple JSON stream is supported.
 */
public class Downstream
{
    private static Logger l = LoggerFactory.getLogger(Downstream.class);

    /**
     * Create an audit channel for the current downstream channel configuration.
     */
    public static IAuditChannel create()
    {
        return Strings.isNullOrEmpty(Audit.CHANNEL_HOST)
                ? new DummyStream() : new NewLineDelimitedStream();
    }

    /**
     * Interface for audit channels.
     */
    public interface IAuditChannel
    {
        /**
         * Send the given message, or throw an exception if it cannot be delivered.
         */
        ChannelFuture doSend(String message);

        /**
         * Best-effort state of the underlying channel implementation.
         */
        boolean isConnected();
    }

    /**
     * A simple write-only service, newline-delimited docs. Appropriate for single-line JSON
     * or similar.
     *
     * TODO: netty doesn't seem to provide queuing of undeliverable messages, so I guess
     * we need one...
     */
    static class NewLineDelimitedStream implements IAuditChannel, IConnectNotifier
    {
        NewLineDelimitedStream()
        {
            final Timer timer = TimerUtil.getGlobalTimer();
            final ClientBootstrap   bootstrap;
            ChannelFactory          factory;
            InetSocketAddress       addr;

            addr = new InetSocketAddress(Audit.CHANNEL_HOST, Audit.CHANNEL_PORT);

            factory = new NioClientSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool());

            bootstrap = new ClientBootstrap(factory);
            bootstrap.setOption("keepAlive", true);
            bootstrap.setOption("tcpNoDelay", true);
            bootstrap.setOption("remoteAddress", addr);
            bootstrap.setPipelineFactory(() -> {
                ChannelPipeline pipeline = Channels.pipeline();

                if (Audit.CHANNEL_SSL) {
                    SslHandler ssl = new SslHandler(getSSLEngineFactory().getSSLEngine());
                    pipeline.addLast("ssl", ssl);
                    pipeline.addLast("ssl-handshake", new SslHandshake(ssl));
                }
                pipeline.addLast("clientHandler",
                        new ReconnectingClientHandler(timer, bootstrap, NewLineDelimitedStream.this));

                return pipeline;
            });

            l.info("Creating channel for {}", addr.toString());

            // bootstrap the initial client connection...
            _channel = bootstrap.connect().getChannel();
        }

        /**
         * A simple send interface. This sends the given message, with a
         * newline delimiter added, to the remote system. The call is unable to block for
         * write completion. However, if the write fails or is cancelled, we will respond by
         * tearing down the channel. A subsequent send will cause reconnect.
         */
        @Override
        public ChannelFuture doSend(String message)
        {
            return Channels.write(_channel, wrappedBuffer(BaseUtil.string2utf(message), DELIM));
        }

        @Override
        public boolean isConnected()
        {
            return _channel.isConnected();
        }

        @Override
        public void channelConnected(Channel c)
        {
            l.info("channel-connected notification {}", c);
            _channel = c;
        }

        private static final byte[] DELIM = new byte[] { '\n' };
        private Channel _channel;
    }

    /**
     * A no-op stream for systems with no downstream components configured. This class
     * always returns a successful future.
     */
    static private class DummyStream implements IAuditChannel
    {
        @Override
        public ChannelFuture doSend(String message)
        {
            l.warn("no downstream: {}", message);
            return _future;
        }
        @Override
        public boolean isConnected() { return true; }

        DummyStream() {
            _future = new DefaultChannelFuture(null, false);
            _future.setSuccess();
        }
        ChannelFuture _future;
    }

    /**
     * Return an SSL engine factory used to create secure contexts. The factory will be configured
     * with an explicit certificate if one is set in Audit.CHANNEL_CERT, otherwise the default
     * trust store is used.
     */
    private static SSLEngineFactory getSSLEngineFactory()
    {
        return new SSLEngineFactory(
                Mode.Client, Platform.Desktop, null,
                (Audit.CHANNEL_CERT.length() > 0) ?
                        new StringBasedCertificateProvider(Audit.CHANNEL_CERT) : null,
                null);
    }
}
