/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pulse;

import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.tng.base.IncomingAeroFSPacket;
import com.aerofs.daemon.tng.base.MessageEvent;
import com.aerofs.daemon.tng.base.OutgoingAeroFSPacket;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.daemon.tng.base.pulse.ex.ExInvalidPulseMessage;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.proto.Transport.PBCheckPulse;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;

import static com.aerofs.testlib.FutureAssert.assertNoThrow;
import static com.aerofs.testlib.FutureAssert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class TestPulseResponderHandler extends AbstractTest
{
    PulseResponderHandler handler;

    @Mock IConnection connection;
    @Mock IPipelineContext pipelineContext;

    @Captor ArgumentCaptor<MessageEvent> eventCaptor;

    @Before
    public void setUp() throws Exception
    {
        when(pipelineContext.getConnection_()).thenReturn(connection);
        handler = new PulseResponderHandler();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldForwardNonPulseCallEvents() throws Exception
    {
        MessageEvent event = new MessageEvent(connection,
                UncancellableFuture.<Void>create(), new Object(), Prio.LO);

        handler.onIncoming_(pipelineContext, event);
        assertFalse(event.getCompletionFuture_().isDone());
        verify(pipelineContext).sendIncomingEvent_(event);

        IPipelineEvent<Void> weirdEvent = mock(IPipelineEvent.class);
        when(weirdEvent.getCompletionFuture_()).thenReturn(
            UncancellableFuture.<Void>create());

        handler.onIncoming_(pipelineContext, weirdEvent);
        assertFalse(weirdEvent.getCompletionFuture_().isDone());
        verify(pipelineContext).sendIncomingEvent_(weirdEvent);

        PBTPHeader header = PBTPHeader.newBuilder()
                .setType(PBTPHeader.Type.DATAGRAM)
                .build();
        IncomingAeroFSPacket packet = new IncomingAeroFSPacket(header,
                new ByteArrayInputStream(new byte[0]), 0);
        MessageEvent datagramEvent = new MessageEvent(connection,
                UncancellableFuture.<Void>create(), packet, Prio.LO);

        handler.onIncoming_(pipelineContext, datagramEvent);
        assertFalse(datagramEvent.getCompletionFuture_().isDone());
        verify(pipelineContext).sendIncomingEvent_(datagramEvent);
    }

    @Test
    public void shouldSetCompletionFutureWithExceptionWhenPulseCallHeaderIsInvalid()
        throws Exception
    {
        PBTPHeader header = PBTPHeader.newBuilder()
                .setType(PBTPHeader.Type.TRANSPORT_CHECK_PULSE_CALL)
                .build();
        IncomingAeroFSPacket packet = new IncomingAeroFSPacket(header,
            new ByteArrayInputStream(new byte[0]), 0);
        MessageEvent event = new MessageEvent(connection,
                UncancellableFuture.<Void>create(), packet, Prio.LO);

        handler.onIncoming_(pipelineContext, event);
        assertThrows(event.getCompletionFuture_(), ExInvalidPulseMessage.class);
        verify(pipelineContext, never()).sendIncomingEvent_(
                any(IPipelineEvent.class));
        verify(pipelineContext, never()).sendOutgoingEvent_(
                any(IPipelineEvent.class));
    }

    @Test
    public void shouldReplyWithPulseReplyMessageWhenPulseCallMessageReceived()
        throws Exception
    {
        PBTPHeader header = PBTPHeader.newBuilder()
            .setType(PBTPHeader.Type.TRANSPORT_CHECK_PULSE_CALL)
            .setCheckPulse(PBCheckPulse.newBuilder()
                    .setPulseId(1)
                    .build())
            .build();
        IncomingAeroFSPacket packet = new IncomingAeroFSPacket(header,
            new ByteArrayInputStream(new byte[0]), 0);
        MessageEvent event = new MessageEvent(connection,
            UncancellableFuture.<Void>create(), packet, Prio.LO);

        handler.onIncoming_(pipelineContext, event);
        assertNoThrow(event.getCompletionFuture_());
        verify(pipelineContext, never()).sendIncomingEvent_(
            any(IPipelineEvent.class));
        verify(pipelineContext).sendOutgoingEvent_(eventCaptor.capture());
        assertTrue(eventCaptor.getValue().getMessage_() instanceof OutgoingAeroFSPacket);
        OutgoingAeroFSPacket outgoingPacket = (OutgoingAeroFSPacket) eventCaptor.getValue().getMessage_();
        assertEquals(PBTPHeader.Type.TRANSPORT_CHECK_PULSE_REPLY, outgoingPacket.getHeader_().getType());
        assertTrue(outgoingPacket.getHeader_().hasCheckPulse());
        assertEquals(1, outgoingPacket.getHeader_().getCheckPulse().getPulseId());
    }
}
