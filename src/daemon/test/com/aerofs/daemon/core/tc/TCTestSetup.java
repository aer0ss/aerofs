package com.aerofs.daemon.core.tc;

import java.sql.SQLException;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.tc.TokenManager.CfgCats;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.event.lib.EBRunnable;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.Scheduler;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.InMemorySQLiteDBCW;

public class TCTestSetup
{
    private static final Logger l = Util.l(TCTestSetup.class);

    public CoreQueue _q = new CoreQueue();
//    public CoreScheduler _sched = new CoreScheduler(_q);

    static class ScheduledWrapper<V> implements Delayed
    {
        private static final AtomicLong _sequencer = new AtomicLong(0);
        private static final long NANO_ORIGIN = System.nanoTime();

        static final long now() {
            return System.nanoTime() - NANO_ORIGIN;
        }

        private final V _value;
        private final long _time;
        private final long _sequence = _sequencer.getAndIncrement();

        public ScheduledWrapper(V value, long ns)
        {
            _value = value;
            _time = ns;
        }

        @Override
        public String toString()
        {
            return String.valueOf(_value);
        }

        public V getValue()
        {
            return _value;
        }

        @Override
        public long getDelay(TimeUnit unit)
        {
            return unit.convert(_time - now(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other)
        {
            if (this == other) return 0;
            if (other instanceof ScheduledWrapper<?>) {
                ScheduledWrapper<?> o = (ScheduledWrapper<?>)other;
                long diff = _time - o._time;
                if (diff != 0) return Long.signum(diff);
                return Long.signum(_sequence - o._sequence);
            } else {
                long diff = getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS);
                return Long.signum(diff);
            }
        }
    }

    public PriorityQueue<ScheduledWrapper<IEvent>> _eventQueue =
            new PriorityQueue<ScheduledWrapper<IEvent>>();

    public CoreScheduler _sched = Mockito.mock(CoreScheduler.class);
    {
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                Object[] args = invocation.getArguments();
                l.debug("schedule(" + args[0] + ", " + args[1] + ')');
                IEvent event = (IEvent)args[0];
                long delayMillis = ((Long)args[1]).longValue();
                long ns = ScheduledWrapper.now() + TimeUnit.MILLISECONDS.toNanos(delayMillis);
                ScheduledWrapper<IEvent> element = new ScheduledWrapper<IEvent>(event, ns);
                _eventQueue.add(element);
                return null;
            }
        }).when(_sched).schedule(Mockito.any(IEvent.class), Mockito.anyLong());
    }

    public void drainScheduler_()
    {
        ScheduledWrapper<IEvent> wev;
        while ((wev = _eventQueue.poll()) != null) {
            IEvent event = wev.getValue();
            AbstractEBSelfHandling sh = (AbstractEBSelfHandling)event;
            sh.handle_();
        }
    }

    public CoreEventDispatcher _disp = Mockito.mock(CoreEventDispatcher.class);
    {
        Mockito.doCallRealMethod().when(_disp).dispatch_(
                Mockito.any(IEvent.class), Mockito.any(Prio.class));
    }

    public CoreDBCW _coreDBCW = new InMemorySQLiteDBCW().mockCoreDBCW();
    public TransManager _transManager = new TransManager(new Trans.Factory(_coreDBCW));
    public TokenManager _tokenManager = new TokenManager();
    public TC _tc = new TC();
    {
        CfgCats cfgCats = Mockito.mock(CfgCats.class);
        Mockito.when(cfgCats.getQuota(Mockito.any(Cat.class))).thenReturn(1);
        _tokenManager.inject_(_tc, cfgCats);
        _tc.inject_(_sched, _transManager, _disp, _q, _tokenManager);
    }

    public void start_() throws SQLException
    {
        _coreDBCW.get().init_();
        _tc.start_();
    }

    public void shutdown_() throws SQLException, InterruptedException
    {
//        drain(_sched);
        _sched.shutdown();
        _q.enqueueBlocking(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                _tc.shutdown_();
            }
        }, Prio.LO);
        _coreDBCW.get().fini_();
    }

    public <T> Future<T> runInCoreThread(Callable<T> callable)
    {
        final RunnableFuture<T> task = new FutureTask<T>(callable);
        _q.enqueueBlocking(new EBRunnable(task), Prio.LO);
        return task;
    }

    private static class SyncEvent extends AbstractEBSelfHandling
    {
        @Override
        public void handle_()
        {
            l.debug("handle");
            synchronized (this) {
                notifyAll();
                l.debug("notify");
            }
        }
    }

    void drain(BlockingPrioQueue<IEvent> q) throws InterruptedException
    {
        IEvent event = new SyncEvent();
        synchronized (event) {
            q.enqueueBlocking(event, Prio.LO);
            event.wait();
        }
    }

    void drain(Scheduler sched) throws InterruptedException
    {
        IEvent event = new SyncEvent();
        synchronized (event) {
            l.debug("schedule");
            sched.schedule(event, 0);
            l.debug("wait");
            event.wait();
        }
    }
}
