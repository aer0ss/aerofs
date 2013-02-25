package com.aerofs.gui;

import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.sched.IScheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * This class serves as a very simple stripped down single threaded scheduler for the GUI. Only
 * {@link AbstractEBSelfHandling} events are handled and they are executed serially due to the fact
 * that the thread pool we are using is single threaded.
 */
public class GuiScheduler implements IScheduler
{
    private ScheduledExecutorService _executor;

    public GuiScheduler()
    {
        _executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable runnable)
            {
                return new Thread(runnable, "gui-scheduler");
            }
        });
    }

    public void shutdown()
    {
        _executor.shutdownNow();
    }

    @Override
    public void schedule(final IEvent ev, long relativeTimeInMSec)
    {
        _executor.schedule(new Runnable() {
            @Override
            public void run()
            {
                assert ev instanceof AbstractEBSelfHandling;
                ((AbstractEBSelfHandling) ev).handle_();
            }
        }, relativeTimeInMSec, TimeUnit.MILLISECONDS);
    }
}
