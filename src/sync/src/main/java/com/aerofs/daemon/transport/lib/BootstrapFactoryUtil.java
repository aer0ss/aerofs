/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.C;
import com.aerofs.base.net.CoreProtocolHandlers.RecvCoreProtocolVersionHandler;
import com.aerofs.base.net.CoreProtocolHandlers.SendCoreProtocolVersionHandler;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.transport.lib.handlers.ConnectTimeoutHandler;
import com.aerofs.daemon.transport.lib.handlers.HeartbeatHandler;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.lib.LibParam;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.util.Timer;

import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkState;

public abstract class BootstrapFactoryUtil
{
    private BootstrapFactoryUtil() { } // private to prevent instantiation

    /**
     * This class encapsulates the framing parameters that we use
     */
    public static class FrameParams
    {
        public static final int LENGTH_FIELD_SIZE = 2; // bytes
        public static final int MAX_MESSAGE_SIZE = DaemonParam.MAX_TRANSPORT_MESSAGE_SIZE;
        public static final byte[] CORE_PROTOCOL_VERSION_BYTES = ByteBuffer.allocate(C.INTEGER_SIZE).putInt(LibParam.CORE_PROTOCOL_VERSION).array();
        public static final int HEADER_SIZE = LENGTH_FIELD_SIZE + CORE_PROTOCOL_VERSION_BYTES.length;

        static {
            // Check that the maximum message size is smaller than the maximum number that can be
            // represented using LENGTH_FIELD_SIZE bytes
            checkState(FrameParams.MAX_MESSAGE_SIZE < Math.pow(256, FrameParams.LENGTH_FIELD_SIZE));
        }
    }

    public static LengthFieldPrepender newLengthFieldPrepender()
    {
        return new LengthFieldPrepender(FrameParams.LENGTH_FIELD_SIZE);
    }

    public static LengthFieldBasedFrameDecoder newFrameDecoder()
    {
        return new LengthFieldBasedFrameDecoder(
                FrameParams.MAX_MESSAGE_SIZE,
                0,
                FrameParams.LENGTH_FIELD_SIZE,
                0,
                FrameParams.LENGTH_FIELD_SIZE);
    }

    public static RecvCoreProtocolVersionHandler newCoreProtocolVersionReader()
    {
        return new RecvCoreProtocolVersionHandler(FrameParams.CORE_PROTOCOL_VERSION_BYTES);
    }

    public static SendCoreProtocolVersionHandler newCoreProtocolVersionWriter()
    {
        return new SendCoreProtocolVersionHandler(FrameParams.CORE_PROTOCOL_VERSION_BYTES);
    }

    public static IOStatsHandler newStatsHandler(TransportStats stats)
    {
        return new IOStatsHandler(stats);
    }

    public static HeartbeatHandler newHeartbeatHandler(long heartbeatInterval, int maxFailedHeartbeats,
            Timer timer, IRoundTripTimes roundTripTimes)
    {
        return new HeartbeatHandler(heartbeatInterval, maxFailedHeartbeats, timer, roundTripTimes);
    }

    public static ConnectTimeoutHandler newConnectTimeoutHandler(long channelConnectTimeout, Timer timer)
    {
        return new ConnectTimeoutHandler(channelConnectTimeout, timer);
    }

    public static void setConnectTimeout(ClientBootstrap bootstrap, long channelConnectTimeout)
    {
        bootstrap.setOption("connectTimeoutMillis", channelConnectTimeout);
    }
}
