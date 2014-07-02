package com.aerofs.daemon.lib;

import com.aerofs.base.ex.ExNoResource;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.StrictLock;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.Prio;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class BlockingPrioQueue<T> implements IBlockingPrioritizedEventSink<T>, IDumpStatMisc
{

    private final StrictLock _l;
    private final Condition _cvDeq;
    private final Condition _cvEnq;
    private final PrioQueue<T> _pq;

    // the number of high-priority producers waiting on the lock
    private final AtomicInteger _hiProducers = new AtomicInteger();
    // the number of low-priority threads waiting for high-priority producers to enter the lock
    private int _spinners;
    private final Condition _cvSpinner;

    static {
        // the condition is needed for lockForEnqueueing() to work properly
        assert Prio.values().length == 2;
    }

    public BlockingPrioQueue(StrictLock l, int capacity)
    {
        // fair lock is too slow. don't use it. see
        // http://www.baptiste-wicht.com/2010/09/java-synchronization-mutual-exclusion-benchmark/
        assert !l.isFair();

        _l = l;
        _cvDeq = _l.newCondition();
        _cvEnq = _l.newCondition();
        _cvSpinner = _l.newCondition();

        _pq = new PrioQueue<T>(capacity);
    }

    public BlockingPrioQueue(int capacity)
    {
        this(new StrictLock(), capacity);
    }

    public Lock getLock()
    {
        return _l;
    }

    /**
     * This method and spin_() combined are to guarantee that threads enqueueing
     * high-priority events can enter the lock before other threads including
     * those enqueueing low-priority events and dequeueing threads.
     *
     * Without this mechanism, threads with high-priority events may not enter
     * the lock for a long time as many other threads (especially a pool of
     * worker threads dequeueing events) are also compete for the lock. Thread
     * priorities do't work well because it's only a hint to the OS and nothing
     * is guaranteed.
     *
     * Using a fair lock would much simplify the design. However, fair locks are
     * not desirable due to its low performance:
     * http://www.baptiste-wicht.com/2010/09/java-synchronization-mutual-exclusion-benchmark/
     */
    private void lockForEnqueueing(Prio prio)
    {
        if (prio == Prio.HI) {
            _hiProducers.incrementAndGet();
            _l.lock();
            int producers = _hiProducers.decrementAndGet();
            // Note: other hi-prio producers may increment _hiProducer after
            // the previous line and before the next line, causing a spurious
            // signal(). this is fine.
            //
            // the variable _spinners is added only for optimization, to avoid
            // unnecessary calls to _cvSpinner.signalAll().
            if (_spinners != 0 && producers == 0) _cvSpinner.signalAll();
        } else {
            _l.lock();
            spin_();
        }
    }

    /**
     * @return false if the thread has spun
     */
    private boolean spin_()
    {
        assert _l.isLocked();

        if (_hiProducers.get() == 0) return false;

        _spinners++;
        while (_hiProducers.get() != 0) _cvSpinner.awaitUninterruptibly();
        _spinners--;

        return true;
    }

    @Override
    public void enqueueBlocking(T ev, Prio prio)
    {
        lockForEnqueueing(prio);
        try {
            enqueueBlocking_(ev, prio);
        } finally {
            _l.unlock();
        }
    }

    private void enqueueBlocking_(T ev, Prio prio)
    {
        while (_pq.isFull_()) {
            _cvEnq.awaitUninterruptibly();
        }
        enqueueImpl_(ev, prio);
    }

    @Override
    public void enqueueThrows(T ev, Prio prio) throws ExNoResource
    {
        lockForEnqueueing(prio);
        try {
            enqueueThrows_(ev, prio);
        } finally {
            _l.unlock();
        }
    }

    private void enqueueThrows_(T ev, Prio prio) throws ExNoResource
    {
        if (!enqueue_(ev, prio)) throw new ExNoResource("q full");
    }

    @Override
    public boolean enqueue(T ev, Prio prio)
    {
        lockForEnqueueing(prio);
        try {
            return enqueue_(ev, prio);
        } finally {
            _l.unlock();
        }
    }

    /**
     * @return false if the queue is full
     */
    private boolean enqueue_(T ev, Prio prio)
    {
        if (_pq.isFull_()) {
            return false;
        } else {
            enqueueImpl_(ev, prio);
            return true;
        }
    }

    private void enqueueImpl_(T ev, Prio prio)
    {
        assert _l.isHeldByCurrentThread();
        _pq.enqueue_(ev, prio);
        _cvDeq.signal();
    }

    /**
     * Dequeues and returns an element. If the queue is empty, this call
     * blocks until there is something to dequeue.
     * @param outPrio The priority of the dequeued element
     * @return An element in the queue
     */
    public T dequeueInterruptibly(OutArg<Prio> outPrio) throws InterruptedException
    {
        _l.lock();
        try {
            return dequeueInterruptibly_(outPrio);
        } finally {
            _l.unlock();
        }
    }

    public T tryDequeue(OutArg<Prio> outPrio)
    {
        _l.lock();
        try {
            return tryDequeue_(outPrio);
        } finally {
            _l.unlock();
        }
    }

    // return null if queue is empty
    public T tryDequeue_(OutArg<Prio> outPrio)
    {
        if (_pq.isEmpty_()) return null;
        return dequeue_(outPrio);
    }

    public void await_()
    {
        while (_pq.isEmpty_()) {
            _cvDeq.awaitUninterruptibly();
        }
    }

    public T dequeue_(OutArg<Prio> outPrio)
    {
        assert _l.isHeldByCurrentThread();

        while (true) {
            while (_pq.isEmpty_()) {
                _cvDeq.awaitUninterruptibly();
            }

            if (!spin_()) break;
        }

        T ev = _pq.dequeue_(outPrio);

        if (!_pq.isFull_()) _cvEnq.signal();

        return ev;
    }

    public T dequeueInterruptibly_(OutArg<Prio> outPrio) throws InterruptedException
    {
        assert _l.isHeldByCurrentThread();

        while (true) {
            while (_pq.isEmpty_()) {
                _cvDeq.await();
            }

            if (!spin_()) break;
        }

        T ev = _pq.dequeue_(outPrio);

        if (!_pq.isFull_()) _cvEnq.signal();

        return ev;
    }

    public boolean isEmpty()
    {
        _l.lock();
        try {
            return isEmpty_();
        } finally {
            _l.unlock();
        }
    }

    private boolean isEmpty_()
    {
        return _pq.isEmpty_();
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        _pq.dumpStatMisc(indent, indentUnit, ps);
    }
}
