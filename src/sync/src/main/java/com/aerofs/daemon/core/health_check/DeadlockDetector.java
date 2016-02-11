/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.health_check;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.lib.SystemUtil;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import static com.aerofs.defects.Defects.newMetric;

public final class DeadlockDetector implements HealthCheckService.ScheduledRunnable
{
    private static final Logger l = Loggers.getLogger(DeadlockDetector.class);

    private final ThreadMXBean _threadMXBean = ManagementFactory.getThreadMXBean();

    @Override
    public long interval()
    {
        return 2 * C.MIN;
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
        ThreadInfo[] deadlockedThreadsFullStacks = _threadMXBean.getThreadInfo(deadlockedTids, Integer.MAX_VALUE);
        ThreadInfo[] allThreads = _threadMXBean.dumpAllThreads(true, true);

        String threadStacks = constructErrorMessage(deadlockedThreads, deadlockedThreadsFullStacks, allThreads);
        l.error("\n\n==== BEGIN DEADLOCK INFO ====\n{}\n==== END DEADLOCK_INFO ====", threadStacks);

        // NOTE: I want to send this in a blocking fashion so
        // that we get the data to RockLog before the system goes down
        newMetric("daemon.deadlock")
                .addData("threads", threadStacks)
                .sendSyncIgnoreErrors();

        // this makes a synchronous call to SV under the hood
        SystemUtil.fatal("deadlock");
    }

    private String constructErrorMessage(ThreadInfo[] deadlockedThreads, ThreadInfo[] deadlockedThreadsFullStacks, ThreadInfo[] allThreads)
    {
        StringBuilder builder = new StringBuilder(1024);

        builder.append("==== BEGIN DEADLOCKED THREADS ====").append("\n");
        for (ThreadInfo info : deadlockedThreads) {
            builder.append(info.toString()).append("\n");
        }
        builder.append("==== END DEADLOCKED THREADS ====");
        builder.append("\n").append("\n");

        builder.append("==== BEGIN DEADLOCKED THREADS STACKS ====").append("\n");
        for (ThreadInfo info : deadlockedThreadsFullStacks) {
            builder.append(info.toString()).append("\n");
        }
        builder.append("==== END DEADLOCKED THREADS STACKS ====");
        builder.append("\n").append("\n");

        builder.append("==== BEGIN ALL THREADS ====").append("\n");
        for (ThreadInfo info : allThreads) {
            builder.append(info.toString()).append("\n");
        }
        builder.append("==== END ALL THREADS ====");
        return builder.toString();
    }
}
