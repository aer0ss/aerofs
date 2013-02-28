/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pulse;

import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.base.ConnectionEvent;
import com.aerofs.daemon.tng.base.IncomingAeroFSPacket;
import com.aerofs.daemon.tng.base.MessageEvent;
import com.aerofs.daemon.tng.base.OutgoingAeroFSPacket;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pulse.ex.ExInvalidPulseReply;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.proto.Transport;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;

import static com.aerofs.testlib.FutureAssert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.*;

public class TestPulseInitiatorHandler extends AbstractTest
{
    PulseInitiatorHandler handler;

    @Mock ISingleThreadedPrioritizedExecutor executor;
    @Mock PulseState pulseState;
    @Mock IConnection connection;
    @Mock IPipelineContext pipelineContext;

    @Captor ArgumentCaptor<OutgoingAeroFSPacket> outgoingPacketCaptor;
    @Captor ArgumentCaptor<Prio> prioCaptor;
    @Captor ArgumentCaptor<Runnable> runnableCaptor;
    @Captor ArgumentCaptor<Runnable> runnableCaptor2;
    @Captor ArgumentCaptor<Long> delayCaptor;

    MessageEvent buildStartPulseMessageEvent()
    {
        StartPulseMessage message = new StartPulseMessage();
        return new MessageEvent(connection,
            UncancellableFuture.<Void>create(), message, Prio.LO);
    }

    MessageEvent buildPulseReplyMessageEvent(int pulseId)
    {
        PBTPHeader header = PBTPHeader.newBuilder()
            .setType(PBTPHeader.Type.TRANSPORT_CHECK_PULSE_REPLY)
            .setCheckPulse(Transport.PBCheckPulse.newBuilder()
                .setPulseId(pulseId))
            .build();
        IncomingAeroFSPacket incomingPacket = new IncomingAeroFSPacket(header,
            new ByteArrayInputStream(new byte[0]), 0);
        return new MessageEvent(connection,
            UncancellableFuture.<Void>create(), incomingPacket, Prio.LO);
    }

    @Before
    public void setUp() throws Exception
    {
        when(connection.getCloseFuture_()).thenReturn(UncancellableFuture.<Void>create());
        when(pipelineContext.getConnection_()).thenReturn(connection);

        when(pulseState.getTimeoutDelay_()).thenReturn(500L);

        handler = new PulseInitiatorHandler(executor, pulseState);
    }

    @Test
    public void shouldStartPulsingOnOutgoingStartPulseMessageEvent() throws Exception
    {
        MessageEvent event = buildStartPulseMessageEvent();

        when(pulseState.getCurrentPulseId_()).thenReturn(1);
        when(pulseState.isOngoing()).thenReturn(false);

        handler.onOutgoing_(pipelineContext, event);

        verify(pulseState).pulseStarted_(Mockito.<UncancellableFuture<Void>>any());
        verify(connection).send_(outgoingPacketCaptor.capture(),
            prioCaptor.capture());

        assertEquals(Prio.LO, prioCaptor.getValue());

        OutgoingAeroFSPacket outgoingPacket = outgoingPacketCaptor.getValue();
        assertEquals(Transport.PBTPHeader.Type.TRANSPORT_CHECK_PULSE_CALL, outgoingPacket.getHeader_().getType());
        assertEquals(1, outgoingPacket.getHeader_().getCheckPulse().getPulseId());
    }

    @Test
    public void shouldStartPulsingAndTimeout() throws Exception
    {
        MessageEvent event = buildStartPulseMessageEvent();

        when(pulseState.getCurrentPulseId_()).thenReturn(1);
        when(pulseState.isOngoing()).thenReturn(false);

        handler.onOutgoing_(pipelineContext, event);

        InOrder ordered = inOrder(pulseState, connection);
        ordered.verify(pulseState).pulseStarted_(Mockito.<UncancellableFuture<Void>>any());
        ordered.verify(connection).send_(anyObject(), any(Prio.class));

        when(pulseState.isOngoing()).thenReturn(true);

        verify(executor).executeAfterDelay(runnableCaptor.capture(), delayCaptor.capture());
        runnableCaptor.getValue().run();

        ordered.verify(pulseState).pulseTimedOut_();
        ordered.verify(connection).send_(anyObject(), any(Prio.class));
    }

    @Test
    public void shouldStartPulsingTimeoutThenDisconnect() throws Exception
    {
        MessageEvent event = buildStartPulseMessageEvent();

        when(pulseState.getCurrentPulseId_()).thenReturn(1);
        when(pulseState.isOngoing()).thenReturn(false);

        handler.onOutgoing_(pipelineContext, event);

        when(pulseState.isOngoing()).thenReturn(true);
        when(pulseState.shouldDisconnect_()).thenReturn(true);

        InOrder ordered = inOrder(executor, connection);
        ordered.verify(executor).executeAfterDelay(runnableCaptor.capture(), delayCaptor.capture());
        runnableCaptor.getValue().run();

        ordered.verify(connection).disconnect_(any(Exception.class));
    }

    @Test
    public void shouldStartPulsingAndReceiveReply() throws Exception
    {
        MessageEvent event = buildStartPulseMessageEvent();

        when(pulseState.getCurrentPulseId_()).thenReturn(1);
        when(pulseState.isOngoing()).thenReturn(false);

        handler.onOutgoing_(pipelineContext, event);

        when(pulseState.isOngoing()).thenReturn(true);

        MessageEvent incomingEvent = buildPulseReplyMessageEvent(pulseState.getCurrentPulseId_());

        handler.onIncoming_(pipelineContext, incomingEvent);

        verify(pulseState).pulseCompleted_();
    }

    @Test
    public void shouldStartPulsingThenReceiveInvalidReplyAndIgnore() throws Exception
    {
        MessageEvent event = buildStartPulseMessageEvent();

        when(pulseState.getCurrentPulseId_()).thenReturn(2);
        when(pulseState.isOngoing()).thenReturn(false);

        handler.onOutgoing_(pipelineContext, event);

        when(pulseState.isOngoing()).thenReturn(true);

        MessageEvent incomingEvent = buildPulseReplyMessageEvent(1);

        handler.onIncoming_(pipelineContext, incomingEvent);

        verify(pulseState, never()).pulseCompleted_();
        assertThrows(incomingEvent.getCompletionFuture_(), ExInvalidPulseReply.class);
    }

    @Test
    public void shouldSendPulsingMessageOnConnectionEventIfOngoingPulseExists() throws Exception
    {
        when(pulseState.getCurrentPulseId_()).thenReturn(23);
        when(pulseState.isOngoing()).thenReturn(true);

        ConnectionEvent connectionEvent = new ConnectionEvent(connection, ConnectionEvent.Type.CONNECT);

        handler.onOutgoing_(pipelineContext, connectionEvent);

        // Set the future here so that the connection event 'finishes'
        connectionEvent.getCompletionFuture_().set(null);

        verify(executor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(connection).send_(outgoingPacketCaptor.capture(), prioCaptor.capture());

        assertEquals(Prio.LO, prioCaptor.getValue());

        OutgoingAeroFSPacket outgoingPacket = outgoingPacketCaptor.getValue();
        assertEquals(Transport.PBTPHeader.Type.TRANSPORT_CHECK_PULSE_CALL, outgoingPacket.getHeader_().getType());
        assertEquals(23, outgoingPacket.getHeader_().getCheckPulse().getPulseId());
    }

    @Test
    public void shouldTimeoutAnOngoingPulseIfTheConnectionDrops() throws Exception
    {
        when(pulseState.getCurrentPulseId_()).thenReturn(1);
        when(pulseState.isOngoing()).thenReturn(false);

        ConnectionEvent event = new ConnectionEvent(connection, ConnectionEvent.Type.CONNECT);
        handler.onOutgoing_(pipelineContext, event);

        MessageEvent pulseEvent = buildStartPulseMessageEvent();
        handler.onOutgoing_(pipelineContext, pulseEvent);

        when(pulseState.isOngoing()).thenReturn(true);

        // The timeout was queued
        verify(executor).executeAfterDelay(runnableCaptor.capture(), delayCaptor.capture());

        // But timeout should not have been called yet
        verify(pulseState, never()).pulseTimedOut_();

        // CLose the connection
        ((UncancellableFuture<Void>)connection.getCloseFuture_()).set(null);

        // The task of calling pulse timed out in response to the close future
        InOrder ordered = inOrder(executor, pulseState);
        ordered.verify(executor).execute(runnableCaptor2.capture());
        runnableCaptor2.getValue().run();

        ordered.verify(pulseState).pulseTimedOut_();
        when(pulseState.getCurrentPulseId_()).thenReturn(2);

        // Run the timeout
        runnableCaptor.getValue().run();

        // The pulse has already timed out
        ordered.verify(pulseState, never()).pulseTimedOut_();
    }
}
