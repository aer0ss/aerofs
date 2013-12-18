/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.base.BaseParam.Audit;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.lib.log.LogUtil;
import com.google.common.base.Strings;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static org.jboss.netty.buffer.ChannelBuffers.*;

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
        void doSend(String message) throws ExExternalServiceUnavailable;
    }

    /**
     * A simple write-only service, newline-delimited docs. Appropriate for single-line JSON
     * or similar.
     */
    static class NewLineDelimitedStream implements IAuditChannel, ChannelFutureListener
    {
        /**
         * A purely synchronous send interface. This sends the given message, with a
         * newline delimiter added, to the remote system. The call blocks for the write()
         * to complete. If the write does not complete, ExExternalServiceUnavailable is thrown.
         */
        @Override
        public void doSend(String message) throws ExExternalServiceUnavailable
        {
            // TODO: configure maximum write timeout
            ChannelFuture writeFuture = Channels.write(
                    getChannelOrThrow(),
                    wrappedBuffer(message.getBytes(), DELIM))
                        .awaitUninterruptibly();

            if (!writeFuture.isSuccess()) {
                l.warn("write failed", LogUtil.suppress(writeFuture.getCause()));
                throw new ExExternalServiceUnavailable("write failed");
            }
        }

        /**
         *  ChannelFutureListener op called when channel close is detected.
         */
        @Override
        public void operationComplete(ChannelFuture channelFuture) throws Exception
        {
            synchronized (this) {
                l.info("Received channel closed notification");
                _channel = null;
            }
        }

        // safely handle re-connect if the channel was torn down
        private Channel getChannelOrThrow() throws ExExternalServiceUnavailable
        {
            Channel retval = _channel;
            // careful here, we can do this part un-synchronized when the channel is non-null...
            try {
                return (retval == null) ? connectOrThrow() : retval;
            } catch (Exception e) {
                l.warn("Error connecting downstream", e);
                throw new ExExternalServiceUnavailable("Cannot connect to downstream svc");
            }
        }

        /**
         * safely connect if _channel is invalid. return immediately if _channel is okay.
         */
        private synchronized Channel connectOrThrow() throws ExExternalServiceUnavailable
        {
            if (_channel != null && _channel.isOpen()) { return _channel; }

            ClientBootstrap     bootstrap;
            Channel             channel;
            ChannelFactory      factory;

            factory = new NioClientSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool());

            bootstrap = new ClientBootstrap(factory);
            bootstrap.setOption("keepAlive", true);
            bootstrap.setOption("child.tcpNoDelay", true);
            bootstrap.setOption("child.keepAlive", true);
            bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
                @Override
                public ChannelPipeline getPipeline()
                {
                    return Channels.pipeline(new FinalUpstreamEventHandler());
                }
            });

            InetSocketAddress addr = new InetSocketAddress(Audit.CHANNEL_HOST, Audit.CHANNEL_PORT);
            l.info("Creating channel for {}", addr.toString());

            channel = bootstrap.connect(addr).awaitUninterruptibly().getChannel();
            channel.setReadable(false);
            channel.getCloseFuture().addListener(this);

            l.info("Connected json channel");

            // do this assignment last so we won't update _channel if any connect step fails
            return _channel = channel;
        }

        private static final byte[] DELIM = new byte[] { '\n' };
        private Channel _channel;
    }

    /** A no-op stream for systems with no downstream components configured */
    static private class DummyStream implements IAuditChannel
    {
        @Override
        public void doSend(String message) { l.warn("no downstream: {}", message);}
    }
}
