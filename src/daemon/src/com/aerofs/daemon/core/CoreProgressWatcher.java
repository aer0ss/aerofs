/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core;

import com.aerofs.base.C;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.lib.Param.Daemon;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExTimeout;
import com.aerofs.sv.client.SVClient;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import static com.aerofs.lib.ThreadUtil.sleepUninterruptable;
import static com.aerofs.lib.ThreadUtil.startDaemonThread;

final class CoreProgressWatcher implements IStartable
{
    private static final int INTERVAL_BETWEEN_CHECKS = (int) (Daemon.HEARTBEAT_INTERVAL / 5);
    private static final int INITIAL_DELAY = (int) (30 * C.SEC);

    static {
        assert INTERVAL_BETWEEN_CHECKS > 0 && INITIAL_DELAY > 0;
    }

    private static final Logger l = Util.l(CoreProgressWatcher.class);

    private final CoreEventDispatcher _disp;

    private long _prevNumExecutedEvents = Long.MIN_VALUE; // only touched by "progress-watcher"

    @Inject
    CoreProgressWatcher(CoreEventDispatcher disp)
    {
        this._disp = disp;
    }

    @Override
    public void start_()
    {
        l.info("start cpw init:" + INITIAL_DELAY + " intv:" + INTERVAL_BETWEEN_CHECKS);

        startDaemonThread("progress-watcher", new Runnable()
        {
            @Override
            public void run()
            {
                sleepUninterruptable(INITIAL_DELAY);

                while (true) {
                    checkDaemon_();
                    sleepUninterruptable(INTERVAL_BETWEEN_CHECKS);
                }
            }
        });
    }

    private void checkDaemon_()
    {
        long currNumExecutedEvents = _disp.getExecutedEventCount();

        if (currNumExecutedEvents != _prevNumExecutedEvents) {
            _prevNumExecutedEvents = currNumExecutedEvents;

        } else if (_disp.getCurrentEventNullable() != null) {
            l.warn("no progress after n:" + _prevNumExecutedEvents + " ev:" +
                    _disp.getCurrentEventNullable());
            // dump thread stacks three times so we can see which thread is not making progress.
            for (int i = 0; i < 3; i++) {
                if (i != 0) ThreadUtil.sleepUninterruptable(3 * C.SEC);
                Util.logAllThreadStackTraces();
            }

            SVClient.logSendDefectSyncIgnoreErrors(true, "stuck daemon",
                    new ExTimeout("stuck daemon"));

            SystemUtil.fatal("stuck daemon");
        }
    }
}
