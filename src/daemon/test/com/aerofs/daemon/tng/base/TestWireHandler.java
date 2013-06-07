/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.mockito.Mockito.*;
import static com.aerofs.testlib.FutureAssert.assertThrows;
import static org.junit.Assert.*;

public class TestWireHandler extends AbstractTest
{
    WireHandler wireHandler;

    @Mock IPipelineContext pipelineContext;
    @Mock IConnection peerConnection;

    @Before
    public void setUp() throws Exception
    {
        wireHandler = new WireHandler();
        when(pipelineContext.getConnection_()).thenReturn(peerConnection);
        when(peerConnection.getCloseFuture_()).thenReturn(UncancellableFuture.<Void>create());
    }

    @Test
    public void shouldForwardOutgoingEventWhenEventIsNotAMessageEventWithOutgoingAeroFSPacket() throws Exception
    {
        // Build a MessageEvent with the wrong message object type
        MessageEvent messageEvent = new MessageEvent(peerConnection, UncancellableFuture.<Void>create(), new Object(), Prio.LO);

        wireHandler.onOutgoing_(pipelineContext, messageEvent);

        assertFalse(messageEvent.getCompletionFuture_().isDone());

        verify(pipelineContext, times(1)).sendOutgoingEvent_(messageEvent);

        // Build an IPipelineEvent that is not a MessageEvent
        @SuppressWarnings("unchecked")
        IPipelineEvent<Void> otherEvent = mock(IPipelineEvent.class);
        when(otherEvent.getConnection_()).thenReturn(peerConnection);
        when(otherEvent.getCompletionFuture_()).thenReturn(UncancellableFuture.<Void>create());

        wireHandler.onOutgoing_(pipelineContext, otherEvent);

        verify(pipelineContext, times(1)).sendOutgoingEvent_(otherEvent);
    }

    @Test
    public void shouldForwardIncomingEventWhenEventIsNotAMessageEventWithWireDataMessage() throws Exception
    {
        // Build a MessageEvent with the wrong message object type
        MessageEvent messageEvent = new MessageEvent(peerConnection, UncancellableFuture.<Void>create(), new Object(), Prio.LO);

        wireHandler.onIncoming_(pipelineContext, messageEvent);

        assertFalse(messageEvent.getCompletionFuture_().isDone());

        verify(pipelineContext, times(1)).sendIncomingEvent_(messageEvent);

        // Build an IPipelineEvent that is not a MessageEvent
        @SuppressWarnings("unchecked")
        IPipelineEvent<Void> otherEvent = mock(IPipelineEvent.class);
        when(otherEvent.getConnection_()).thenReturn(peerConnection);
        when(otherEvent.getCompletionFuture_()).thenReturn(UncancellableFuture.<Void>create());

        wireHandler.onIncoming_(pipelineContext, otherEvent);

        verify(pipelineContext, times(1)).sendIncomingEvent_(otherEvent);
    }

    @Test
    public void shouldSerializeHeaderOfOutgoingAeroFSPacketWithNoPayloadData() throws Exception
    {
        final PBTPHeader expectedHeader = PBTPHeader.newBuilder()
                .setType(PBTPHeader.Type.DATAGRAM)
                .build();
        OutgoingAeroFSPacket packet = new OutgoingAeroFSPacket(expectedHeader, null);
        MessageEvent event = new MessageEvent(peerConnection, UncancellableFuture.<Void>create(), packet, Prio.LO);

        wireHandler.onOutgoing_(pipelineContext, event);

        assertFalse(event.getCompletionFuture_().isDone());

        ArgumentCaptor<MessageEvent> eventCaptor = ArgumentCaptor.forClass(MessageEvent.class);
        verify(pipelineContext, times(1)).sendOutgoingEvent_(eventCaptor.capture());
        assertTrue(eventCaptor.getValue().getMessage_() instanceof byte[][]);
        byte[][] data = (byte[][]) eventCaptor.getValue().getMessage_();

        PBTPHeader output = PBTPHeader.parseDelimitedFrom(new ByteArrayInputStream(data[0]));
        assertEquals(expectedHeader.getType(), output.getType());

        // No payload, so the byte array of byte arrays should only have one element, aka, the header
        assertEquals(1, data.length);

        verifyNoMoreInteractions(pipelineContext);
    }

    @Test
    public void shouldSerializeHeaderOfOutgoingAeroFSPacketWithPayloadData() throws Exception
    {
        final PBTPHeader expectedHeader = PBTPHeader.newBuilder()
                .setType(PBTPHeader.Type.DATAGRAM)
                .build();
        final byte[] expectedPayload = "Hello AeroFS!".getBytes();
        OutgoingAeroFSPacket packet = new OutgoingAeroFSPacket(expectedHeader, expectedPayload);
        MessageEvent event = new MessageEvent(peerConnection, UncancellableFuture.<Void>create(), packet, Prio.LO);

        wireHandler.onOutgoing_(pipelineContext, event);

        assertFalse(event.getCompletionFuture_().isDone());

        ArgumentCaptor<MessageEvent> eventCaptor = ArgumentCaptor.forClass(MessageEvent.class);
        verify(pipelineContext, times(1)).sendOutgoingEvent_(eventCaptor.capture());
        assertTrue(eventCaptor.getValue().getMessage_() instanceof byte[][]);
        byte[][] data = (byte[][]) eventCaptor.getValue().getMessage_();

        PBTPHeader output = PBTPHeader.parseDelimitedFrom(new ByteArrayInputStream(data[0]));
        assertEquals(expectedHeader.getType(), output.getType());

        // Payload exists, so there should be two elements
        assertEquals(2, data.length);

        assertArrayEquals(expectedPayload, data[1]);

        verifyNoMoreInteractions(pipelineContext);
    }

    private byte[] buildTransportHeaderWithPayload(PBTPHeader header, byte[] payload) throws Exception
    {
        // Add 4 because protobuf adds a varint as the size of the message
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        header.writeDelimitedTo(output);
        output.write(payload);
        return output.toByteArray();
    }

    @Test
    public void shouldSetCompletionFutureWithExceptionWhenUnableToDecodeIncomingWireDataIntoTransportHeader() throws Exception
    {
        // Build a non PBTPHeader message
        final byte[] invalidData = "This message is definitely not a PBTPHeader".getBytes();

        MessageEvent event = new MessageEvent(peerConnection, UncancellableFuture.<Void>create(),
                new WireData(invalidData, invalidData.length), Prio.LO);

        wireHandler.onIncoming_(pipelineContext, event);

        assertThrows(event.getCompletionFuture_(), IOException.class);
    }

    @Test
    public void shouldSendIncomingAeroFSPacketWhenIncomingMessageEventContainsWireDataWithValidTransportHeader() throws Exception
    {
        // Build a MessageEvent that encapsulates WireData, representing a PBTPHeader
        final PBTPHeader expectedHeader = PBTPHeader.newBuilder()
                .setType(PBTPHeader.Type.DATAGRAM)
                .build();

        final byte[] expectedPayload = "Hello AeroFS!".getBytes();
        final WireData data = new WireData(buildTransportHeaderWithPayload(expectedHeader, expectedPayload), 0);
        MessageEvent validEvent = new MessageEvent(peerConnection, UncancellableFuture.<Void>create(), data, Prio.LO);

        wireHandler.onIncoming_(pipelineContext, validEvent);

        // The should be transformed and passed along the pipeline, but not finished
        assertFalse(validEvent.getCompletionFuture_().isDone());

        // Verify the correct output was sent
        ArgumentCaptor<MessageEvent> outputEvent = ArgumentCaptor.forClass(MessageEvent.class);
        verify(pipelineContext, times(1)).sendIncomingEvent_(outputEvent.capture());
        assertTrue(outputEvent.getValue().getMessage_() instanceof IncomingAeroFSPacket);
        IncomingAeroFSPacket packet = (IncomingAeroFSPacket) outputEvent.getValue().getMessage_();

        assertEquals(expectedHeader.getType(), packet.getHeader_().getType());

        byte[] output = new byte[packet.getPayload_().available()];
        packet.getPayload_().read(output);
        assertArrayEquals(expectedPayload, output);

        verifyNoMoreInteractions(pipelineContext);
    }



}
