/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.netty;

import com.aerofs.base.C;
import com.aerofs.base.net.MagicHeader.ReadMagicHeaderHandler;
import com.aerofs.base.net.MagicHeader.WriteMagicHeaderHandler;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.netty.handlers.DiagnosticsHandler;
import com.aerofs.daemon.transport.netty.handlers.IOStatsHandler;
import com.aerofs.lib.LibParam;
import com.aerofs.rocklog.RockLog;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.util.Timer;

import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkState;

public class BootstrapFactoryUtil
{
    /**
     * This class encapsulates the framing parameters that we use
     */
    public static class FrameParams
    {
        public static final int LENGTH_FIELD_SIZE = 2; // bytes
        public static final int MAX_MESSAGE_SIZE = DaemonParam.MAX_TRANSPORT_MESSAGE_SIZE;
        public static final byte[] MAGIC_BYTES = ByteBuffer.allocate(C.INTEGER_SIZE).putInt(LibParam.CORE_MAGIC).array();
        public static final int HEADER_SIZE = LENGTH_FIELD_SIZE + MAGIC_BYTES.length;

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
        return new LengthFieldBasedFrameDecoder(FrameParams.MAX_MESSAGE_SIZE, 0,
                FrameParams.LENGTH_FIELD_SIZE, 0, FrameParams.LENGTH_FIELD_SIZE);
    }

    public static ReadMagicHeaderHandler newMagicReader()
    {
        return new ReadMagicHeaderHandler(FrameParams.MAGIC_BYTES);
    }

    public static WriteMagicHeaderHandler newMagicWriter()
    {
        return new WriteMagicHeaderHandler(FrameParams.MAGIC_BYTES);
    }

    public static IOStatsHandler newStatsHandler(TransportStats stats)
    {
        return new IOStatsHandler(stats);
    }

    public static DiagnosticsHandler newDiagnosticsHandler(String transportId, RockLog rockLog, Timer timer)
    {
        return new DiagnosticsHandler(transportId, rockLog, timer);
    }
}
