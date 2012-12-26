/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.base.async.UncancellableFuture;

import javax.annotation.Nullable;

final public class ConnectionEvent implements IPipelineEvent<Void>
{
    public static enum Type
    {
        CONNECT,
        DISCONNECT
    }

    private final IConnection _connection;
    private final Type _type;
    @Nullable private final Exception _ex;
    private final UncancellableFuture<Void> _completionFuture = UncancellableFuture.create();

    public ConnectionEvent(IConnection connection, Type type)
    {
        this(connection, type, null);
    }

    public ConnectionEvent(IConnection connection, Type type, @Nullable Exception ex)
    {
        this._connection = connection;
        this._type = type;
        this._ex = ex;
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

    public Type getType_()
    {
        return _type;
    }

    @Nullable
    Exception getException_()
    {
        return _ex;
    }
}