/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkState;

/**
 * In order to improve the throughput between the core and the transport, we don't want to
 *  wait until stream chunks are be sent by the transport. However, we don't want to enqueue
 * too many chunks at once since this would be using the transport's event loop for flow
 * control. So we do flow control manually here, by counting how many chunks we've enqueued
 * on the transport event queue and blocking if we're above some maximum.
 */
public class ChunksCounter
{
    private static final int MAX_WAITING_CHUNKS = 10;
    private volatile int _waitingChunks; // how many EOChunks are sitting on the transport queue
    private final Object _waitingChunksLock = new Object(); // protects access to _waitingChunks

    public void incChunkCount()
    {
        synchronized (_waitingChunksLock) {
            _waitingChunks++;
        }
    }

    public void decChunkCount()
    {
        synchronized (_waitingChunksLock) {
            _waitingChunks--;
            checkState(_waitingChunks >= 0);
            if (_waitingChunks < (MAX_WAITING_CHUNKS / 2)) _waitingChunksLock.notify();
        }
    }

    /**
     * Wait until we've sent some chunks.
     * @param tk if non-null, we will call pseudo pause/resume on the token
     * @throws ExAborted if tk is non-null and pseudo pause or resume throws.
     */
    public void waitIfTooManyChunks_(@Nullable Token tk)
            throws ExAborted
    {
        synchronized (_waitingChunksLock) {
            if (_waitingChunks >= MAX_WAITING_CHUNKS) {
                TCB tcb = null;
                if (tk != null) tcb = tk.pseudoPause_("waiting for tp to send chunks");
                try {
                    _waitingChunksLock.wait();
                } catch (InterruptedException e) {
                    // ignore
                } finally {
                    if (tcb != null) tcb.pseudoResumed_();
                }
            }
        }
    }
}
