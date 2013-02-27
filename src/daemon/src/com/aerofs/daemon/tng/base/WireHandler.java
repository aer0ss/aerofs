/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.proto.Transport.PBTPHeader;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static com.aerofs.daemon.tng.base.Handlers.sendIncomingMessage_;
import static com.aerofs.daemon.tng.base.Handlers.sendOutgoingMessage_;
import static com.aerofs.lib.Util.writeDelimited;

final public class WireHandler extends SimplePipelineEventHandler
{
    private static final Logger l = Loggers.getLogger(WireHandler.class);

    @Override
    protected void onIncomingMessageEvent_(IPipelineContext ctx, MessageEvent messageEvent)
            throws Exception
    {
        if (!(messageEvent.getMessage_() instanceof WireData)) {
            super.onIncomingMessageEvent_(ctx, messageEvent);
            return;
        }

        WireData in = (WireData) messageEvent.getMessage_();
        try {
            ByteArrayInputStream is = new ByteArrayInputStream(in.getData_());
            PBTPHeader hdr = PBTPHeader.parseDelimitedFrom(is);
            IncomingAeroFSPacket pkt = new IncomingAeroFSPacket(hdr, is, in.getWirelen_());
            sendIncomingMessage_(ctx, messageEvent, pkt);
        } catch (IOException e) {
            l.warn("fail read pkt err:" + e);
            messageEvent.getCompletionFuture_().setException(e);
        }
    }

    @Override
    protected void onOutgoingMessageEvent_(IPipelineContext ctx, MessageEvent messageEvent)
            throws Exception
    {
        if (!(messageEvent.getMessage_() instanceof OutgoingAeroFSPacket)) {
            super.onOutgoingMessageEvent_(ctx, messageEvent);
            return;
        }

        OutgoingAeroFSPacket out = (OutgoingAeroFSPacket) messageEvent.getMessage_();

        byte[] serializedHeader = writeDelimited(out.getHeader_()).toByteArray();
        byte[][] serialized;

        if (out.getData_() == null) {
            serialized = new byte[][]{serializedHeader, /* empty */};
        } else {
            serialized = new byte[][]{serializedHeader, out.getData_()};
        }

        sendOutgoingMessage_(ctx, messageEvent, serialized);
    }
}
