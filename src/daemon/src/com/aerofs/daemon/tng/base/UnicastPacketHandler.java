/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.IUnicastListener;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;

import static com.aerofs.daemon.tng.base.Handlers.sendOutgoingMessage_;
import static com.aerofs.proto.Transport.PBTPHeader;

final public class UnicastPacketHandler extends SimplePipelineEventHandler
{
    private final IUnicastListener _unicastListener;

    UnicastPacketHandler(IUnicastListener unicastListener)
    {
        _unicastListener = unicastListener;
    }

    @Override
    protected void onIncomingMessageEvent_(IPipelineContext ctx, MessageEvent messageEvent)
            throws Exception
    {
        if (!(messageEvent.getMessage_() instanceof IncomingAeroFSPacket)) {
            super.onIncomingMessageEvent_(ctx, messageEvent);
            return;
        }

        IncomingAeroFSPacket in = (IncomingAeroFSPacket) messageEvent.getMessage_();
        if (in.getType_() != PBTPHeader.Type.DATAGRAM) {
            super.onIncomingMessageEvent_(ctx, messageEvent);
            return;
        }

        _unicastListener.onUnicastDatagramReceived(ctx.getDID_(), in.getPayload_(), in.getWirelen_());

        messageEvent.getCompletionFuture_().set(null);
    }

    @Override
    protected void onOutgoingMessageEvent_(IPipelineContext ctx, MessageEvent messageEvent)
            throws Exception
    {
        if (!(messageEvent.getMessage_() instanceof OutgoingUnicastPacket)) {
            super.onOutgoingMessageEvent_(ctx, messageEvent);
            return;
        }

        OutgoingUnicastPacket out = (OutgoingUnicastPacket) messageEvent.getMessage_();

        PBTPHeader hdr = PBTPHeader.newBuilder()
                .setType(PBTPHeader.Type.DATAGRAM)
                .build();

        OutgoingAeroFSPacket pkt = new OutgoingAeroFSPacket(hdr, out.getPayload_());
        sendOutgoingMessage_(ctx, messageEvent, pkt);
    }
}