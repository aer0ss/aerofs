/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class SimpleSingleThreadedExecutor
        implements Runnable, ISingleThreadedPrioritizedExecutor
{
    private final BlockingQueue<Runnable> _taskQueue = new LinkedBlockingQueue<Runnable>();
    private final Timer _timer = new Timer();

    private volatile boolean _shouldStop = false;

    public void stop()
    {
        _shouldStop = true;
        _timer.cancel();
        execute(new Runnable()
        {
            @Override
            public void run()
            {
                // Noop runnable to force the !_shouldStop variable to
                // be evaluated
            }
        });
    }

    @Override
    public void run()
    {
        try {
            while (!_shouldStop) {
                Runnable runnable = _taskQueue.take();
                runnable.run();
            }
        } catch (InterruptedException e) {
            // noop
        }
    }

    @Override
    public void execute(Runnable runnable, Prio pri)
    {
        try {
            _taskQueue.put(runnable);
        } catch (InterruptedException e) {
            // cannot place runnable into queue
            assert false;
        }
    }

    @Override
    public void execute(Runnable runnable)
    {
        try {
            _taskQueue.put(runnable);
        } catch (InterruptedException e) {
            // cannot place runnable into queue
            assert false;
        }
    }

    @Override
    public void executeAfterDelay(final Runnable runnable, long delayInMilliseconds)
    {
        _timer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                execute(runnable); // places it for execution on the event loop thread
            }
        }, delayInMilliseconds);
    }
}
