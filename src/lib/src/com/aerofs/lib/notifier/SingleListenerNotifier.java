/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.notifier;

import com.aerofs.base.async.UncancellableFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.util.concurrent.Futures.addCallback;

public final class SingleListenerNotifier<ListenerType>
{
    private final AtomicBoolean _listenerSet = new AtomicBoolean(false);
    private final Notifier<ListenerType> _notifier = Notifier.create();

    public static <ListenerType> SingleListenerNotifier<ListenerType> create()
    {
        return new SingleListenerNotifier<ListenerType>();
    }

    public static <ListenerType> SingleListenerNotifier<ListenerType> create(
            ListenerType listener,
            Executor callbackExecutor)
    {
        SingleListenerNotifier<ListenerType> notifier = new SingleListenerNotifier<ListenerType>();
        notifier.setListener(listener, callbackExecutor);
        return notifier;
    }

    private SingleListenerNotifier()
    {
        // Hide the constructor
    }

    public final void setListener(ListenerType listener, Executor callbackExecutor)
    {
        assert !_listenerSet.getAndSet(true);
        _notifier.addListener(listener, callbackExecutor);
    }

    public final ListenableFuture<Void> notifyOnOtherThreads(final IListenerVisitor<ListenerType> visitor)
    {
        ListenableFuture<List<Void>> future = _notifier.notifyOnOtherThreads(visitor);

        final UncancellableFuture<Void> returnedFuture = UncancellableFuture.create();
        addCallback(future, new FutureCallback<List<Void>>()
        {
            @Override
            public void onSuccess(List<Void> voids)
            {
                returnedFuture.set(null);
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                returnedFuture.setException(throwable);
            }
        });
        return returnedFuture;
    }

}
