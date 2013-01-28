/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap.filter;

import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.ImmediateInlineExecutor;
import com.aerofs.daemon.tng.base.MessageEvent;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import static com.aerofs.testlib.FutureAssert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TestMessageFilter extends AbstractTest
{
    MessageFilter filter;
    ISingleThreadedPrioritizedExecutor executor;

    @Mock IMessageFilterListener listener;
    @Mock IPipelineContext pipelineContext;
    @Mock IConnection connection;

    @Captor ArgumentCaptor<MessageFilterRequest> messageFilterRequestCaptor;

    @Before
    public void setUp() throws Exception
    {
        executor = new ImmediateInlineExecutor();
        filter = new MessageFilter(executor, listener, executor);
    }

    @Test
    public void shouldForwardAllNonIncomingMessageEvents() throws Exception
    {
        IPipelineEvent<?> event = mock(IPipelineEvent.class);

        filter.onIncoming_(pipelineContext, event);

        verify(pipelineContext).sendIncomingEvent_(event);
    }

    @Test
    public void shouldForwardAllNonOutgoingMessageEvents() throws Exception
    {
        IPipelineEvent<?> event = mock(IPipelineEvent.class);

        filter.onOutgoing_(pipelineContext, event);

        verify(pipelineContext).sendOutgoingEvent_(event);
    }

    MessageEvent buildMessageEvent(Object message)
    {
        return new MessageEvent(connection, UncancellableFuture.<Void>create(), message, Prio.LO);
    }

    @Test
    public void shouldCallListenerOnIncomingMessageEventAndAllow() throws Exception
    {
        final String EXPECTED_MESSAGE = "hey";
        MessageEvent event = buildMessageEvent(EXPECTED_MESSAGE);

        filter.onIncoming_(pipelineContext, event);

        verify(listener).onIncomingMessageReceived_(messageFilterRequestCaptor.capture());
        assertEquals(EXPECTED_MESSAGE, messageFilterRequestCaptor.getValue().message);

        verify(pipelineContext, never()).sendIncomingEvent_(event);

        messageFilterRequestCaptor.getValue().allow();

        verify(pipelineContext).sendIncomingEvent_(event);
    }

    @Test
    public void shouldCallListenerOnOutgoingMessageEventAndAllow() throws Exception
    {
        final String EXPECTED_MESSAGE = "hey";
        MessageEvent event = buildMessageEvent(EXPECTED_MESSAGE);

        filter.onOutgoing_(pipelineContext, event);

        verify(listener).onOutgoingMessageReceived_(messageFilterRequestCaptor.capture());
        assertEquals(EXPECTED_MESSAGE, messageFilterRequestCaptor.getValue().message);

        verify(pipelineContext, never()).sendOutgoingEvent_(event);

        messageFilterRequestCaptor.getValue().allow();

        verify(pipelineContext).sendOutgoingEvent_(event);
    }

    @Test
    public void shouldCallListenerOnIncomingMessageEventAndDeny() throws Exception
    {
        final String EXPECTED_MESSAGE = "hey";
        MessageEvent event = buildMessageEvent(EXPECTED_MESSAGE);

        filter.onIncoming_(pipelineContext, event);

        verify(listener).onIncomingMessageReceived_(messageFilterRequestCaptor.capture());
        assertEquals(EXPECTED_MESSAGE, messageFilterRequestCaptor.getValue().message);

        verify(pipelineContext, never()).sendIncomingEvent_(event);

        messageFilterRequestCaptor.getValue().deny();

        assertThrows(event.getCompletionFuture_(), Exception.class);

        verify(pipelineContext, never()).sendIncomingEvent_(event);
    }

    @Test
    public void shouldCallListenerOnOutgoingMessageEventAndDeny() throws Exception
    {
        final String EXPECTED_MESSAGE = "hey";
        MessageEvent event = buildMessageEvent(EXPECTED_MESSAGE);

        filter.onOutgoing_(pipelineContext, event);

        verify(listener).onOutgoingMessageReceived_(messageFilterRequestCaptor.capture());
        assertEquals(EXPECTED_MESSAGE, messageFilterRequestCaptor.getValue().message);

        verify(pipelineContext, never()).sendOutgoingEvent_(event);

        messageFilterRequestCaptor.getValue().deny();

        assertThrows(event.getCompletionFuture_(), Exception.class);

        verify(pipelineContext, never()).sendOutgoingEvent_(event);
    }
}
