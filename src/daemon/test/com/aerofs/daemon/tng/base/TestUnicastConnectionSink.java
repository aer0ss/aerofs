/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import static com.aerofs.daemon.lib.Prio.LO;
import static com.aerofs.daemon.tng.base.ConnectionEvent.Type.CONNECT;
import static com.aerofs.daemon.tng.base.ConnectionEvent.Type.DISCONNECT;
import static com.aerofs.testlib.FutureAssert.assertCompletionFutureChainedProperly;
import static com.aerofs.testlib.FutureAssert.getFutureThrowable;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TestUnicastConnectionSink extends AbstractTest
{
    private final IUnicastConnection _unicast = mock(IUnicastConnection.class);
    private final IConnection _connection = mock(IConnection.class);
    private final UnicastConnectionSink _unicastSink = new UnicastConnectionSink(_unicast);

    @Test
    public void shouldCallUnicastConnectWhenReceivingConnectionEventWithCONNECTType()
    {
        UncancellableFuture<Void> connectFuture = UncancellableFuture.create();
        when(_unicast.connect_()).thenReturn(connectFuture);

        ConnectionEvent event = new ConnectionEvent(_connection, CONNECT, null);

        _unicastSink.processSunkEvent_(event);

        verify(_unicast).connect_();

        assertCompletionFutureChainedProperly(connectFuture, event.getCompletionFuture_());
    }

    @Test
    public void shouldCallUnicastDisconnectWhenReceivingConnectionEventWithDISCONNECTType()
    {
        UncancellableFuture<Void> disconnectFuture = UncancellableFuture.create();
        when(_unicast.disconnect_(any(Exception.class))).thenReturn(disconnectFuture);

        final ExTransport DISCONNECT_EXCEPTION = new ExTransport("explicit destruction");
        ConnectionEvent event = new ConnectionEvent(_connection, DISCONNECT, DISCONNECT_EXCEPTION);

        _unicastSink.processSunkEvent_(event);

        verify(_unicast).disconnect_(eq(DISCONNECT_EXCEPTION));

        assertCompletionFutureChainedProperly(disconnectFuture, event.getCompletionFuture_());
    }

    @Test
    public void shouldCallUnicastSendWhenReceivingMessageEventContainingAnArrayOfByteArrays()
    {
        UncancellableFuture<Void> sendFuture = UncancellableFuture.create();
        when(_unicast.send_(any(byte[][].class), any(Prio.class))).thenReturn(sendFuture);

        final byte[][] SERIALIZED = new byte[][]{new byte[]{0}, /* empty */};
        final Prio PRI = LO;
        UncancellableFuture<Void> completionFuture = UncancellableFuture.create();
        MessageEvent message = new MessageEvent(_connection, completionFuture, SERIALIZED, PRI);

        _unicastSink.processSunkEvent_(message);

        verify(_unicast).send_(eq(SERIALIZED), eq(PRI));

        assertCompletionFutureChainedProperly(sendFuture, message.getCompletionFuture_());
    }

    @Test
    public void shouldSetExceptionFotTheEventCompletionFutureIfMessageEventDoesNotContainArryOfByteArryays()
    {
        UncancellableFuture<Void> completionFuture = UncancellableFuture.create();
        MessageEvent message = new MessageEvent(_connection, completionFuture, new Object(), LO);

        _unicastSink.processSunkEvent_(message);

        assertTrue(completionFuture.isDone());

        ExTransport ex = (ExTransport) getFutureThrowable(completionFuture);
        assertNotNull(ex);
    }

    @Test
    public void shouldCallUnicastReceiveWhenReceivingReadEvent()
    {
        UncancellableFuture<ImmutableList<WireData>> receiveFuture = UncancellableFuture.create();
        when(_unicast.receive_()).thenReturn(receiveFuture);

        ReadEvent event = new ReadEvent(_connection);

        _unicastSink.processSunkEvent_(event);

        verify(_unicast).receive_();

        assertCompletionFutureChainedProperly(receiveFuture, event.getCompletionFuture_());
    }

    @Test
    public void shouldSetExceptionForTheEventCompletionFutureIfTheEventIsNotARecognizedEventType()
    {
        final UncancellableFuture<Void> completionFuture = UncancellableFuture.create();
        final IPipelineEvent<Void> UNRECOGNIZED_EVENT = new IPipelineEvent<Void>()
        {
            @Override
            public UncancellableFuture<Void> getCompletionFuture_()
            {
                return completionFuture;
            }

            @Override
            public IConnection getConnection_()
            {
                return _connection;
            }
        };

        _unicastSink.processSunkEvent_(UNRECOGNIZED_EVENT);

        assertTrue(completionFuture.isDone());

        try {
            completionFuture.get();
            fail("should not return a value");
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void shouldDisconnectUnicastWhenHandlerThrowsAnException()
    {
        when(_unicast.disconnect_(any(Exception.class))).thenReturn(
                UncancellableFuture.<Void>create());

        UncancellableFuture<Void> completionFuture = UncancellableFuture.create();
        final Exception HANDLER_EXCEPTION = new ExTransport("handler threw exception");
        final ExceptionEvent<Void> event = ExceptionEvent.getInstance_(completionFuture,
                _connection, HANDLER_EXCEPTION);

        _unicastSink.processSunkEvent_(event);

        verify(_unicast).disconnect_(HANDLER_EXCEPTION);

        assertTrue(completionFuture.isDone());

        ExTransport ex = (ExTransport) getFutureThrowable(completionFuture);
        assertEquals(HANDLER_EXCEPTION, ex);
    }
}
