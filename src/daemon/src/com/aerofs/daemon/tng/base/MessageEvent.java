/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.base.async.UncancellableFuture;

public final class MessageEvent implements IPipelineEvent<Void>
{
    private final IConnection _connection;
    private final UncancellableFuture<Void> _completionFuture;
    private final Object _msg;
    private final Prio _pri;

    public MessageEvent(IConnection connection, UncancellableFuture<Void> completionFuture,
            Object msg, Prio pri)
    {
        this._connection = connection;
        this._msg = msg;
        this._pri = pri;
        this._completionFuture = completionFuture;
    }

    @Override
    public UncancellableFuture<Void> getCompletionFuture_()
    {
        return _completionFuture;
    }

    @Override
    public IConnection getConnection_()
    {
        return _connection;
    }

    public Object getMessage_()
    {
        return _msg;
    }

    public Prio getPriority_()
    {
        return _pri;
    }
}
