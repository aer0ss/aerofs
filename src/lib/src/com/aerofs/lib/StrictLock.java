package com.aerofs.lib;

import java.util.concurrent.locks.ReentrantLock;

// it's a strict lock in a sense that it doesn't allow reentrance
public class StrictLock extends ReentrantLock
{
    private static final long serialVersionUID = 1L;

    @Override
    public void lock()
    {
        assert !isHeldByCurrentThread();
        super.lock();
    }

    @Override
    public boolean tryLock()
    {
        assert !isHeldByCurrentThread();
        return super.tryLock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException
    {
        assert !isHeldByCurrentThread();
        super.lockInterruptibly();
    }
}
