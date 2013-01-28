/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.base.IEventLoop;
import com.aerofs.proto.Files;

import java.io.PrintStream;

public class DropDelayedInlineEventLoop implements IEventLoop
{
    private final ISingleThreadedPrioritizedExecutor _executor = new DropDelayedInlineExecutor();

    @Override
    public void start_()
    {
        // noop
    }

    @Override
    public void assertEventThread()
    {
        // noop
    }

    @Override
    public void assertNonEventThread()
    {
        // noop
    }

    @Override
    public void dumpStat(Files.PBDumpStat template, Files.PBDumpStat.Builder bd)
            throws Exception
    {
        // noop
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        // noop
    }

    @Override
    public void execute(Runnable runnable, Prio pri)
    {
        _executor.execute(runnable, pri);
    }

    @Override
    public void execute(Runnable runnable)
    {
        _executor.execute(runnable);
    }

    @Override
    public void executeAfterDelay(Runnable runnable, long delayInMilliseconds)
    {
        _executor.executeAfterDelay(runnable, delayInMilliseconds);
    }
}
