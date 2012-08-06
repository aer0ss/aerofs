/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.tng.ImmediateInlineExecutor;
import com.aerofs.daemon.tng.base.pipeline.IPipeline;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.OutArg;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

import static com.aerofs.daemon.lib.Prio.HI;
import static com.aerofs.daemon.lib.Prio.LO;
import static com.aerofs.daemon.tng.base.ConnectionEvent.Type.CONNECT;
import static com.aerofs.daemon.tng.base.ConnectionEvent.Type.DISCONNECT;
import static com.aerofs.testlib.FutureAssert.assertCompletionFutureChainedProperly;
import static com.aerofs.testlib.FutureAssert.getFutureThrowable;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

// XXX: hmm. this entire test is testing implementation - I don't know how to avoid this
// FIXME: not extending from AbstractTest because this class requires initMocks in the constructor
public class TestPeerConnection
{
    private final PeerConnection _connection;
    private final UncancellableFuture<Void> _unicastCloseFuture = UncancellableFuture.create();

    @Captor private ArgumentCaptor<IPipelineEvent<?>> _eventCaptor;
    @Mock private IPipeline _pipeline;

    public TestPeerConnection()
    {
        MockitoAnnotations.initMocks(this);

        IUnicastConnection unicast = mock(IUnicastConnection.class);
        when(unicast.getCloseFuture_()).thenReturn(_unicastCloseFuture);

        _connection = PeerConnection.getInstance_(new ImmediateInlineExecutor(), unicast);
        _connection.setPipeline_(_pipeline);
    }

    @Test
    public void shouldSetCloseFutureToExceptionWhenCloseFutureOfUnicastConnectionDependedOnIsTriggered()
    {
        assertFalse(_connection.getCloseFuture_().isDone());

        final ExTransport CLOSE_FUTURE_EXCEPTION = new ExTransport("shutting down");
        _unicastCloseFuture.setException(CLOSE_FUTURE_EXCEPTION);

        assertTrue(_connection.getCloseFuture_().isDone());

        assertEquals(CLOSE_FUTURE_EXCEPTION, getFutureThrowable(_connection.getCloseFuture_()));
    }

    @Test
    public void shouldReturnImmeditatelyTriggeredFutureWithExceptionWhenSendIsCalledAfterTheUnderlyingUnicastConnectionHasClosed()
    {
        _unicastCloseFuture.setException(new ExTransport("shutting down"));

        ListenableFuture<Void> sendFuture = _connection.send_(new Object(), LO);
        assertTrue(sendFuture.isDone());

        assertNotNull(getFutureThrowable(sendFuture));
    }

    @Test
    public void shouldReturnImmeditatelyTriggeredFutureWithExceptionWhenConnectIsCalledAfterTheUnderlyingUnicastConnectionHasClosed()
    {
        _unicastCloseFuture.setException(new ExTransport("shutting down"));

        ListenableFuture<Void> connectFuture = _connection.connect_();
        assertTrue(connectFuture.isDone());

        assertNotNull(getFutureThrowable(connectFuture));
    }

    @Test
    public void shouldReturnImmeditatelyTriggeredFutureWithExceptionWhenDisconnectIsCalledAfterTheUnderlyingUnicastConnectionHasClosed()
    {
        _unicastCloseFuture.setException(new ExTransport("shutting down"));

        ListenableFuture<Void> disconnectFuture = _connection.disconnect_(
                new ExTransport("Owner triggers disconnect"));
        assertTrue(disconnectFuture.isDone());

        assertNotNull(getFutureThrowable(disconnectFuture));
    }

    @Test
    public void shouldSendConnectionEventWithTypeSetToCONNECTAndWithCorrectParametersDownPipelineWhenConnectCalled()
    {
        ListenableFuture<Void> connectFuture = _connection.connect_();

        verify(_pipeline).processOutgoing_(_eventCaptor.capture());

        ConnectionEvent event = (ConnectionEvent) _eventCaptor.getValue();

        assertEquals(CONNECT, event.getType_());
        assertEquals(_connection, event.getConnection_());
        assertNull(event.getException_());

        assertCompletionFutureChainedProperly(event.getCompletionFuture_(), connectFuture);
    }

    @Test
    public void shouldSetCloseFutureWithExceptionIfConnectCallFails()
    {
        _connection.connect_();

        // grab the _settable_ connect future

        verify(_pipeline).processOutgoing_(_eventCaptor.capture());
        ConnectionEvent event = (ConnectionEvent) _eventCaptor.getValue();

        assertFalse(_connection.getCloseFuture_().isDone());

        // simulate a failure

        final ExTransport CONNECT_EXCEPTION = new ExTransport("fail");
        event.getCompletionFuture_().setException(CONNECT_EXCEPTION);

        assertTrue(_connection.getCloseFuture_().isDone());

        ExTransport ex = (ExTransport) getFutureThrowable(_connection.getCloseFuture_());
        assertEquals(CONNECT_EXCEPTION, ex);
    }

    @Test
    public void shouldSendConnectionEventWithTypeSetToDISCONNECTDownPipelineAndWithCorrectParametersWhenDisconnectCalled()
    {
        final ExTransport DISCONNECT_EXCEPTION = new ExTransport("shutting down");
        ListenableFuture<Void> disconnectFuture = _connection.disconnect_(DISCONNECT_EXCEPTION);

        verify(_pipeline).processOutgoing_(_eventCaptor.capture());

        ConnectionEvent event = (ConnectionEvent) _eventCaptor.getValue();

        assertEquals(DISCONNECT, event.getType_());
        assertEquals(DISCONNECT_EXCEPTION, event.getException_());
        assertEquals(_connection, event.getConnection_());

        assertTrue(disconnectFuture.isDone());
        ExTransport ex = (ExTransport) getFutureThrowable(disconnectFuture);
        assertEquals(DISCONNECT_EXCEPTION, ex);
    }

    @Test
    public void shouldSendMessageEventAndWithCorrectParametersDownPipelineWhenSendCalled()
    {
        final Object SEND_OBJECT = new Object();
        final Prio SEND_PRIO = HI;
        ListenableFuture<Void> sendFuture = _connection.send_(SEND_OBJECT, SEND_PRIO);

        verify(_pipeline).processOutgoing_(_eventCaptor.capture());

        MessageEvent event = (MessageEvent) _eventCaptor.getValue();

        assertEquals(SEND_OBJECT, event.getMessage_());
        assertEquals(SEND_PRIO, event.getPriority_());
        assertEquals(_connection, event.getConnection_());

        assertCompletionFutureChainedProperly(event.getCompletionFuture_(), sendFuture);
    }

    @Test
    // FIXME: this is really testing implementation - what happens if 2nd call ignored in future?
    public void shouldAllowCallerToOnlyStartTheReceiveLoopOnce()
    {
        _connection.startReceiveLoop_();

        try {
            _connection.startReceiveLoop_();
            fail("second call expected to fail");
        } catch (AssertionError ae) {
            assertTrue("second call expected to fail", true);
        }
    }

    @Test
    public void shouldNotBeAbleToStartReceiveLoopIfCloseFutureHasBeenSet()
    {
        _unicastCloseFuture.setException(new ExTransport("shutdown")); // should kill connection
        _connection.startReceiveLoop_();

        verifyZeroInteractions(_pipeline); // starting receive loop sends event down pipeline
    }

    @Test
    public void shouldSendReadEventDownPipelineWhenReceiveLoopStarted()
    {
        _connection.startReceiveLoop_();

        verify(_pipeline).processOutgoing_(_eventCaptor.capture());

        ReadEvent event = (ReadEvent) _eventCaptor.getValue();
        assertEquals(_connection, event.getConnection_());
    }

    @Test
    // IMPORTANT: implies that receive callback is set up properly
    public void shouldPlaceReceivedDataIntoThePipelineWhenReceiveCallSucceeds()
    {
        // grab the future associated with the read event

        final OutArg<UncancellableFuture<ImmutableList<WireData>>> dataRef = new OutArg<UncancellableFuture<ImmutableList<WireData>>>(
                null);

        final Answer<Void> RECV_ANSWER_0 = new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                ReadEvent event = (ReadEvent) invocation.getArguments()[0];
                dataRef.set(event.getCompletionFuture_());
                return null;
            }
        };

        final Answer<Void> RECV_ANSWER_1 = new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                return null;
            }
        };

        doAnswer(RECV_ANSWER_0).doAnswer(RECV_ANSWER_1)
                .when(_pipeline)
                .processOutgoing_(any(IPipelineEvent.class));

        final List<MessageEvent> messageEvents = new ArrayList<MessageEvent>(2);

        final Answer<Void> PROCESS_RECV_DATA_ANSWER = new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                messageEvents.add((MessageEvent) invocation.getArguments()[0]);
                return null;
            }
        };

        doAnswer(PROCESS_RECV_DATA_ANSWER).when(_pipeline).processIncoming_(any(IPipelineEvent
                .class));

        _connection.startReceiveLoop_();

        final WireData DATA_0 = new WireData(new byte[]{0}, 1);
        final WireData DATA_1 = new WireData(new byte[]{1}, 1);
        final ImmutableList<WireData> DATA_LIST = ImmutableList.of(DATA_0, DATA_1);

        // we've received data!

        dataRef.get().set(DATA_LIST);

        assertEquals(DATA_LIST.size(), messageEvents.size());

        for (int i = 0; i < DATA_LIST.size(); i++) {
            MessageEvent event = messageEvents.get(0);
            WireData data = DATA_LIST.get(0);
            assertEquals(data, event.getMessage_());
            assertEquals(LO, event.getPriority_());
            assertEquals(_connection, event.getConnection_());
        }
    }

    @Test
    public void shouldScheduleAnotherRecvCallWhenDataIsReceivedFromTheUnderlyingUnicastConnection()
    {
        final OutArg<UncancellableFuture<ImmutableList<WireData>>> dataRef = new OutArg<UncancellableFuture<ImmutableList<WireData>>>(
                null);

        final Answer<Void> GRAB_RECEIVE_COMPLETION_FUTURE = new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                ReadEvent event = (ReadEvent) invocation.getArguments()[0];
                dataRef.set(event.getCompletionFuture_());
                return null;
            }
        };

        final OutArg<Boolean> secondReceiveCallRef = new OutArg<Boolean>(false);

        final Answer<Void> TRIP_SECOND_RECEIVE_CALL_FLAG = new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                secondReceiveCallRef.set(true);
                return null;
            }
        };

        doAnswer(GRAB_RECEIVE_COMPLETION_FUTURE).doAnswer(TRIP_SECOND_RECEIVE_CALL_FLAG)
                .when(_pipeline)
                .processOutgoing_(any(IPipelineEvent.class));

        _connection.startReceiveLoop_();

        assertFalse(secondReceiveCallRef.get()); // we should not have triggered the second yet

        dataRef.get().set(ImmutableList.<WireData>of()); // should trigger the second receive call

        assertTrue(secondReceiveCallRef.get());
    }

    @Test
    public void shouldDisconnectTheUnderlyingUnicastConnectionIfReceiveCallFails()
    {
        // need to grab the completion future from the 1st call
        // need to set an exception
        // need to verify that we send down a disconnect event on the 2nd call

        final OutArg<UncancellableFuture<ImmutableList<WireData>>> dataRef = new OutArg<UncancellableFuture<ImmutableList<WireData>>>(
                null);

        final Answer<Void> GRAB_RECEIVE_COMPLETION_FUTURE = new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                ReadEvent event = (ReadEvent) invocation.getArguments()[0];
                dataRef.set(event.getCompletionFuture_());
                return null;
            }
        };

        final OutArg<ConnectionEvent> eventRef = new OutArg<ConnectionEvent>(null);

        final Answer<Void> GRAB_DISCONNECT_EVENT = new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                ConnectionEvent event = (ConnectionEvent) invocation.getArguments()[0];
                eventRef.set(event);
                return null;
            }
        };

        doAnswer(GRAB_RECEIVE_COMPLETION_FUTURE).doAnswer(GRAB_DISCONNECT_EVENT)
                .when(_pipeline)
                .processOutgoing_(any(IPipelineEvent.class));

        _connection.startReceiveLoop_();

        final ExTransport RECEIVE_EXCEPTION = new ExTransport("receive failed");
        dataRef.get().setException(RECEIVE_EXCEPTION);

        assertEquals(DISCONNECT, eventRef.get().getType_());
        assertEquals(RECEIVE_EXCEPTION, eventRef.get().getException_());
        assertEquals(_connection, eventRef.get().getConnection_());
    }
}