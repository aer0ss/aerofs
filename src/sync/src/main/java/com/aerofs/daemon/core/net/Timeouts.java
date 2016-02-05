package com.aerofs.daemon.core.net;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class Timeouts<T extends Timable<T>> {
    private final static Logger l = Loggers.getLogger(Timeouts.class);

    public class Timeout implements Comparable<Timeout> {
        private final T _t;
        private final long _timeout;

        Timeout(T t, long timeout) {
            _t = t;
            _timeout = timeout;
        }

        public void cancel_() {
            Timeouts.this.cancel_(this);
        }

        @Override
        public int compareTo(Timeout o) {
            int c = Long.compare(_timeout, o._timeout);
            if (c == 0) c = _t.compareTo(o._t);
            return c;
        }
    }

    private final CoreScheduler _sched;

    private final ElapsedTimer _t = new ElapsedTimer();

    // next scheduled insertion of event in the core queue, cancellable
    private long _schedD;
    // non-null if an event is scheduled
    // true if the event is "done", i.e. past the point where the blocking enqueue can be cancelled
    private AtomicBoolean _schedF;

    private final SortedSet<Timeout> _timeouts = new TreeSet<>();

    private final AbstractEBSelfHandling _ev = new AbstractEBSelfHandling() {
        @Override
        public void handle_() {
            long now = _t.nanosElapsed();
            Iterator<Timeout> it = _timeouts.iterator();
            while (it.hasNext()) {
                Timeout ts = it.next();
                checkState(ts._timeout > 0);
                long next = MILLISECONDS.convert(ts._timeout - now, NANOSECONDS);
                if (next > 50) {
                    l.debug("next unripe {}ms", next);
                    reschedWithDelay_(next);
                    break;
                }
                it.remove();
                ts._t.timeout_();
            }
        }
    };

    public Timeouts(CoreScheduler sched) {
        _sched = sched;
    }

    public void cancel_(Timeout timeout) {
        _timeouts.remove(timeout);
        if (_timeouts.isEmpty() && _schedF != null) {
            _schedF.set(true);
            _schedF = null;
        }
    }

    public Timeout add_(T t, long millis) {
        if (_timeouts.isEmpty()) {
            if (_schedF != null) {
                _schedF.set(true);
                _schedF = null;
            }
            _t.restart();
        }
        long nanos = reschedWithDelay_(millis);
        Timeout timeout = new Timeout(t, nanos);
        _timeouts.add(timeout);
        return timeout;
    }

    private long reschedWithDelay_(long millis) {
        long base = _t.nanosElapsed();
        long nanos = base + NANOSECONDS.convert(millis, MILLISECONDS);
        long cur = MILLISECONDS.convert(Math.max(0L, _schedD - base), NANOSECONDS);
        if (_schedF != null && cur > millis + 50) {
            _schedF.set(true);
            _schedF = null;
        }
        if (_schedF == null || _schedF.get()) {
            l.debug("restart timeout processing in {}ms", millis);
            _schedD = nanos;
            _schedF = _sched.scheduleCancellable(_ev, millis);
        } else {
            l.debug("req redundant timeout processing in {}ms [{}ms]", millis, cur);
        }
        return nanos;
    }
}
