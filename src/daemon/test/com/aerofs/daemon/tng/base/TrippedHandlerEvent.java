/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.base.async.UncancellableFuture;

import java.util.LinkedList;
import java.util.List;

final class TrippedHandlerEvent implements IPipelineEvent<Void>
{
    private final List<Integer> _trippedHandlerIds = new LinkedList<Integer>();
    private final UncancellableFuture<Void> _completionFuture = UncancellableFuture.create();
    private final IConnection _connection;

    TrippedHandlerEvent(IConnection connection)
    {
        this._connection = connection;
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

    void addTrippedHandlerId_(int id)
    {
        _trippedHandlerIds.add(id);
    }

    List<Integer> getTrippedHandlerIds_()
    {
        return _trippedHandlerIds;
    }
}
