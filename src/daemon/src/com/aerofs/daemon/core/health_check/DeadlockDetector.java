/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.health_check;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.DaemonDefects;
import com.aerofs.lib.SystemUtil;
import com.aerofs.rocklog.Defect;
import com.aerofs.rocklog.RockLog;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

final class DeadlockDetector implements Runnable
{
    private static final Logger l = Loggers.getLogger(DeadlockDetector.class);

    private final ThreadMXBean _threadMXBean = ManagementFactory.getThreadMXBean();
    private final RockLog _rockLog;

    @Inject
    DeadlockDetector(RockLog rockLog)
    {
        _rockLog = rockLog;
    }

    @Override
    public void run()
    {
        try {
            l.info("run dld");
            checkForDeadlocks();
        } catch (Throwable t) {
            l.error("fail check deadlocks", t);
        }
    }

    private void checkForDeadlocks()
    {
        long[] deadlockedTids = _threadMXBean.findDeadlockedThreads();
        if (deadlockedTids == null || deadlockedTids.length == 0) return;

        l.error("DEADLOCK DETECTED - FML");

        ThreadInfo[] deadlockedThreads = _threadMXBean.getThreadInfo(deadlockedTids, true, true);
        ThreadInfo[] allThreads = _threadMXBean.dumpAllThreads(true, true);

        String threadStacks = constructErrorMessage(deadlockedThreads, allThreads);
        l.error("\n\n==== BEGIN DEADLOCK INFO ====\n{}\n==== END DEADLOCK_INFO ====", threadStacks);

        // NOTE: I want to send this in a blocking fashion so
        // that we get the data to RockLog before the system goes down
        Defect deadlockDefect = _rockLog.newDefect(DaemonDefects.DAEMON_DEADLOCK);
        deadlockDefect.addData("threads", threadStacks);
        deadlockDefect.sendBlocking();

        // this makes a synchronous call to SV under the hood
        SystemUtil.fatal("deadlock");
    }

    private String constructErrorMessage(ThreadInfo[] deadlockedThreads, ThreadInfo[] allThreads)
    {
        StringBuilder builder = new StringBuilder(1024);

        builder.append("==== BEGIN DEADLOCKED THREADS ====").append("\n");
        for (ThreadInfo info : deadlockedThreads) {
            builder.append(info.toString()).append("\n");
        }
        builder.append("==== END DEADLOCKED THREADS ====");

        builder.append("\n").append("\n");

        builder.append("==== BEGIN ALL THREADS ====").append("\n");
        for (ThreadInfo info : allThreads) {
            builder.append(info.toString()).append("\n");
        }
        builder.append("==== END ALL THREADS ====");
        return builder.toString();
    }
}
