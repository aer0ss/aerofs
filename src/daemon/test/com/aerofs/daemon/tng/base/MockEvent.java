/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;

final class MockEvent implements IPipelineEvent<Void>
{
    private final UncancellableFuture<Void> _completionFuture = UncancellableFuture.create();
    private final IConnection _connection;

    MockEvent(IConnection connection)
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
}
