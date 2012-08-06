/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pulse;

import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.base.ConnectionEvent;
import com.aerofs.daemon.tng.base.IncomingAeroFSPacket;
import com.aerofs.daemon.tng.base.MessageEvent;
import com.aerofs.daemon.tng.base.OutgoingAeroFSPacket;
import com.aerofs.daemon.tng.base.SimplePipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pulse.ex.ExInvalidPulseMessage;
import com.aerofs.daemon.tng.base.pulse.ex.ExInvalidPulseReply;
import com.aerofs.daemon.tng.base.pulse.ex.ExPulsingFailed;
import com.aerofs.lib.async.FutureUtil;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.proto.Transport;
import com.aerofs.proto.Transport.PBTPHeader;
import com.google.common.util.concurrent.FutureCallback;

public class PulseInitiatorHandler extends SimplePipelineEventHandler
{
    private final ISingleThreadedPrioritizedExecutor _executor;
    private final PulseState _pulseState;

    public PulseInitiatorHandler(ISingleThreadedPrioritizedExecutor executor, PulseState pulseState)
    {
        _executor = executor;
        _pulseState = pulseState;
    }

    @Override
    protected void onConnectEvent_(IPipelineContext ctx, final ConnectionEvent connectEvent)
            throws Exception
    {
        // This is a connection event. We haven't started any timeouts yet
        // so this is the best place to hook into the close future of the
        // connection. When the connection disconnects, we bump the
        // PulseState's pulse ID so that ongoing timeouts that trigger
        // after the connection closes won't do anything
        ctx.getConnection_().getCloseFuture_().addListener(new Runnable()
        {
            @Override
            public void run()
            {
                if (_pulseState.isOngoing()) {
                    _pulseState.pulseTimedOut_();
                }
            }
        }, _executor);

        if (_pulseState.isOngoing()) {
            // When the pulse is still ongoing from a previous connection,
            // send out a PulseCall message when this new connection
            // connects

            FutureUtil.addCallback(connectEvent.getCompletionFuture_(), new FutureCallback<Void>()
            {
                @Override
                public void onSuccess(Void aVoid)
                {
                    // Send a pulse call
                    sendPulseMessage(connectEvent.getConnection_());
                }

                @Override
                public void onFailure(Throwable throwable)
                {
                    // Do nothing on failure, the connection will tear down
                    // this handler anyways
                }

            }, _executor);
        }

        super.onConnectEvent_(ctx, connectEvent);
    }

    @Override
    protected void onOutgoingMessageEvent_(IPipelineContext ctx, MessageEvent messageEvent)
            throws Exception
    {
        if (!(messageEvent.getMessage_() instanceof StartPulseMessage)) {
            super.onOutgoingMessageEvent_(ctx, messageEvent);
            return;
        }

        // Received a request to start pulsing a peer
        StartPulseMessage startPulseMessage = (StartPulseMessage) messageEvent.getMessage_();

        UncancellableFuture<Void> pulseFuture = startPulseMessage.getPulseFuture();
        try {
            // Set the pulsing state to ongoing and send out a
            // pulse call on the wire

            if (_pulseState.isOngoing()) {
                throw new IllegalStateException(
                        "Starting pulse while existing pulse request exists");
            }

            _pulseState.pulseStarted_(pulseFuture);
            sendPulseMessage(ctx.getConnection_());

            messageEvent.getCompletionFuture_().set(null);
        } catch (IllegalStateException e) {
            // We are already pulsing, so notify the requestor that
            // multiple parallel pulsing requests are illegal

            pulseFuture.setException(e);
            messageEvent.getCompletionFuture_().setException(e);
        }
    }

    @Override
    protected void onIncomingMessageEvent_(IPipelineContext ctx, MessageEvent messageEvent)
            throws Exception
    {
        // If no pulse is ongoing, or the message type is not a packet, then we don't care
        // about incoming events
        if (!(messageEvent.getMessage_() instanceof IncomingAeroFSPacket) ||
                !_pulseState.isOngoing()) {
            super.onIncomingMessageEvent_(ctx, messageEvent);
            return;
        }

        // The header type must be a Pulse Reply
        IncomingAeroFSPacket packet = (IncomingAeroFSPacket) messageEvent.getMessage_();
        if (packet.getType_() != Transport.PBTPHeader.Type.TRANSPORT_CHECK_PULSE_REPLY) {
            super.onIncomingMessageEvent_(ctx, messageEvent);
            return;
        }

        // From this point on, we don't forward any events up the pipeline
        // because we know this event is for us, but it may be invalid

        // The header must contain a pulse field
        if (!packet.getHeader_().hasCheckPulse()) {
            messageEvent.getCompletionFuture_()
                    .setException(new ExInvalidPulseMessage("PBCheckPulse not set"));
            return;
        }

        // At this point this message is a valid incoming pulse response
        int pulseId = packet.getHeader_().getCheckPulse().getPulseId();
        if (_pulseState.getCurrentPulseId_() == pulseId) {
            // The received pulseId matches our latest pulseId

            _pulseState.pulseCompleted_();
            messageEvent.getCompletionFuture_().set(null);
        } else {
            messageEvent.getCompletionFuture_()
                    .setException(
                            new ExInvalidPulseReply(_pulseState.getCurrentPulseId_(), pulseId));
        }
    }

    private void sendPulseMessage(final IConnection connection)
    {
        PBTPHeader header = PBTPHeader.newBuilder()
                .setType(PBTPHeader.Type.TRANSPORT_CHECK_PULSE_CALL)
                .setCheckPulse(Transport.PBCheckPulse
                        .newBuilder()
                        .setPulseId(_pulseState.getCurrentPulseId_()))
                .build();

        connection.send_(new OutgoingAeroFSPacket(header, null), Prio.LO);

        final int frozenPulseId = _pulseState.getCurrentPulseId_();
        _executor.executeAfterDelay(new Runnable()
        {
            @Override
            public void run()
            {
                if (_pulseState.getCurrentPulseId_() != frozenPulseId || !_pulseState.isOngoing()) {
                    // The pulse id's don't match, or there is no ongoing pulse,
                    // meaning this timeout is already invalidated. It can be
                    // invalidated for several reasons:
                    // 1) The connection disconnected, which bumps up the pulse ID
                    // 2) A reply was received, which bumps up the pulse ID

                    return;
                }

                _pulseState.pulseTimedOut_();

                if (_pulseState.shouldDisconnect_()) {
                    connection.disconnect_(new ExPulsingFailed());
                } else {
                    // If pulsing is still happening, aka, we are not
                    // disconnecting
                    sendPulseMessage(connection);
                }

            }

        }, _pulseState.getTimeoutDelay_());
    }
}
