/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.notifier;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

import static com.google.common.collect.Maps.newConcurrentMap;
import static com.google.common.util.concurrent.Futures.allAsList;

/**
 * TODO (WW) this class is duplicate with ConcurrentlyModifiableListeners in functionality.
 *
 * N.B. Listeners are notified in an arbitrary order.
 */
public final class Notifier<ListenerType>
{
    private final ConcurrentMap<ListenerType, Executor> _listeners = newConcurrentMap();

    public static <ListenerType> Notifier<ListenerType> create()
    {
        return new Notifier<ListenerType>();
    }

    private Notifier()
    {
        // Hide the constructor
    }

    public final void addListener(ListenerType listener, Executor notificationExecutor)
    {
        Executor previous = _listeners.put(listener, notificationExecutor);
        assert previous == null;
    }

    public final void removeListener(ListenerType listener)
    {
        _listeners.remove(listener);
    }

    /**
     * Notify listeners on their <strong>own</strong> thread
     *
     * @param visitor Caller-supplied visitor that is passed a listener that it can wants to notify.
     * <strong>IMPORTANT:</strong> the visitor's visit() method will be called from multiple
     * threads. This means that the {@code ListenerVisitor} should be reference immutable or
     * thread-safe data only.
     */
    public final ListenableFuture<List<Void>> notifyOnOtherThreads(
            final IListenerVisitor<ListenerType> visitor)
    {
        List<ListenableFuture<Void>> futures = new LinkedList<ListenableFuture<Void>>();

        Iterator<Entry<ListenerType, Executor>> it = _listeners.entrySet().iterator();
        while (it.hasNext()) {
            Entry<ListenerType, Executor> entry = it.next();

            final ListenerType listener = entry.getKey();
            final Executor executor = entry.getValue();
            final SettableFuture<Void> future = SettableFuture.create();
            futures.add(future);

            executor.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    try {
                        visitor.visit(listener);
                    } finally {
                        future.set(null);
                    }
                }
            });
        }

        return allAsList(futures);
    }

}
