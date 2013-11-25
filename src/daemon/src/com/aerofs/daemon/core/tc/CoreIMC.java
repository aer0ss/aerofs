package com.aerofs.daemon.core.tc;

import com.aerofs.base.ex.ExNoResource;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.QueueBasedIMCExecutor;

public class CoreIMC
{
    public static void enqueueBlocking_(AbstractEBIMC ev, Token tk)
            throws ExAborted, ExNoResource
    {
        // try the unblocking version first, and then fall back to the blocking version
        if (ev.imce().enqueue_(ev, TC.currentThreadPrio())) return;
        enqueueBlockingImpl_(ev, tk);
    }

    public static void enqueueBlocking_(AbstractEBIMC ev, TokenManager tokenManager)
        throws ExNoResource, ExAborted
    {
        // try the unblocking version first, and then fall back to the blocking version
        if (ev.imce().enqueue_(ev, TC.currentThreadPrio())) return;

        Token tk = tokenManager.acquireThrows_(Cat.UNLIMITED, "CoreIMC.enqBlocking");
        try {
            enqueueBlockingImpl_(ev, tk);
        } finally {
            tk.reclaim_();
        }
    }

    private static void enqueueBlockingImpl_(AbstractEBIMC ev, Token tk)
        throws ExNoResource, ExAborted
    {
        assert ev.imce() instanceof QueueBasedIMCExecutor;

        TCB tcb = tk.pseudoPause_(ev.toString());
        try {
            ev.imce().enqueueBlocking_(ev, TC.currentThreadPrio());
        } finally {
            tcb.pseudoResumed_();
        }
    }
}
