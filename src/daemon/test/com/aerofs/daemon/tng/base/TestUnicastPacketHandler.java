/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.tng.IUnicastListener;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.lib.OutArg;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Transport;
import com.aerofs.testlib.AbstractTest;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class TestUnicastPacketHandler extends AbstractTest
{
    private final IUnicastListener _unicastListener = mock(IUnicastListener.class);
    private final IPipelineContext _pipelineContext = mock(IPipelineContext.class);
    private final UnicastPacketHandler _handler = new UnicastPacketHandler(_unicastListener);

    @Test
    public void shouldForwardIncomingObjectsThatAreNotOfTypeMessageEvent()
            throws Exception
    {
        @SuppressWarnings("unchecked")
        IPipelineEvent<Void> event = mock(IPipelineEvent.class);

        _handler.onIncoming_(_pipelineContext, event);

        verify(_pipelineContext).sendIncomingEvent_(event);

        verifyZeroInteractions(_unicastListener);
    }

    @Test
    public void shouldForwardIncomingObjectsThatAreNotOfTypeIncomingAeroFSPacket()
            throws Exception
    {
        MessageEvent event = new MessageEvent(mock(IConnection.class),
                UncancellableFuture.<Void>create(), new Object(), Prio.LO);

        _handler.onIncoming_(_pipelineContext, event);

        verify(_pipelineContext).sendIncomingEvent_(event);

        verifyZeroInteractions(_unicastListener);
    }

    @Test
    public void shouldForwardIncomingObjectsThatAreOfTypeIncomingAeroFSPacketButDoNotContainAtomicPayload()
            throws Exception
    {
        IncomingAeroFSPacket packet = new IncomingAeroFSPacket(
                Transport.PBTPHeader.newBuilder().setType(Transport.PBTPHeader.Type.STREAM).build(),
                new ByteArrayInputStream(new byte[]{0}), 1);

        MessageEvent event = new MessageEvent(mock(IConnection.class),
                UncancellableFuture.<Void>create(), packet, Prio.LO);

        _handler.onIncoming_(_pipelineContext, event);

        verify(_pipelineContext).sendIncomingEvent_(event);
    }

    @Test
    public void shouldTakeIncomingAeroFSPacketObjectWithAtomicPayloadAndDeliverItToTheUnicastListenerAndSetTheCompletionFutureToSuccess()
            throws Exception
    {
        final DID PIPELINE_CONTEXT_DID = new DID(DID.ZERO);

        when(_pipelineContext.getDID_()).thenReturn(PIPELINE_CONTEXT_DID);

        final SID INCOMING_SID = new SID(SID.ZERO);
        final byte[] INCOMING_DATA = new byte[]{0};
        final ByteArrayInputStream INCOMING_BAIS = new ByteArrayInputStream(INCOMING_DATA);
        final int INCOMING_WIRELEN = 1;

        final Transport.PBTPHeader INCOMING_HDR = Transport.PBTPHeader
                .newBuilder()
                .setType(Transport.PBTPHeader.Type.DATAGRAM)
                .setSid(INCOMING_SID.toPB())
                .build();

        final IncomingAeroFSPacket INCOMING_AEROFS_PACKET = new IncomingAeroFSPacket(INCOMING_HDR,
                INCOMING_BAIS, INCOMING_WIRELEN);

        final IConnection CONNECTION = mock(IConnection.class);
        final UncancellableFuture<Void> COMPLETION_FUTURE = UncancellableFuture.create();
        final Prio PRI = Prio.LO;

        MessageEvent event = new MessageEvent(CONNECTION, COMPLETION_FUTURE, INCOMING_AEROFS_PACKET,
                PRI);

        assertFalse(COMPLETION_FUTURE.isDone());

        _handler.onIncoming_(_pipelineContext, event);

        assertTrue(COMPLETION_FUTURE.isDone());
        verify(_unicastListener).onUnicastDatagramReceived(PIPELINE_CONTEXT_DID, INCOMING_SID,
                INCOMING_BAIS, INCOMING_WIRELEN);

        // how do I verify that the bais has the right bytes?
    }

    @Test
    public void shouldForwardOutgoingObjectsThatAreNotOfTypeMessageEvent()
            throws Exception
    {
        @SuppressWarnings("unchecked")
        IPipelineEvent<Void> event = mock(IPipelineEvent.class);

        _handler.onOutgoing_(_pipelineContext, event);

        verify(_pipelineContext).sendOutgoingEvent_(event);
    }

    @Test
    public void shouldForwardOutgoingObjectsThatAreNotOfTypeOutgoingUnicastPacket()
            throws Exception
    {
        MessageEvent event = new MessageEvent(mock(IConnection.class),
                UncancellableFuture.<Void>create(), new Object(), Prio.LO);

        _handler.onOutgoing_(_pipelineContext, event);

        verify(_pipelineContext).sendOutgoingEvent_(event);
    }

    @Test
    public void shouldConvertOutgoingUnicastPacketObjectIntoAnOutgoingAeroFSPacketObjectWithTheCorrectParametersAndSendItToTheNextOutgoingHandlerInThePipeline()
            throws Exception
    {
        final IConnection CONNECTION = mock(IConnection.class);
        final SID UNICAST_PACKET_SID = new SID(SID.ZERO);
        final byte[] UNICAST_PACKET_PAYLOAD = new byte[]{0};

        final UncancellableFuture<Void> COMPLETION_FUTURE = UncancellableFuture.create();
        final Prio PRI = Prio.LO;

        final OutgoingUnicastPacket UNICAST_PACKET = new OutgoingUnicastPacket(UNICAST_PACKET_SID,
                UNICAST_PACKET_PAYLOAD);

        final MessageEvent event = new MessageEvent(CONNECTION, COMPLETION_FUTURE, UNICAST_PACKET,
                PRI);

        final OutArg<MessageEvent> nextEventRef = new OutArg<MessageEvent>(null);

        doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                MessageEvent nextEvent = (MessageEvent) invocation.getArguments()[0];
                nextEventRef.set(nextEvent);
                return null;
            }
        }).when(_pipelineContext).sendOutgoingEvent_(any(IPipelineEvent.class));

        _handler.onOutgoing_(_pipelineContext, event);

        MessageEvent nextEvent = nextEventRef.get();
        assertEquals(CONNECTION, nextEvent.getConnection_());
        assertEquals(COMPLETION_FUTURE, nextEvent.getCompletionFuture_());
        assertEquals(PRI, nextEvent.getPriority_());

        OutgoingAeroFSPacket packet = (OutgoingAeroFSPacket) nextEvent.getMessage_();
        assertEquals(UNICAST_PACKET_PAYLOAD, packet.getData_());

        Transport.PBTPHeader hdr = packet.getHeader_();
        assertEquals(Transport.PBTPHeader.Type.DATAGRAM, hdr.getType());
        assertEquals(UNICAST_PACKET_SID, new SID(hdr.getSid()));
    }
}