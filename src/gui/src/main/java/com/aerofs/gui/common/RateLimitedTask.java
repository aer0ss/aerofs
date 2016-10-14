/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.common;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.gui.GUI;
import com.google.common.base.Preconditions;

/**
 * RateLimitedTask is intended to be used to throttle expensive GUI actions like refreshing. All
 * methods must be called on the UI thread, thus there's no need to synchronize method calls.
 */
public abstract class RateLimitedTask implements Runnable
{
    private final ElapsedTimer _timer = new ElapsedTimer().start();
    private final long _rate;

    private boolean _scheduled;

    /**
     * @param rate - the minimum amount of time in between each successive _scheduled_ runs.
     */
    public RateLimitedTask(long rate)
    {
        _rate = rate;
    }

    /**
     * Invoke to immediate perform the task.
     */
    public void run()
    {
        Preconditions.checkState(GUI.get().isUIThread());

        _timer.restart();
        _scheduled = false;

        workImpl();
    }

    /**
     * Invoke to schedule the task to be performed on the GUI thread no less than {@paramref rate}
     * milliseconds, possibly immediately, since the task was last performed.
     */
    public void schedule()
    {
        Preconditions.checkState(GUI.get().isUIThread());

        if (!_scheduled) {
            _scheduled = true;
            GUI.get().timerExec(Math.max(0, _rate - _timer.elapsed()), this);
        }
    }

    /**
     * Override to perform the actual task.
     * @pre invoked from the UI thread.
     */
    protected abstract void workImpl();
}
