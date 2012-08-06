/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap.filter;

import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.lib.async.UncancellableFuture;

public final class MessageFilterRequest
{
    private final UncancellableFuture<Void> _permissionFuture;
    public final Object message;

    MessageFilterRequest(Object message, UncancellableFuture<Void> permissionFuture)
    {
        this.message = message;
        this._permissionFuture = permissionFuture;
    }

    /**
     * Forwards this message to the pipeline
     */
    public void allow()
    {
        _permissionFuture.set(null);
    }

    /**
     * Fails this message and prevents it from going into the pipeline
     */
    public void deny()
    {
        _permissionFuture.setException(new ExTapDeniedMessage(message));
    }

    private class ExTapDeniedMessage extends ExTransport
    {
        private static final long serialVersionUID = 0L;

        private ExTapDeniedMessage(Object message)
        {
            super("Pipeline message " + message + " denied by MessageFilter");
        }
    }
}
