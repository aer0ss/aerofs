/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEventSink;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.base.async.UncancellableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;

final class UnicastConnectionSink implements IPipelineEventSink
{
    private static final Logger l = Loggers.getLogger(UnicastConnectionSink.class);

    private final IUnicastConnection _unicast;

    UnicastConnectionSink(IUnicastConnection unicast)
    {
        this._unicast = unicast;
    }

    @Override
    public void processSunkEvent_(IPipelineEvent<?> event)
    {
        assert event != null;

        if (event instanceof ConnectionEvent) {
            handleConnectionEvent_((ConnectionEvent) event);
        } else if (event instanceof MessageEvent) {
            handleSendEvent_((MessageEvent) event);
        } else if (event instanceof ReadEvent) {
            handleReadEvent_((ReadEvent) event);
        } else if (event instanceof ExceptionEvent<?>) {
            handleExceptionEvent_((ExceptionEvent<?>) event);
        } else {
            handleUnrecognizedEvent_(event);
        }
    }

    private void handleConnectionEvent_(ConnectionEvent event)
    {
        ListenableFuture<Void> operationFuture;
        switch (event.getType_()) {
        case CONNECT:
            operationFuture = _unicast.connect_();
            break;
        case DISCONNECT:
            operationFuture = _unicast.disconnect_(event.getException_());
            break;
        default:
            l.error("bad connection event type");
            operationFuture = UncancellableFuture.createFailed(
                    new ExTransport("unexpected connection event type" + event.getType_()));
            break;
        }

        event.getCompletionFuture_().chain(operationFuture);
    }

    private void handleUnrecognized_(String errorMessage, UncancellableFuture<?> completionFuture)
    {
        l.warn(errorMessage);
        completionFuture.setException(new ExTransport(errorMessage));
    }

    private void handleSendEvent_(MessageEvent event)
    {
        if (event.getMessage_() instanceof byte[][]) {
            event.getCompletionFuture_()
                    .chain(_unicast.send_((byte[][]) event.getMessage_(), event.getPriority_()));
        } else {
            String errorMessage = "unrecognized message type:" + event.getMessage_();
            handleUnrecognized_(errorMessage, event.getCompletionFuture_());
        }
    }

    private void handleReadEvent_(ReadEvent event)
    {
        assert !event.getCompletionFuture_().isDone();
        event.getCompletionFuture_().chain(_unicast.receive_());
    }

    private void handleExceptionEvent_(ExceptionEvent<?> event)
    {
        l.warn("exception from pipeline handler:" + event.getException_());
        event.getCompletionFuture_().setException(event.getException_());
        _unicast.disconnect_(event.getException_()); // should I chain this anywhere?
    }

    private void handleUnrecognizedEvent_(IPipelineEvent<?> event)
    {
        String errorMessage = "unrecognized event:" + event;
        handleUnrecognized_(errorMessage, event.getCompletionFuture_());
    }
}
