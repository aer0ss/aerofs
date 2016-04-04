package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.lib.id.SOID;
import com.google.common.util.concurrent.AbstractFuture;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

public class WaitableSubmitter<T> {
    private class Waiter extends AbstractFuture<T> {
        private final SOID soid;

        Waiter(SOID soid) {
            this.soid = soid;
        }

        @Override
        public boolean set(@Nullable T value) {
            return super.set(value);
        }

        @Override
        public boolean setException(Throwable t) {
            return super.setException(t);
        }

        @Override
        public boolean cancel(boolean maybeInterruptIfRunning) {
            _waiters.remove(soid, this);
            return super.cancel(maybeInterruptIfRunning);
        }
    }

    private final Map<SOID, Waiter> _waiters = new HashMap<>();

    public Future<T> waitSubmitted_(SOID soid) {
        Waiter w = _waiters.get(soid);
        if (w == null) {
            w = new Waiter(soid);
            _waiters.put(soid, w);
        }
        return w;
    }

    protected void notifyWaiter_(SOID soid, T v) {
        Waiter w = _waiters.remove(soid);
        if (w != null) w.set(v);
    }
}
