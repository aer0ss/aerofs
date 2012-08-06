package com.aerofs.daemon.core.tc;

import com.aerofs.daemon.core.tc.TC.TCB;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class FutureBasedCoreIMC
{
    private static <FutureReturn> FutureReturn getFutureResult_(Future<FutureReturn> f)
            throws Exception
    {
        try {
            return f.get();
        } catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof Exception) throw (Exception) t;
            else assert false : ("future execution caused non-checked throw");
            return null; // satisfy compiler
        }
    }

    public static <FutureReturn> FutureReturn blockingWaitForResult_(Future<FutureReturn> f,
            TC tc,
            Cat taskCategory,
            String blockingReason)
            throws Exception
    {
        Token tk = tc.acquireThrows_(taskCategory, blockingReason);
        try {
            return blockingWaitForResult_(f, tk, blockingReason);
        } finally {
            tk.reclaim_();
        }
    }

    public static <FutureReturn> FutureReturn blockingWaitForResult_(Future<FutureReturn> f,
            Token token,
            String blockingReason)
            throws Exception
    {
        if (f.isDone()) {
            return getFutureResult_(f);
        } else {
            TCB tcb = token.pseudoPause_(blockingReason);
            try {
                return getFutureResult_(f);
            } finally {
                tcb.pseudoResumed_();
            }
        }
    }
}
