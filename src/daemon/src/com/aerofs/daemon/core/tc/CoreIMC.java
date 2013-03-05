package com.aerofs.daemon.core.tc;

import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.QueueBasedIMCExecutor;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.base.ex.ExNoResource;

public class CoreIMC {

    public static void enqueueBlocking_(AbstractEBIMC ev, TC tc, Cat cat)
        throws ExNoResource, ExAborted
    {
        // try the unblocking version first, and then fall back to the blocking
        // version

        if (ev.imce().enqueue_(ev, tc.prio())) return;

        Token tk = tc.acquireThrows_(cat, "CoreIMC.enqBlocking");
        try {
            enqueueBlockingImpl_(ev, tc, tk);
        } finally {
            tk.reclaim_();
        }
    }

    private static void enqueueBlockingImpl_(AbstractEBIMC ev, TC tc, Token tk)
        throws ExNoResource, ExAborted
    {
        assert ev.imce() instanceof QueueBasedIMCExecutor;

        TCB tcb = tk.pseudoPause_(ev.toString());
        try {
            ev.imce().enqueueBlocking_(ev, tc.prio());
        } finally {
            tcb.pseudoResumed_();
        }
    }

    public static void execute_(AbstractEBIMC ev, TC tc, Cat cat)
        throws Exception
    {
        Token tk = tc.acquireThrows_(cat, "CoreIMC.execute");
        try {
            execute_(ev, tc, tk);
        } finally {
            tk.reclaim_();
        }
    }

    public static void execute_(AbstractEBIMC ev, TC tc, Token tk)
        throws Exception
    {
        assert ev.imce() instanceof QueueBasedIMCExecutor;

        TCB tcb = tk.pseudoPause_(ev.toString());
        try {
            ev.imce().execute_(ev, tc.prio());
            if (ev.exception() != null) throw ev.exception();
        } finally {
            tcb.pseudoResumed_();
        }
    }
}
