/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.IDefectReporter;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.base.async.FailedFutureCallback;

import static com.google.common.util.concurrent.Futures.addCallback;

public final class SendFailureHandler extends SimplePipelineEventHandler
{
    private final IDefectReporter _defectReporter;

    public SendFailureHandler(IDefectReporter defectReporter)
    {
        this._defectReporter = defectReporter;
    }

    @Override
    protected void onOutgoingMessageEvent_(IPipelineContext ctx, MessageEvent messageEvent)
            throws Exception
    {
        final Object message = messageEvent.getMessage_();

        addCallback(messageEvent.getCompletionFuture_(), new FailedFutureCallback()
        {
            @Override
            public void onFailure(Throwable t)
            {
                _defectReporter.reportDefect("fail send msg:" + message, t);
            }
        }); // doesn't matter what executor this runs on; defect reporting is thread-safe

        super.onOutgoingMessageEvent_(ctx, messageEvent);
    }
}
