/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.launch_tasks;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ui.UIGlobals;

/**
 * Tasks to be run on every launch.
 * They will all be scheduled after daemon launch and run under exponential retry
 * They should be idempotent and ideally exit quickly if their purpose has already been fulfilled
 */
public class UILaunchTasks
{
    private final UILaunchTask[] _tasks;

    public UILaunchTasks()
    {
        _tasks = new UILaunchTask[] {
                new ULTRecertifyDevice(UIGlobals.scheduler(), Cfg.user(), Cfg.did())
        };
    }

    /**
     * This method is called from a non-UI thread
     */
    public void runAll()
    {
        for (UILaunchTask lt : _tasks) {
            lt.schedule();
        }
    }
}
