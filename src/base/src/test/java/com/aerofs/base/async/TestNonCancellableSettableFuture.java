/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.async;

import com.aerofs.base.Loggers;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Test;
import org.slf4j.Logger;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestNonCancellableSettableFuture
{
    @Test
    public void itShouldTriggerTheCallerWhenTheNestedOperationCompletesSuccessfully()
        throws ExecutionException, InterruptedException
    {
        // construct an async module

        UncancellableFuture<Void> asyncModule1Future = UncancellableFuture.create();
        OneUseAsyncModule oa0 = new OneUseAsyncModule(asyncModule1Future, 200, false);

        // now assume that you're the caller...

        ListenableFuture<Void> f1 = oa0.doOperation();
        UncancellableFuture<Void> f0 = UncancellableFuture.create();
        assertEquals(FutureUtil.chain(f0, f1), f0);
        assertEquals("hello", "hello");

        // now wait on f0

        Thread t = new Thread(oa0);
        t.start();
        f0.get();

        assertTrue(f0.isDone());
        assertTrue(f1.isDone());
    }

    @Test
    public void itShouldTriggerTheCallerEvenWhenTheNestedOperationThrowsAnException()
    {
         // construct an module

        UncancellableFuture<Void> asyncModule1Future = UncancellableFuture.create();
        OneUseAsyncModule oa0 = new OneUseAsyncModule(asyncModule1Future, 200, true);

        // now assume that you're the caller...

        ListenableFuture<Void> f1 = oa0.doOperation();
        UncancellableFuture<Void> f0 = UncancellableFuture.create();
        assertEquals(FutureUtil.chain(f0, f1), f0);

        // now wait on f0

        Thread t = new Thread(oa0);
        t.start();
        try {
            f0.get();
        } catch (ExecutionException e) {
            assertEquals(MY_TEST_RUNTIME_EXCEPTION, e.getCause());
        } catch (Throwable thr) {
            fail("unexpected exception");
        }

        assertTrue(f0.isDone());
        assertTrue(f1.isDone());
    }

    private static class OneUseAsyncModule implements Runnable
    {
        private OneUseAsyncModule(UncancellableFuture<Void> f, long sleeptime, boolean throwex)
        {
            _f = f;
            _sleeptime = sleeptime;
            _throwex = throwex;
        }

        public ListenableFuture<Void> doOperation()
        {
            return _f;
        }

        @Override
        public void run()
        {
            try {
                Thread.sleep(_sleeptime);
                if (_throwex) _f.setException(MY_TEST_RUNTIME_EXCEPTION); else _f.set(null);
            } catch (InterruptedException e) {
                _f.setException(e);
                l.warn("interrupted");
            }
        }

        private final boolean _throwex;
        private final UncancellableFuture<Void> _f;
        private final long _sleeptime;
        private static final Logger l = Loggers.getLogger(OneUseAsyncModule.class);
    }

    private static final RuntimeException MY_TEST_RUNTIME_EXCEPTION = new RuntimeException();
}
