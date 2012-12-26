/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.base.async.UncancellableFuture;
import com.google.common.collect.ImmutableList;

final public class ReadEvent implements IPipelineEvent<ImmutableList<WireData>>
{
    private final UncancellableFuture<ImmutableList<WireData>> _completionFuture = UncancellableFuture
            .create();
    private final IConnection _connection;

    ReadEvent(IConnection connection)
    {
        this._connection = connection;
    }

    @Override
    public UncancellableFuture<ImmutableList<WireData>> getCompletionFuture_()
    {
        return _completionFuture;
    }

    @Override
    public IConnection getConnection_()
    {
        return _connection;
    }
}
