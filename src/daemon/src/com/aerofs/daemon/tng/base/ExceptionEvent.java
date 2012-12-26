/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.base.async.UncancellableFuture;

// same type of future return value as the event that caused the handler to throw
final public class ExceptionEvent<FutureReturn> implements IPipelineEvent<FutureReturn>
{
    private final UncancellableFuture<FutureReturn> _completionFuture;
    private final IConnection _connection;
    private final Exception _exception;

    private ExceptionEvent(UncancellableFuture<FutureReturn> completionFuture,
            IConnection connection, Exception exception)
    {
        this._completionFuture = completionFuture;
        this._connection = connection;
        this._exception = exception;
    }

    @Override
    public UncancellableFuture<FutureReturn> getCompletionFuture_()
    {
        return _completionFuture;
    }

    @Override
    public IConnection getConnection_()
    {
        return _connection;
    }

    Exception getException_()
    {
        return _exception;
    }

    static <FutureReturn> ExceptionEvent<FutureReturn> getInstance_(
            IPipelineEvent<FutureReturn> failedEvent, Exception handlerException)
    {
        return new ExceptionEvent<FutureReturn>(failedEvent.getCompletionFuture_(),
                failedEvent.getConnection_(), handlerException);
    }

    static <FutureReturn> ExceptionEvent<FutureReturn> getInstance_(
            UncancellableFuture<FutureReturn> failedEventCompletionFuture,
            IConnection failedEventConnection, Exception handlerException)
    {
        return new ExceptionEvent<FutureReturn>(failedEventCompletionFuture, failedEventConnection,
                handlerException);
    }
}