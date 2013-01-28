/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.tng.IDefectReporter;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.testlib.AbstractTest;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class TestSendFailureHandler extends AbstractTest
{
    private final IDefectReporter _defectReporter = mock(IDefectReporter.class);
    private final IPipelineContext _ctx = mock(IPipelineContext.class);
    private final IConnection _connection = mock(IConnection.class);
    private final MessageEvent _event = new MessageEvent(_connection, UncancellableFuture.<Void>create(), null, Prio.HI);
    private final SendFailureHandler _handler = new SendFailureHandler(_defectReporter);

    @Test
    public void shouldSendADefectWhenSendingAnOutgoingPacketFails()
            throws Exception
    {
        Throwable sendFailedThrowable = new Throwable("send failed");

        _handler.onOutgoingMessageEvent_(_ctx, _event);
        _event.getCompletionFuture_().setException(sendFailedThrowable);

        verify(_defectReporter).reportDefect(any(String.class), eq(sendFailedThrowable));
    }

    @Test
    public void shouldNotSendADefectWhenSendingAnOutgoingPacketSucceeds()
            throws Exception
    {
        _handler.onOutgoingMessageEvent_(_ctx, _event);
        _event.getCompletionFuture_().set(null);

        verifyZeroInteractions(_defectReporter);
    }
}