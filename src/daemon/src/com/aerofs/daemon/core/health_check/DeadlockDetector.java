/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.health_check;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.lib.SystemUtil;
import com.aerofs.sv.client.SVClient;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

final class DeadlockDetector implements Runnable
{
    private static final Logger l = Loggers.getLogger(DeadlockDetector.class);

    private final ThreadMXBean _threadMXBean = ManagementFactory.getThreadMXBean();

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

        String errorMesage = constructErrorMessage(deadlockedThreads, allThreads);
        l.error("\n\n==== BEGIN DEADLOCK INFO ====\n{}\n==== END DEADLOCK_INFO ====", errorMesage);

        SVClient.logSendDefectSyncIgnoreErrors(true, "deadlock", new ExTimeout("deadlock"));
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
