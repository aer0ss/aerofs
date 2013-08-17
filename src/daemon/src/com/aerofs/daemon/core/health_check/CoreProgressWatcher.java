/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.health_check;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.sv.client.SVClient;
import com.google.inject.Inject;
import org.slf4j.Logger;

final class CoreProgressWatcher implements Runnable
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
            checkProgress_();
        } catch (Throwable t) {
            l.error("fail check progress", t);
        }
    }

    private void checkProgress_()
    {
        if (!hasMadeProgress_() && !isIdle()) {
            l.warn("no progress after n:" + _prevNumExecutedEvents + " ev:" + _disp.getCurrentEventNullable());

            // dump thread stacks three times so we can see which thread is not making progress.
            for (int i = 0; i < 3; i++) {
                if (i != 0) ThreadUtil.sleepUninterruptable(3 * C.SEC);
                Util.logAllThreadStackTraces();
            }

            SVClient.logSendDefectSyncIgnoreErrors(true, "stuck daemon",
                    new ExTimeout("stuck daemon"));
            SystemUtil.fatal("stuck daemon");
        }

        _prevNumExecutedEvents = _disp.getExecutedEventCount();
        _prevNumWalkedObjects = _pi.getMonotonicProgress();
    }

    /**
     * @return whether any the daemon has made any sort of progress since the last check
     */
    private boolean hasMadeProgress_()
    {
        return _disp.getExecutedEventCount() != _prevNumExecutedEvents ||
                _pi.getMonotonicProgress() != _prevNumWalkedObjects ||
                _pi.hasInProgressSyscall();
    }

    private boolean isIdle()
    {
        return _disp.getCurrentEventNullable() == null;
    }
}
