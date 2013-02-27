/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.proto.Files;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EventQueueBasedEventLoop implements IEventLoop
{
    private static final Logger l = Util.l(EventQueueBasedEventLoop.class);

    private final AtomicBoolean _started = new AtomicBoolean(false);
    private final BlockingPrioQueue<IEvent> _eq;
    private final EventDispatcher _dispatcher = new EventDispatcher();
    private final Scheduler _scheduler;

    @Nullable private Thread _eventLoopThread;

    public EventQueueBasedEventLoop(BlockingPrioQueue<IEvent> eq)
    {
        this._eq = eq;
        this._scheduler = new Scheduler(_eq, "tp-sched");
    }

    @Override
    public final void start_()
    {
        boolean previouslyStarted = _started.getAndSet(true);
        if (previouslyStarted) return;

        _eventLoopThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                OutArg<Prio> evPrio = new OutArg<Prio>();
                while (true) {
                    IEvent ev = _eq.dequeue(evPrio);
                    _dispatcher.dispatch_(ev, evPrio.get());
                }
            }
        }, "tp");

        _eventLoopThread.start();
    }

    private final void execute_(final Runnable runnable, Prio pri)
    {
        try {
            _eq.enqueueThrows(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    runnable.run();
                }
            }, pri);
        } catch (ExNoResource e) {
            l.warn("fail enq ev - resched for immediate ex");
            executeAfterDelay_(runnable, 0);
        }
    }

    @Override
    public final void execute(Runnable runnable, Prio pri)
    {
        execute_(runnable, pri);
    }

    @Override
    public final void execute(Runnable runnable)
    {
        execute(runnable, Prio.LO);
    }

    private void executeAfterDelay_(final Runnable runnable, long delayInMilliseconds)
    {
        _scheduler.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                runnable.run();
            }
        }, delayInMilliseconds);
    }

    @Override
    public final void executeAfterDelay(Runnable runnable, long delayInMilliseconds)
    {
        executeAfterDelay_(runnable, delayInMilliseconds);
    }

    /**
     * Asserts that the current method <em>is</em> being called from within the {@link IEventLoop}
     * thread
     */
    @Override
    public final void assertEventThread()
    {
        assert _eventLoopThread != null;
        assert Thread.currentThread() == _eventLoopThread;
    }

    /**
     * Asserts that the current method <em>is not</em> being called from within the {@link
     * IEventLoop} thread
     */
    @Override
    public final void assertNonEventThread()
    {
        assert _eventLoopThread != null;
        assert Thread.currentThread() != _eventLoopThread;
    }

    @Override
    public final void dumpStat(Files.PBDumpStat template, Files.PBDumpStat.Builder bd)
            throws Exception
    {
        // AAG FIXME: implement
    }

    @Override
    public final void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        // AAG FIXME: implement
    }
}
