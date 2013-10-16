/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.tunnel;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Queue;

public class ConnectionWatcher<V>
{
    private final Queue<V> _connected = Queues.newArrayDeque();
    private final Queue<SettableFuture<V>> _onConnect = Queues.newArrayDeque();

    public synchronized ListenableFuture<V> nextConnection()
    {
        if (_connected.isEmpty()) {
            SettableFuture<V> f = SettableFuture.create();
            _onConnect.add(f);
            return f;
        } else {
            return Futures.immediateFuture(_connected.poll());
        }
    }

    public synchronized void connected(V v)
    {
        if (_onConnect.isEmpty()) {
            _connected.add(v);
        } else {
            _onConnect.poll().set(v);
        }
    }

    public synchronized void disconnected(V v)
    {

    }
}
