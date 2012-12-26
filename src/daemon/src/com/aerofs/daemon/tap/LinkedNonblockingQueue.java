/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap;

import com.aerofs.base.async.UncancellableFuture;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.LinkedList;
import java.util.Queue;

public class LinkedNonblockingQueue<E>
{
    private final Queue<E> _delegateList = new LinkedList<E>();
    private final Queue<UncancellableFuture<E>> _pendingRequests = new LinkedList<UncancellableFuture<E>>();

    public static <E> LinkedNonblockingQueue<E> create()
    {
        return new LinkedNonblockingQueue<E>();
    }

    public synchronized ListenableFuture<E> takeWhenAvailable()
    {
        UncancellableFuture<E> future = UncancellableFuture.create();
        if (!_delegateList.isEmpty()) {
            // We can set this future in the synchronized block because
            // we have just created it. No callbacks are attached
            future.set(_delegateList.remove());
        } else {
            _pendingRequests.add(future);
        }
        return future;
    }

    public synchronized void offer(E element)
    {
        UncancellableFuture<E> pendingRequest = _pendingRequests.poll();
        if (pendingRequest != null) {
            pendingRequest.set(element);
        } else {
            _delegateList.add(element);
        }
    }
}
