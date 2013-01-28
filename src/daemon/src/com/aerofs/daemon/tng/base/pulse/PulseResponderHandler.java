/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pulse;

import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.tng.base.IncomingAeroFSPacket;
import com.aerofs.daemon.tng.base.MessageEvent;
import com.aerofs.daemon.tng.base.OutgoingAeroFSPacket;
import com.aerofs.daemon.tng.base.pipeline.IIncomingPipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.daemon.tng.base.pulse.ex.ExInvalidPulseMessage;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.proto.Transport.PBTPHeader;

public class PulseResponderHandler implements IIncomingPipelineEventHandler
{
    private static OutgoingAeroFSPacket buildPulseReply(PBTPHeader pulseCall)
    {
        PBTPHeader reply = PBTPHeader.newBuilder()
                .setType(PBTPHeader.Type.TRANSPORT_CHECK_PULSE_REPLY)
                .setCheckPulse(pulseCall.getCheckPulse())
                .build();
        return new OutgoingAeroFSPacket(reply, null);
    }

    @Override
    public void onIncoming_(IPipelineContext ctx, IPipelineEvent<?> event)
            throws Exception
    {
        if (!(event instanceof MessageEvent)) {
            ctx.sendIncomingEvent_(event);
            return;
        }

        MessageEvent messageEvent = (MessageEvent) event;
        if (!(messageEvent.getMessage_() instanceof IncomingAeroFSPacket)) {
            ctx.sendIncomingEvent_(event);
            return;
        }

        IncomingAeroFSPacket packet = (IncomingAeroFSPacket) messageEvent.getMessage_();
        if (packet.getType_() != PBTPHeader.Type.TRANSPORT_CHECK_PULSE_CALL) {
            ctx.sendIncomingEvent_(event);
            return;
        }

        if (!packet.getHeader_().hasCheckPulse()) {
            messageEvent.getCompletionFuture_()
                    .setException(new ExInvalidPulseMessage("PBCheckPulse was not set"));
            return;
        }

        // Complete the event's future, as it is a valid Pulse Call
        messageEvent.getCompletionFuture_().set(null);

        // Generate a Pulse Reply message
        MessageEvent response = new MessageEvent(ctx.getConnection_(),
                UncancellableFuture.<Void>create(), buildPulseReply(packet.getHeader_()), Prio.LO);

        // Send the message out the pipeline
        ctx.sendOutgoingEvent_(response);
    }
}
