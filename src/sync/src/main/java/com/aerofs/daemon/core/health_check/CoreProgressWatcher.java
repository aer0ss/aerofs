/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.health_check;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.google.inject.Inject;
import org.slf4j.Logger;

import static com.aerofs.defects.Defects.newDefectWithLogs;

public final class CoreProgressWatcher implements HealthCheckService.ScheduledRunnable
{
    private static final Logger l = Loggers.getLogger(CoreProgressWatcher.class);

    private final CoreEventDispatcher _disp;
    private final ProgressIndicators _pi;

    private long _prevNumExecutedEvents = Long.MIN_VALUE; // only touched by "progress-watcher"
    private long _prevNumWalkedObjects = Long.MIN_VALUE;

    @Inject
    CoreProgressWatcher(CoreEventDispatcher disp)
    {
        _disp = disp;
        _pi = ProgressIndicators.get();  // sigh, this should be injected...
    }

    @Override
    public void run()
    {
        try {
            l.info("run cpw");
            if (l.isDebugEnabled() && _prevNumExecutedEvents == Long.MIN_VALUE) {
                Util.logAllThreadStackTraces();
            }
            checkProgress_();
        } catch (Throwable t) {
            l.error("fail check progress", t);
        }
    }

    private void checkProgress_()
    {
        if (!hasMadeProgress_() && !isIdle()) {
            l.warn("no progress after n:" + _prevNumExecutedEvents + " ev:" + _disp.getCurrentEvents());

            if (!_pi.hasInProgressSyscall()) {
                try {
                    // dump thread stacks three times to see which thread is not making progress
                    for (int i = 0; i < 3; i++) {
                        if (i != 0) ThreadUtil.sleepUninterruptable(3 * C.SEC);
                        Util.logAllThreadStackTraces();
                    }

                    newDefectWithLogs("core.progress")
                            .setMessage("stuck daemon")
                            .sendSyncIgnoreErrors();
                } finally {
                    // finally block is important to prevent corner cases where an OOM causes some
                    // but not all threads to die and somehow turns the process into a zombie
                    // this was for instance seen at spainwilliams where an OOM would rarely result
                    // in the Exit being inhibited somehow and the progress checker would repeatedly
                    // trigger but fail to kill the process as a NoClassDefFound error would happen
                    // when trying to submit a defect
                    SystemUtil.fatal("stuck daemon");
                }
            }
        }

        _prevNumExecutedEvents = _disp.getExecutedEventCount();
        _prevNumWalkedObjects = _pi.getMonotonicProgress();
    }

    /**
     * @return whether any the daemon has made any sort of progress since the last check
     */
    private boolean hasMadeProgress_()
    {
        return _disp.getExecutedEventCount() > _prevNumExecutedEvents ||
                _pi.getMonotonicProgress() > _prevNumWalkedObjects;
    }

    private boolean isIdle()
    {
        return _disp.getCurrentEvents().isEmpty();
    }
}
