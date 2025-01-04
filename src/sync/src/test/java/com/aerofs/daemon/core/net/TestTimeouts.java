package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.testlib.AbstractTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestTimeouts extends AbstractTest {
    private static class T implements Timable<T> {
        volatile boolean _timedout;

        @Override
        public void timeout_() {
            _timedout = true;
        }

        @Override
        public int compareTo(T o) {
            return hashCode() - o.hashCode();
        }
    }

    private static final IEvent DONE = new IEvent() {};

    private final CoreQueue q = new CoreQueue();
    private final CoreScheduler sched = new CoreScheduler(q);

    private int _eventCount = 0;
    private int _expectedEventCount = 0;
    private final Thread t = new Thread(() -> {
        OutArg<Prio> prio = new OutArg<>();
        while (true) {
            q.getLock().lock();
            IEvent e = q.dequeue_(prio);
            q.getLock().unlock();
            if (e == DONE) break;
            ++_eventCount;
            ((AbstractEBSelfHandling)e).handle_();
        }
    });

    private Timeouts<T> _timeouts = new Timeouts<>(sched);

    private long scaleFactor = 1;

    @Before
    public void setUp() {
        // the github action mac runner needs some extra buffer for these tests...
        this.scaleFactor = OSUtil.isOSX() ? 5 : 1;
        t.start();
    }

    @After
    public void tearDown() throws Exception {
        q.enqueue(DONE, Prio.LO);
        t.join();
        assertEquals(_expectedEventCount, _eventCount);
    }

    @Test
    public void shouldNotTimeout() throws Exception {
        _expectedEventCount = 0;
        T t0 = new T();
        Timeouts<T>.Timeout tt = _timeouts.add_(t0, 500 * this.scaleFactor);
        Thread.sleep(200 * this.scaleFactor);
        tt.cancel_();
        Thread.sleep(400 * this.scaleFactor);
        assertFalse(t0._timedout);
    }

    @Test
    public void shouldTimeout() throws Exception {
        _expectedEventCount = 1;
        T t0 = new T();
        _timeouts.add_(t0, 200 * this.scaleFactor);
        Thread.sleep(400 * this.scaleFactor);
        assertTrue(t0._timedout);
    }

    @Test
    public void shouldGroupTimeouts() throws Exception {
        _expectedEventCount = 1;
        T t0 = new T(), t1 = new T();
        _timeouts.add_(t0, 200 * this.scaleFactor);
        _timeouts.add_(t1, 200 * this.scaleFactor);
        Thread.sleep(300 * this.scaleFactor);
        assertTrue(t0._timedout);
        assertTrue(t1._timedout);
    }

    @Test
    public void shouldTimeoutSubsequently() throws Exception {
        _expectedEventCount = 2;
        T t0 = new T(), t1 = new T();
        _timeouts.add_(t0, 200 * this.scaleFactor);
        _timeouts.add_(t1, 600 * this.scaleFactor);
        Thread.sleep(250 * this.scaleFactor);
        assertTrue(t0._timedout);
        assertFalse(t1._timedout);
        Thread.sleep(400 * this.scaleFactor);
        assertTrue(t1._timedout);
    }

    @Test
    public void shouldTimeoutAfterCancel() throws Exception {
        _expectedEventCount = 2;
        T t0 = new T(), t1 = new T();
        Timeouts<T>.Timeout tt = _timeouts.add_(t0, 200 * this.scaleFactor);
        _timeouts.add_(t1, 600 * this.scaleFactor);
        Thread.sleep(100 * this.scaleFactor);
        tt.cancel_();
        assertFalse(t0._timedout);
        assertFalse(t1._timedout);
        Thread.sleep(200 * this.scaleFactor);
        assertFalse(t1._timedout);
        Thread.sleep(400 * this.scaleFactor);
        assertTrue(t1._timedout);
    }
}
