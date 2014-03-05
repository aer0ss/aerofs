/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.launch_tasks;

import com.aerofs.lib.guice.GuiceUtil;
import com.google.inject.Binder;

import javax.inject.Inject;
import java.util.Set;

/**
 * This class is very similar to UILaunchTasks except that this class is more suitable for daemon
 * specific tasks, and the tasks are run in the core thread.
 */
public class DaemonLaunchTasks
{
    private final Set<DaemonLaunchTask> _tasks;

    @Inject
    public DaemonLaunchTasks(Set<DaemonLaunchTask> tasks)
    {
        _tasks = tasks;
    }

    /**
     * DaemonLaunchTasks can be run in any order so we use a set binder to simplify their
     * instantiation. However we don't want to leak the specific classes outside the package
     * hence the use of a static method.
     */
    public static void bindTasks(Binder binder)
    {
        GuiceUtil.multibind(binder, DaemonLaunchTask.class, DLTFetchStoreNames.class);
        GuiceUtil.multibind(binder, DaemonLaunchTask.class, DLTCleanActivityLog.class);
    }

    public void run()
    {
        for (DaemonLaunchTask task : _tasks) task.schedule();
    }
}
