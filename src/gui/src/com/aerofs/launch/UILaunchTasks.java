/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.launch;

import com.aerofs.ui.UI;

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
                new UILTRecertifyDevice(UI.scheduler())
        };
    }
    public void runAll()
    {
        for (UILaunchTask lt : _tasks)
        {
            lt.schedule();
        }
    }
}
