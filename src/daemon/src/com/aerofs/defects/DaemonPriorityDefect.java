/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.daemon.ritual.RitualService;
import com.aerofs.proto.Diagnostics.PBDumpStat;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;

import java.util.concurrent.Executor;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

public class DaemonPriorityDefect extends PriorityDefect
{
    private final RitualService _ritual;

    protected DaemonPriorityDefect(InjectableSPBlockingClientFactory spFactory, Executor executor,
            RitualService ritual)
    {
        super(spFactory, executor);

        _ritual = ritual;
    }

    @Override
    protected void logThreadsImpl() throws Exception
    {
        _ritual.logThreads().get();
    }

    @Override
    protected PBDumpStat getDaemonStatusImpl(PBDumpStat template) throws Exception
    {
        return _ritual.dumpStats(template).get().getStats();
    }

    public static PriorityDefect newDaemonPriorityDefect(RitualService ritual, Executor executor)
    {
        return new DaemonPriorityDefect(newMutualAuthClientFactory(), executor, ritual);
    }
}
