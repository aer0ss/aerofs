/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.tng.base.pipeline.*;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.List;

import static com.aerofs.testlib.FutureAssert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static com.aerofs.testlib.FutureAssert.getFutureThrowable;
import static org.junit.Assert.*;

public class TestSimplePipeline extends AbstractTest
{
    private final DID _did = new DID(DID.ZERO);

    @Captor private ArgumentCaptor<IPipelineEvent<?>> _eventCaptor; // makes my life easier
    @Mock private IPipelineEventSink _unicastSink;
    @Mock private IConnection _connection;
    @Mock private IStateContainer _stateContainer;

    private SimplePipeline createEmptyPipeline_()
    {
        IPipelineBuilder builder = SimplePipeline.builder_(_did, _stateContainer, _connection, _unicastSink);
        return (SimplePipeline) builder.build_();
    }

    @Test
    public void shouldConstructPipelineWithAllHandlersInCorrectOrder()
    {
        IPipelineBuilder builder = SimplePipeline.builder_(_did, _stateContainer, _connection, _unicastSink);

        final int NUM_HANDLERS = 5;
        for (int i = 0; i < NUM_HANDLERS; i++) {
            builder.addLast_(new MockIdentifiedForwardingIncomingHandler(i));
        }

        builder.addFirst_(new MockIdentifiedForwardingIncomingHandler(NUM_HANDLERS));

        SimplePipeline pipeline = (SimplePipeline) builder.build_();

        MockIdentifiedForwardingIncomingHandler handler;

        ImmutableList<IPipelineEventHandler> forward = pipeline.getHandlersInForwardOrder_();
        handler = (MockIdentifiedForwardingIncomingHandler) forward.get(0);
        assertEquals(NUM_HANDLERS, handler.getId_());
        for (int i = 0; i < NUM_HANDLERS; i++) {
            handler = (MockIdentifiedForwardingIncomingHandler) forward.get(i + 1);
            assertEquals(i, handler.getId_());
        }

        ImmutableList<IPipelineEventHandler> reverse = pipeline.getHandlersInReverseOrder_();
        for (int i = 0; i < NUM_HANDLERS; i++) {
            handler = (MockIdentifiedForwardingIncomingHandler) reverse.get(NUM_HANDLERS - 1 - i);
            assertEquals(i, handler.getId_());
        }

        handler = (MockIdentifiedForwardingIncomingHandler) reverse.get(NUM_HANDLERS);
        assertEquals(NUM_HANDLERS, handler.getId_());
    }

    @Test
    public void shouldImmediatelyDropEventAndSetExceptionIfThereAreNoIncomingHandlersInThePipeline()
    {
        SimplePipeline pipeline = createEmptyPipeline_();

        MockEvent event = new MockEvent(_connection);
        pipeline.processIncoming_(event);

        assertTrue(event.getCompletionFuture_().isDone());
        assertThrows(event.getCompletionFuture_(), ExTransport.class);
    }

    @Test
    public void shouldOnlySendToIncomingHandlersForIncomingPipelineEvents()
    {
        IPipelineBuilder builder = SimplePipeline.builder_(_did, _stateContainer, _connection, _unicastSink);

        builder.addLast_(new MockIdentifiedForwardingIncomingHandler(1));
        builder.addLast_(new MockIdentifiedForwardingOutgoingHandler(2));
        builder.addLast_(new MockIdentifiedForwardingDualHandler(3));
        builder.addLast_(new MockIdentifiedForwardingOutgoingHandler(4));

        // expect to see only 1 and 3 (don't consider ordering right now)

        SimplePipeline pipeline = (SimplePipeline) builder.build_();

        TrippedHandlerEvent event = new TrippedHandlerEvent(_connection);
        pipeline.processIncoming_(event);

        assertEquals(2, event.getTrippedHandlerIds_().size());

        assertTrue(event.getTrippedHandlerIds_().contains(1));
        assertTrue(event.getTrippedHandlerIds_().contains(3));
    }

    @Test
    public void shouldStartIncomingEventProcessingAtTheFirstIncomingHandlerInThePipeline()
    {
        IPipelineBuilder builder = SimplePipeline.builder_(_did, _stateContainer, _connection, _unicastSink);

        builder.addLast_(new MockIdentifiedForwardingOutgoingHandler(1));
        builder.addLast_(new MockIdentifiedForwardingIncomingHandler(2));
        builder.addLast_(new MockIdentifiedForwardingIncomingHandler(3));
        builder.addLast_(new MockIdentifiedForwardingOutgoingHandler(4));

        SimplePipeline pipeline = (SimplePipeline) builder.build_();

        TrippedHandlerEvent event = new TrippedHandlerEvent(_connection);
        pipeline.processIncoming_(event);

        assertEquals(2, event.getTrippedHandlerIds_().size());
        assertEquals(2, event.getTrippedHandlerIds_().get(0).intValue());
    }

    @Test
    public void shouldSendToAllIncomingHandlersInOrderWhenAnIncomingPipelineEventIsReceived()
    {
        IPipelineBuilder builder = SimplePipeline.builder_(_did, _stateContainer, _connection, _unicastSink);

        builder.addLast_(new MockIdentifiedForwardingIncomingHandler(1));
        builder.addLast_(new MockIdentifiedForwardingOutgoingHandler(2));
        builder.addLast_(new MockIdentifiedForwardingIncomingHandler(3));
        builder.addLast_(new MockIdentifiedForwardingIncomingHandler(4));
        builder.addLast_(new MockIdentifiedForwardingOutgoingHandler(5));
        builder.addLast_(new MockIdentifiedForwardingDualHandler(6));

        final int[] EXPECTED_CALLED_HANDLERS = new int[]{1, 3, 4, 6};

        SimplePipeline pipeline = (SimplePipeline) builder.build_();

        TrippedHandlerEvent event = new TrippedHandlerEvent(_connection);
        pipeline.processIncoming_(event);

        List<Integer> trippedHandlerIds = event.getTrippedHandlerIds_();

        assertEquals(EXPECTED_CALLED_HANDLERS.length, trippedHandlerIds.size());

        for (int i = 0; i < EXPECTED_CALLED_HANDLERS.length; i++) {
            assertEquals(EXPECTED_CALLED_HANDLERS[i], trippedHandlerIds.get(i).intValue());
        }
    }

    @Test
    public void shouldCreateExceptionEventWithItsCompletionFutureChainedToOriginalEventAndSendToOutgoingEventSinkWhenIncomingHandlerThrowsExceptionProcessingTheOriginalEvent()
            throws Exception
    {
        final ExTransport HANDLER_EXCEPTION = new ExTransport("handler failed");

        // handler that'll throw an exception

        IIncomingPipelineEventHandler throwingHandler = mock(IIncomingPipelineEventHandler.class);
        doThrow(HANDLER_EXCEPTION).when(throwingHandler)
                .onIncoming_(any(IPipelineContext.class), any(IPipelineEvent.class));

        IPipelineBuilder builder = SimplePipeline.builder_(_did, _stateContainer, _connection, _unicastSink);
        builder.addLast_(new MockIdentifiedForwardingDualHandler(0));
        builder.addLast_(throwingHandler);
        SimplePipeline pipeline = (SimplePipeline) builder.build_();

        // event that's being processed
        final MockEvent event = new MockEvent(_connection);
        final UncancellableFuture<Void> throwingEventFuture = event.getCompletionFuture_();

        // run it through the pipeline

        pipeline.processIncoming_(event);

        // grab the resulting event and check it

        verify(_unicastSink).processSunkEvent_(_eventCaptor.capture());

        assertTrue(_eventCaptor.getValue() instanceof ExceptionEvent<?>);

        ExceptionEvent<?> exceptionEvent = (ExceptionEvent<?>) _eventCaptor.getValue();
        assertEquals(HANDLER_EXCEPTION, exceptionEvent.getException_());

        assertEquals(throwingEventFuture, exceptionEvent.getCompletionFuture_());
    }

    @Test
    public void shouldNotContinueSendingIncomingEventsAlongThePipelineUnlessAHandlerAsks()
    {
        IPipelineBuilder builder = SimplePipeline.builder_(_did, _stateContainer, _connection, _unicastSink);

        // all handlers should support outgoing

        builder.addLast_(new MockIdentifiedForwardingDualHandler(1));
        builder.addLast_(new MockIdentifiedStoppingHandler(2));
        builder.addLast_(new MockIdentifiedForwardingIncomingHandler(3));

        SimplePipeline pipeline = (SimplePipeline) builder.build_();

        TrippedHandlerEvent event = new TrippedHandlerEvent(_connection);
        pipeline.processIncoming_(event);

        assertEquals(2, event.getTrippedHandlerIds_().size());
        assertFalse(event.getTrippedHandlerIds_().contains(3));
    }

    @Test
    public void shouldDropEventAndSetExceptionIfIncomingEventReachesTheEndOfTheIncomingHandlerChain()
    {
        IPipelineBuilder builder = SimplePipeline.builder_(_did, _stateContainer, _connection, _unicastSink);

        builder.addLast_(new MockIdentifiedForwardingDualHandler(0));

        SimplePipeline pipeline = (SimplePipeline) builder.build_();

        MockEvent event = new MockEvent(_connection);
        pipeline.processIncoming_(event);

        assertTrue(event.getCompletionFuture_().isDone());
        assertNotNull(getFutureThrowable(event.getCompletionFuture_()));
    }

    @Test
    public void shouldImmediatelySendPipelineEventToUnicastIfThereAreNoOutgoingHandlersInThePipeline()
    {
        SimplePipeline pipeline = createEmptyPipeline_();

        MockEvent event = new MockEvent(_connection);
        pipeline.processOutgoing_(event);

        verify(_unicastSink).processSunkEvent_(event);
    }

    @Test
    public void shouldOnlySendToOutgoingHandlersForOutgoingPipelineEvents()
    {
        IPipelineBuilder builder = SimplePipeline.builder_(_did, _stateContainer, _connection, _unicastSink);

        builder.addLast_(new MockIdentifiedForwardingDualHandler(1));
        builder.addLast_(new MockIdentifiedForwardingIncomingHandler(2));
        builder.addLast_(new MockIdentifiedForwardingOutgoingHandler(3));
        builder.addLast_(new MockIdentifiedForwardingIncomingHandler(4));

        // expect to see only 1,3 (don't consider ordering, b/c right ordering is 3,1)

        SimplePipeline pipeline = (SimplePipeline) builder.build_();

        TrippedHandlerEvent event = new TrippedHandlerEvent(_connection);
        pipeline.processOutgoing_(event);

        assertEquals(2, event.getTrippedHandlerIds_().size());

        assertTrue(event.getTrippedHandlerIds_().contains(1));
        assertTrue(event.getTrippedHandlerIds_().contains(3));
    }

    @Test
    public void shouldStartOutgoingEventProcessingAtTheLastOutgoingHandlerInThePipeline()
    {
        IPipelineBuilder builder = SimplePipeline.builder_(_did, _stateContainer, _connection, _unicastSink);

        builder.addLast_(new MockIdentifiedForwardingIncomingHandler(1));
        builder.addLast_(new MockIdentifiedForwardingOutgoingHandler(2));
        builder.addLast_(new MockIdentifiedForwardingOutgoingHandler(3));
        builder.addLast_(new MockIdentifiedForwardingIncomingHandler(4));

        SimplePipeline pipeline = (SimplePipeline) builder.build_();

        TrippedHandlerEvent event = new TrippedHandlerEvent(_connection);
        pipeline.processOutgoing_(event);

        assertEquals(2, event.getTrippedHandlerIds_().size());
        assertEquals(3, event.getTrippedHandlerIds_().get(0).intValue());
    }

    @Test
    public void shouldSendToAllOutgoingHandlersInOrderWhenAnOutgoingPipelineEventIsReceived()
    {
        IPipelineBuilder builder = SimplePipeline.builder_(_did, _stateContainer, _connection, _unicastSink);

        builder.addLast_(new MockIdentifiedForwardingOutgoingHandler(1));
        builder.addLast_(new MockIdentifiedForwardingOutgoingHandler(2));
        builder.addLast_(new MockIdentifiedForwardingIncomingHandler(3));
        builder.addLast_(new MockIdentifiedForwardingIncomingHandler(4));
        builder.addLast_(new MockIdentifiedForwardingDualHandler(5));
        builder.addLast_(new MockIdentifiedForwardingOutgoingHandler(6));

        final int[] EXPECTED_CALLED_HANDLERS = new int[]{6, 5, 2, 1};

        SimplePipeline pipeline = (SimplePipeline) builder.build_();

        TrippedHandlerEvent event = new TrippedHandlerEvent(_connection);
        pipeline.processOutgoing_(event);

        List<Integer> trippedHandlerIds = event.getTrippedHandlerIds_();

        assertEquals(EXPECTED_CALLED_HANDLERS.length, trippedHandlerIds.size());

        for (int i = 0; i < EXPECTED_CALLED_HANDLERS.length; i++) {
            assertEquals(EXPECTED_CALLED_HANDLERS[i], trippedHandlerIds.get(i).intValue());
        }

    }

    @Test
    public void shouldCreateExceptionEventWithItsCompletionFutureChainedToOriginalEventAndSendToOutgoingEventSinkWhenOutgoingHandlerThrowsExceptionProcessingTheOriginalEvent()
            throws Exception
    {
        final ExTransport HANDLER_EXCEPTION = new ExTransport("handler failed");

        // handler that'll throw an exception

        IOutgoingPipelineEventHandler throwingHandler = mock(IOutgoingPipelineEventHandler.class);
        doThrow(HANDLER_EXCEPTION).when(throwingHandler)
                .onOutgoing_(any(IPipelineContext.class), any(IPipelineEvent.class));

        IPipelineBuilder builder = SimplePipeline.builder_(_did, _stateContainer, _connection, _unicastSink);
        builder.addLast_(new MockIdentifiedForwardingDualHandler(0));
        builder.addLast_(throwingHandler);
        SimplePipeline pipeline = (SimplePipeline) builder.build_();

        // event that's being processed
        final MockEvent event = new MockEvent(_connection);
        final UncancellableFuture<Void> throwingEventFuture = event.getCompletionFuture_();

        // run it through the pipeline

        pipeline.processOutgoing_(event);

        // grab the resulting event and check it

        verify(_unicastSink).processSunkEvent_(_eventCaptor.capture());

        assertTrue(_eventCaptor.getValue() instanceof ExceptionEvent<?>);

        ExceptionEvent<?> exceptionEvent = (ExceptionEvent<?>) _eventCaptor.getValue();
        assertEquals(HANDLER_EXCEPTION, exceptionEvent.getException_());

        assertEquals(throwingEventFuture, exceptionEvent.getCompletionFuture_());
    }

    @Test
    public void shouldNotContinueSendingOutgoingEventsAlongThePipelineUnlessAHandlerAsks()
    {
        IPipelineBuilder builder = SimplePipeline.builder_(_did, _stateContainer, _connection, _unicastSink);

        // all handlers should support outgoing

        builder.addLast_(new MockIdentifiedForwardingOutgoingHandler(1));
        builder.addLast_(new MockIdentifiedStoppingHandler(2));
        builder.addLast_(new MockIdentifiedForwardingDualHandler(3));

        SimplePipeline pipeline = (SimplePipeline) builder.build_();

        TrippedHandlerEvent event = new TrippedHandlerEvent(_connection);
        pipeline.processOutgoing_(event);

        assertEquals(2, event.getTrippedHandlerIds_().size());
        assertFalse(event.getTrippedHandlerIds_().contains(1));
    }
}
