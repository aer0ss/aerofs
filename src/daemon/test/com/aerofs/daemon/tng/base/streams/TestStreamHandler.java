/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.streams;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.*;
import com.aerofs.daemon.tng.base.IStreamFactory;
import com.aerofs.daemon.tng.base.IncomingAeroFSPacket;
import com.aerofs.daemon.tng.base.MessageEvent;
import com.aerofs.daemon.tng.base.OutgoingAeroFSPacket;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;
import com.aerofs.daemon.tng.ex.ExStreamInvalid;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.testlib.AbstractTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;

import static org.mockito.Mockito.*;
import static com.aerofs.testlib.FutureAssert.assertNoThrow;
import static com.aerofs.testlib.FutureAssert.assertThrows;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyObject;

public class TestStreamHandler extends AbstractTest
{
    StreamHandler streamHandler;
    StreamMap<IIncomingStream> incomingMap;
    StreamMap<IOutgoingStream> outgoingMap;
    ISingleThreadedPrioritizedExecutor executor;

    @Mock IUnicastListener unicastListener;
    @Mock IStreamFactory streamFactory;
    @Mock IPipelineContext pipelineContext;
    @Mock IConnection peerConnection;

    @Before
    public void setUp()
    {
        incomingMap = new StreamMap<IIncomingStream>();
        outgoingMap = new StreamMap<IOutgoingStream>();
        executor = new ImmediateInlineExecutor();
        streamHandler = new StreamHandler(unicastListener, executor, streamFactory, incomingMap, outgoingMap);

        when(peerConnection.getCloseFuture_()).thenReturn(UncancellableFuture.<Void>create());
        when(pipelineContext.getConnection_()).thenReturn(peerConnection);
    }

    private MessageEvent buildNewOutgoingStreamMessageEvent(IOutgoingStream expectedStream) throws Exception
    {
        return new MessageEvent(peerConnection, UncancellableFuture.<Void>create(),
                new NewOutgoingStream(expectedStream.getStreamId_(), expectedStream.getSid_()),
                        expectedStream.getPriority_());
    }

    private PBTPHeader.Builder buildTransportHeader(IStream basis)
    {
        return PBTPHeader.newBuilder()
                .setType(PBTPHeader.Type.STREAM)
                .setSid(basis.getSid_().toPB());
    }

    private MessageEvent buildNewIncomingStreamMessageEvent(IIncomingStream expectedStream) throws Exception
    {
        PBTPHeader header = buildTransportHeader(expectedStream)
                .setStream(PBStream.newBuilder()
                        .setStreamId(expectedStream.getStreamId_().getInt())
                        .setType(PBStream.Type.BEGIN_STREAM))
                .build();

        return new MessageEvent(peerConnection, UncancellableFuture.<Void>create(),
                new IncomingAeroFSPacket(header, new ByteArrayInputStream(new byte[0]), 0),
                        expectedStream.getPriority_());
    }

    private MessageEvent buildAbortIncomingStreamMessageEvent(IIncomingStream expectedStream)
    {
        PBTPHeader header = buildTransportHeader(expectedStream)
                .setStream(PBStream.newBuilder()
                        .setStreamId(expectedStream.getStreamId_().getInt())
                        .setType(PBStream.Type.TX_ABORT_STREAM)
                        .setReason(InvalidationReason.INTERNAL_ERROR))
                .build();

        return new MessageEvent(peerConnection, UncancellableFuture.<Void>create(),
                new IncomingAeroFSPacket(header, new ByteArrayInputStream(new byte[0]), 0),
                        expectedStream.getPriority_());
    }

    private MessageEvent buildAbortOutgoingStreamMessageEvent(IOutgoingStream expectedStream)
    {
        PBTPHeader header = buildTransportHeader(expectedStream)
                .setStream(PBStream.newBuilder()
                        .setStreamId(expectedStream.getStreamId_().getInt())
                        .setType(PBStream.Type.RX_ABORT_STREAM)
                        .setReason(InvalidationReason.INTERNAL_ERROR))
                .build();

        return new MessageEvent(peerConnection, UncancellableFuture.<Void>create(),
                new IncomingAeroFSPacket(header, new ByteArrayInputStream(new byte[0]), 0),
                        expectedStream.getPriority_());
    }

    private MessageEvent buildPayloadIncomingStreamMessageEvent(IIncomingStream expectedStream, byte[] data)
    {
        PBTPHeader header = buildTransportHeader(expectedStream)
                .setStream(PBStream.newBuilder()
                        .setStreamId(expectedStream.getStreamId_().getInt())
                        .setSeqNum(1)
                        .setType(PBStream.Type.PAYLOAD))
                .build();

        return new MessageEvent(peerConnection, UncancellableFuture.<Void>create(),
                new IncomingAeroFSPacket(header, new ByteArrayInputStream(data), data.length),
                        expectedStream.getPriority_());
    }

    @Test
    public void shouldForwardOutgoingEventIfNotNewOutgoingMessageEvent() throws Exception
    {
        MessageEvent event = new MessageEvent(peerConnection, UncancellableFuture.<Void>create(), new Object(), Prio.LO);

        streamHandler.onOutgoing_(pipelineContext, event);

        // The message event should have just been forwarded, not finished
        assertFalse(event.getCompletionFuture_().isDone());

        // Verify that the MessageEvent was propagated to the rest of the pipeline
        verify(pipelineContext, times(1)).sendOutgoingEvent_(event);

        @SuppressWarnings("unchecked")
        IPipelineEvent<Void> weirdEvent = mock(IPipelineEvent.class);
        when(weirdEvent.getCompletionFuture_()).thenReturn(UncancellableFuture.<Void>create());
        when(weirdEvent.getConnection_()).thenReturn(peerConnection);

        streamHandler.onOutgoing_(pipelineContext, weirdEvent);

        assertFalse(weirdEvent.getCompletionFuture_().isDone());

        // Verify that the weird event was propagated to the rest of the pipeline
        verify(pipelineContext, times(1)).sendOutgoingEvent_(weirdEvent);
    }

    @Test
    public void shouldForwardIncomingEventIfNotIncomingAeroFSPacket() throws Exception
    {
        MessageEvent event = new MessageEvent(peerConnection, UncancellableFuture.<Void>create(), new Object(), Prio.LO);

        streamHandler.onIncoming_(pipelineContext, event);

        // The message event should have just been forwarded, not finished
        assertFalse(event.getCompletionFuture_().isDone());

        // Verify that the MessageEvent was propagated to the rest of the pipeline
        verify(pipelineContext, times(1)).sendIncomingEvent_(event);

        @SuppressWarnings("unchecked")
        IPipelineEvent<Void> weirdEvent = mock(IPipelineEvent.class);
        when(weirdEvent.getCompletionFuture_()).thenReturn(UncancellableFuture.<Void>create());
        when(weirdEvent.getConnection_()).thenReturn(peerConnection);

        streamHandler.onIncoming_(pipelineContext, weirdEvent);

        assertFalse(weirdEvent.getCompletionFuture_().isDone());

        // Verify that the weird event was propagated to the rest of the pipeline
        verify(pipelineContext, times(1)).sendIncomingEvent_(weirdEvent);

        PBTPHeader header = PBTPHeader.newBuilder()
                .setType(PBTPHeader.Type.DIAGNOSIS)
                .build();
        IncomingAeroFSPacket packet = new IncomingAeroFSPacket(header, new ByteArrayInputStream(new byte[0]), 0);
        MessageEvent invalidEvent = new MessageEvent(peerConnection, UncancellableFuture.<Void>create(), packet, Prio.LO);

        streamHandler.onIncoming_(pipelineContext, invalidEvent);

        assertFalse(invalidEvent.getCompletionFuture_().isDone());

        // Verify that the weird event was propagated to the rest of the pipeline
        verify(pipelineContext, times(1)).sendIncomingEvent_(invalidEvent);
    }

    @Test
    public void shouldSetCompletionFutureOfAbortMessageEventWithExceptionWhenNoIncomingStreamToAbort() throws Exception
    {
        // Expected incoming stream
        final IncomingStream expectedIncomingStream = IncomingStream.getInstance_(executor, peerConnection, new StreamID(1), new DID(DID.ZERO), new SID(SID.ZERO), Prio.LO);

        // Build the abort incoming message event
        MessageEvent abortEvent = buildAbortIncomingStreamMessageEvent(expectedIncomingStream);

        // Abort the stream
        streamHandler.onIncoming_(pipelineContext, abortEvent);

        assertThrows(abortEvent.getCompletionFuture_(), ExStreamInvalid.class);
    }

    @Test
    public void shouldSetCompletionFutureOfAbortMessageEventWithExceptionWhenNoOutgoingStreamToAbort() throws Exception
    {
        // Expected outgoing stream
        final OutgoingStream expectedOutgoingStream = OutgoingStream.getInstance_(executor, peerConnection, new StreamID(1), new DID(DID.ZERO), new SID(SID.ZERO), Prio.LO);

        // Build the abort incoming message event
        MessageEvent abortEvent = buildAbortOutgoingStreamMessageEvent(expectedOutgoingStream);

        // Abort the stream
        streamHandler.onIncoming_(pipelineContext, abortEvent);

        assertThrows(abortEvent.getCompletionFuture_(), ExStreamInvalid.class);
    }

    @Captor ArgumentCaptor<IPipelineEvent<Void>> eventCaptor;

    @Test
    public void shouldSetCompletionFutureOfPayloadMessageEventWhenNoIncomingStreamExistsForPayload() throws Exception
    {
        // Expected incoming stream
        final IncomingStream expectedIncomingStream = IncomingStream.getInstance_(executor, peerConnection, new StreamID(1), new DID(DID.ZERO), new SID(SID.ZERO), Prio.LO);

        // Build the payload event
        MessageEvent payloadEvent = buildPayloadIncomingStreamMessageEvent(expectedIncomingStream, "hello world".getBytes());

        // Abort the stream
        streamHandler.onIncoming_(pipelineContext, payloadEvent);

        assertThrows(payloadEvent.getCompletionFuture_(), ExStreamInvalid.class);

        // Check to see if an abort message was sent back to the sender
        verify(pipelineContext, times(1)).sendOutgoingEvent_(eventCaptor.capture());
        assertTrue(eventCaptor.getValue() instanceof MessageEvent);
        MessageEvent messageEvent = (MessageEvent) eventCaptor.getValue();
        assertTrue(messageEvent.getMessage_() instanceof OutgoingAeroFSPacket);
        OutgoingAeroFSPacket packet = (OutgoingAeroFSPacket) messageEvent.getMessage_();
        assertEquals(PBTPHeader.Type.STREAM, packet.getHeader_().getType());
        assertEquals(PBStream.Type.RX_ABORT_STREAM, packet.getHeader_().getStream().getType());
        assertEquals(expectedIncomingStream.getStreamId_().getInt(), packet.getHeader_().getStream().getStreamId());
    }

    @Test
    public void shouldCreateNewOutgoingStreamOnNewOutgoingMessageReceivedAndSucceedBegin() throws Exception
    {
        // Expected stream
        final OutgoingStream expectedStream = OutgoingStream.getInstance_(executor, peerConnection, new StreamID(1), new DID(DID.ZERO), new SID(SID.ZERO), Prio.LO);

        // Build the proper pipeline event
        MessageEvent event = buildNewOutgoingStreamMessageEvent(expectedStream);

        // Set the mocks to do the required operations
        when(streamFactory.createOutgoing_(peerConnection, expectedStream.getStreamId_(), expectedStream.getSid_(), expectedStream.getPriority_()))
                .thenReturn(expectedStream);
        when(peerConnection.send_(anyObject(), eq(expectedStream.getPriority_()))).thenReturn(UncancellableFuture.<Void>createSucceeded(null));

        // Process the event
        streamHandler.onOutgoing_(pipelineContext, event);

        // Verify that the new stream is in the map
        assertEquals(expectedStream, outgoingMap.get(expectedStream.getStreamId_()));

        // Ensure the event completed successfully
        assertNoThrow(event.getCompletionFuture_());
        assertNoThrow(((NewOutgoingStream)event.getMessage_()).getStreamCreationFuture_());

        // Verify the send method was called with a begin message
        ArgumentCaptor<OutgoingAeroFSPacket> headerCaptor = ArgumentCaptor.forClass(OutgoingAeroFSPacket.class);
        ArgumentCaptor<Prio> prioCaptor = ArgumentCaptor.forClass(Prio.class);
        verify(peerConnection, times(1)).send_(headerCaptor.capture(), prioCaptor.capture());
        OutgoingAeroFSPacket packet = headerCaptor.getValue();
        assertEquals(PBTPHeader.Type.STREAM, packet.getHeader_().getType());
        assertEquals(PBStream.Type.BEGIN_STREAM, packet.getHeader_().getStream().getType());

        // The pipeline context should not be called because the StreamHandler
        // stops the propagation of the NewOutgoingStream message event
        verifyZeroInteractions(pipelineContext);
    }

    @Test
    public void shouldCreateNewOutgoingStreamOnNewOutgoingMessageReceivedAndFailBegin() throws Exception
    {
        // Expected stream
        final OutgoingStream expectedStream = OutgoingStream.getInstance_(executor, peerConnection, new StreamID(1), new DID(DID.ZERO), new SID(SID.ZERO), Prio.LO);

        // Build the proper pipeline event
        MessageEvent event = buildNewOutgoingStreamMessageEvent(expectedStream);

        // Set the mocks to do the required operations
        when(streamFactory.createOutgoing_(peerConnection, expectedStream.getStreamId_(), expectedStream.getSid_(), expectedStream.getPriority_()))
                .thenReturn(expectedStream);
        when(peerConnection.send_(anyObject(), eq(expectedStream.getPriority_()))).thenReturn(UncancellableFuture.<Void>createFailed(new Exception("test")));

        // Process the event
        streamHandler.onOutgoing_(pipelineContext, event);

        // Verify the event failed
        assertThrows(event.getCompletionFuture_(), Exception.class, "test");
        assertThrows(((NewOutgoingStream)event.getMessage_()).getStreamCreationFuture_(), Exception.class, "test");

        // Verify the stream failed
        assertThrows(expectedStream.getCloseFuture_(), Exception.class, "test");

        // Verify there is no stream in the map
        try {
            outgoingMap.get(expectedStream.getStreamId_());
            fail();
        } catch (ExStreamInvalid e) {}

        // The pipeline context should not be called because the StreamHandler
        // stops the propagation of the NewOutgoingStream message event
        verifyZeroInteractions(pipelineContext);
    }

    @Test
    public void shouldCreateOutgoingStreamAndFailToCreateAnotherOutgoingStreamWithTheSameIdAsTheFirst() throws Exception
    {
        // Expected stream
        final OutgoingStream expectedStream = OutgoingStream.getInstance_(executor, peerConnection, new StreamID(1), new DID(DID.ZERO), new SID(SID.ZERO), Prio.LO);

        // Build the proper pipeline event
        MessageEvent event = buildNewOutgoingStreamMessageEvent(expectedStream);

        // Set the mocks to do the required operations
        when(streamFactory.createOutgoing_(peerConnection, expectedStream.getStreamId_(), expectedStream.getSid_(), expectedStream.getPriority_()))
                .thenReturn(expectedStream);
        when(peerConnection.send_(anyObject(), eq(expectedStream.getPriority_()))).thenReturn(UncancellableFuture.<Void>createSucceeded(null));

        // Process the event
        streamHandler.onOutgoing_(pipelineContext, event);

        // Verify that the new stream is in the map
        assertEquals(expectedStream, outgoingMap.get(expectedStream.getStreamId_()));

        final OutgoingStream secondStream = OutgoingStream.getInstance_(executor, peerConnection, new StreamID(1), new DID(DID.ZERO), new SID(SID.ZERO), Prio.LO);

        // Build the proper pipeline event
        MessageEvent secondEvent = buildNewOutgoingStreamMessageEvent(secondStream);

        when(streamFactory.createOutgoing_(peerConnection, secondStream.getStreamId_(), secondStream.getSid_(), secondStream.getPriority_()))
                .thenReturn(secondStream);

        // Process the event
        streamHandler.onOutgoing_(pipelineContext, secondEvent);

        // The second stream should fail to be created
        assertThrows(secondEvent.getCompletionFuture_(), ExStreamAlreadyExists.class);
    }

    @Test
    public void shouldCreateOutgoingStreamAndAbortStreamOnIncomingAbortMessageEventReceived() throws Exception
    {
        // Expected stream
        final OutgoingStream expectedStream = OutgoingStream.getInstance_(executor, peerConnection, new StreamID(1), new DID(DID.ZERO), new SID(SID.ZERO), Prio.LO);

        // Build the proper pipeline event
        MessageEvent event = buildNewOutgoingStreamMessageEvent(expectedStream);

        // Set the mocks to do the required operations
        when(streamFactory.createOutgoing_(peerConnection, expectedStream.getStreamId_(), expectedStream.getSid_(), expectedStream.getPriority_()))
                .thenReturn(expectedStream);
        when(peerConnection.send_(anyObject(), eq(expectedStream.getPriority_()))).thenReturn(UncancellableFuture.<Void>createSucceeded(null));

        // Process the event
        streamHandler.onOutgoing_(pipelineContext, event);

        // Verify that the new stream is in the map
        assertEquals(expectedStream, outgoingMap.get(expectedStream.getStreamId_()));

        // Build the abort message
        MessageEvent abortEvent = buildAbortOutgoingStreamMessageEvent(expectedStream);

        // Pump the event in
        streamHandler.onIncoming_(pipelineContext, abortEvent);

        // Verify that the stream is aborted
        assertThrows(expectedStream.getCloseFuture_(), ExStreamInvalid.class);

        // Verify the stream is removed from the map
        try {
            outgoingMap.get(expectedStream.getStreamId_());
            fail();
        } catch (ExStreamInvalid e) {}
    }

    @Test
    public void shouldCreateIncomingStreamAndNotifyListenerWhenBeginIncomingStreamEventReceived() throws Exception
    {
        // Expected incoming stream
        final IncomingStream expectedIncomingStream = IncomingStream.getInstance_(executor, peerConnection, new StreamID(1), new DID(DID.ZERO), new SID(SID.ZERO), Prio.LO);

        // Build the incoming message event
        MessageEvent event = buildNewIncomingStreamMessageEvent(expectedIncomingStream);

        // Set the mocks to return the expected objects
        when(streamFactory.createIncoming_(peerConnection, expectedIncomingStream.getStreamId_(), expectedIncomingStream.getSid_(), expectedIncomingStream.getPriority_()))
                .thenReturn(expectedIncomingStream);

        // Pump in the event
        streamHandler.onIncoming_(pipelineContext, event);

        // Verify that the listener was called
        verify(unicastListener).onStreamBegun(expectedIncomingStream);

        // Verify that the new stream is in the map
        assertEquals(expectedIncomingStream, incomingMap.get(expectedIncomingStream.getStreamId_()));

        // Ensure the event completed successfully
        assertNoThrow(event.getCompletionFuture_());

        // The pipeline context should not be called because the StreamHandler
        // stops the propagation of the BEGIN_STREAM message event
        verify(pipelineContext).getConnection_();
        verifyNoMoreInteractions(pipelineContext);
    }

    @Test
    public void shouldCreateIncomingStreamAndFailWhenCreatingSecondIncomingStreamWithTheSameStreamIdAsTheFirst() throws Exception
    {
        // Expected incoming stream
        final IncomingStream expectedIncomingStream = IncomingStream.getInstance_(executor, peerConnection, new StreamID(1), new DID(DID.ZERO), new SID(SID.ZERO), Prio.LO);

        // Build the incoming message event
        MessageEvent event = buildNewIncomingStreamMessageEvent(expectedIncomingStream);

        // Set the mocks to return the expected objects
        when(streamFactory.createIncoming_(peerConnection, expectedIncomingStream.getStreamId_(), expectedIncomingStream.getSid_(), expectedIncomingStream.getPriority_()))
                .thenReturn(expectedIncomingStream);

        // Pump in the event
        streamHandler.onIncoming_(pipelineContext, event);

        // Verify that the listener was called
        verify(unicastListener).onStreamBegun(expectedIncomingStream);

        // Second incoming stream
        final IncomingStream secondIncomingStream = IncomingStream.getInstance_(executor, peerConnection, new StreamID(1), new DID(DID.ZERO), new SID(SID.ZERO), Prio.LO);

        // Build the incoming message event
        MessageEvent secondEvent = buildNewIncomingStreamMessageEvent(secondIncomingStream);

        // Set the mocks to return the expected objects
        when(streamFactory.createIncoming_(peerConnection, secondIncomingStream.getStreamId_(), secondIncomingStream.getSid_(), secondIncomingStream.getPriority_()))
                .thenReturn(secondIncomingStream);

        // Pump in the event
        streamHandler.onIncoming_(pipelineContext, secondEvent);

        assertThrows(secondEvent.getCompletionFuture_(), ExStreamAlreadyExists.class);

    }

    @Test
    public void shouldCreateIncomingStreamAndRemoveStreamFromMapOnIncomingAbortMessageReceived() throws Exception
    {
        // Expected incoming stream
        final IncomingStream expectedIncomingStream = IncomingStream.getInstance_(executor, peerConnection, new StreamID(1), new DID(DID.ZERO), new SID(SID.ZERO), Prio.LO);

        // Build the incoming message event
        MessageEvent event = buildNewIncomingStreamMessageEvent(expectedIncomingStream);

        // Set the mocks to return the expected objects
        when(streamFactory.createIncoming_(peerConnection, expectedIncomingStream.getStreamId_(), expectedIncomingStream.getSid_(), expectedIncomingStream.getPriority_()))
                .thenReturn(expectedIncomingStream);

        // Pump in the event
        streamHandler.onIncoming_(pipelineContext, event);

        // Verify that the new stream is in the map
        assertEquals(expectedIncomingStream, incomingMap.get(expectedIncomingStream.getStreamId_()));

        // Build the abort incoming message event
        MessageEvent abortEvent = buildAbortIncomingStreamMessageEvent(expectedIncomingStream);

        // Abort the stream
        streamHandler.onIncoming_(pipelineContext, abortEvent);

        // Verify the event completed successfully
        assertThrows(abortEvent.getCompletionFuture_(), ExStreamInvalid.class);

        // Verify the stream aborted successfully
        assertThrows(expectedIncomingStream.getCloseFuture_(), ExStreamInvalid.class);

        // Verify that the stream is no longer in the map
        try {
            incomingMap.get(expectedIncomingStream.getStreamId_());
            fail();
        } catch (ExStreamInvalid e) {}
    }

    @Test
    public void shouldAddBytesToIncomingStreamOnIncomingPayloadMessageEventReceived() throws Exception
    {
       final IncomingStream expectedIncomingStream = mock(IncomingStream.class);
       when(expectedIncomingStream.getCloseFuture_()).thenReturn(UncancellableFuture.<Void>create());
       when(expectedIncomingStream.getDid_()).thenReturn(new DID(DID.ZERO));
       when(expectedIncomingStream.getSid_()).thenReturn(new SID(SID.ZERO));
       when(expectedIncomingStream.getStreamId_()).thenReturn(new StreamID(1));
       when(expectedIncomingStream.getPriority_()).thenReturn(Prio.LO);

       // Build the incoming message event
       MessageEvent event = buildNewIncomingStreamMessageEvent(expectedIncomingStream);

       // Set the mocks to return the expected objects
       when(streamFactory.createIncoming_(peerConnection, expectedIncomingStream.getStreamId_(), expectedIncomingStream.getSid_(), expectedIncomingStream.getPriority_()))
               .thenReturn(expectedIncomingStream);

       // Pump in the event
       streamHandler.onIncoming_(pipelineContext, event);

       // Verify that the new stream is in the map
       assertEquals(expectedIncomingStream, incomingMap.get(expectedIncomingStream.getStreamId_()));

       final byte[] expectedPayload = "Hello AeroFS!".getBytes();

       // Build payload event
       MessageEvent payloadEvent = buildPayloadIncomingStreamMessageEvent(expectedIncomingStream, expectedPayload);

       // Pump in the event
       streamHandler.onIncoming_(pipelineContext, payloadEvent);

       ArgumentCaptor<Integer> seqCaptor = ArgumentCaptor.forClass(Integer.class);
       ArgumentCaptor<ByteArrayInputStream> baisCaptor = ArgumentCaptor.forClass(ByteArrayInputStream.class);
       ArgumentCaptor<Integer> wirelenCaptor = ArgumentCaptor.forClass(Integer.class);
       verify(expectedIncomingStream, times(1)).onBytesReceived_(seqCaptor.capture(), baisCaptor.capture(), wirelenCaptor.capture());
       assertEquals(1, seqCaptor.getValue().intValue());
       ByteArrayInputStream bais = baisCaptor.getValue();
       byte[] output = new byte[bais.available()];
       bais.read(output);

       assertArrayEquals(expectedPayload, output);
    }

}
