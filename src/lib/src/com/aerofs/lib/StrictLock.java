package com.aerofs.lib;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

// it's a strict lock in a sense that it doesn't allow reentrance
public class StrictLock extends ReentrantLock
{

    private static final long serialVersionUID = 1L;

    private final Profiler _profiler = new Profiler();

    @Override
    public void lock()
    {
        assert !isHeldByCurrentThread();
        super.lock();
        _profiler.start();
    }

    @Override
    public boolean tryLock()
    {
        assert !isHeldByCurrentThread();
        if (!super.tryLock()) return false;
        _profiler.start();
        return true;
    }

    @Override
    public void lockInterruptibly() throws InterruptedException
    {
        assert !isHeldByCurrentThread();
        super.lockInterruptibly();
        _profiler.start();
    }

    @Override
    public void unlock()
    {
        _profiler.stop();
        super.unlock();
    }

    @Override
    public Condition newCondition()
    {
        return newUnprofiledCondition();
    }

    public Condition newUnprofiledCondition()
    {
        return super.newCondition();
    }

    public Condition newProfiledCondition()
    {
        return profiled(newUnprofiledCondition());
    }

    @Override
    public boolean hasWaiters(Condition condition)
    {
        return super.hasWaiters(unwrap(condition));
    }

    @Override
    public int getWaitQueueLength(Condition condition)
    {
        return super.getWaitQueueLength(unwrap(condition));
    }

    public Profiler profiler()
    {
        return _profiler;
    }

    private Condition profiled(Condition cond)
    {
        if (!(cond instanceof ProfiledCondition)) {
            cond = new ProfiledCondition(cond);
        }
        return cond;
    }

    private Condition unwrap(Condition cond)
    {
        while (cond instanceof ProfiledCondition) {
            cond = ((ProfiledCondition)cond)._cond;
        }
        return cond;
    }

    private final class ProfiledCondition implements Condition
    {
        private final Condition _cond;

        private ProfiledCondition(Condition cond)
        {
            _cond = cond;
        }

        public void await() throws InterruptedException
        {
            _profiler.stop();
            try {
                _cond.await();
            } finally {
                _profiler.start();
            }
        }

        public void awaitUninterruptibly()
        {
            _profiler.stop();
            try {
                _cond.awaitUninterruptibly();
            } finally {
                _profiler.start();
            }
        }

        public long awaitNanos(long nanosTimeout) throws InterruptedException
        {
            _profiler.stop();
            try {
                return _cond.awaitNanos(nanosTimeout);
            } finally {
                _profiler.start();
            }
        }

        public boolean await(long time, TimeUnit unit)
                throws InterruptedException
        {
            _profiler.stop();
            try {
                return _cond.await(time, unit);
            } finally {
                _profiler.start();
            }
        }

        public boolean awaitUntil(Date deadline) throws InterruptedException
        {
            _profiler.stop();
            try {
                return _cond.awaitUntil(deadline);
            } finally {
                _profiler.start();
            }
        }

        public void signal()
        {
            _cond.signal();
        }

        public void signalAll()
        {
            _cond.signalAll();
        }
    }
}
