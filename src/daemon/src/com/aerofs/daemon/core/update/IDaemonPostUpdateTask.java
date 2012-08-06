package com.aerofs.daemon.core.update;

public interface IDaemonPostUpdateTask
{
    /**
     * This is called immediately after database is initialized and before other core components are
     * initialized. It's also called before UIPostUpdateTasks.
     *
     * REQUIREMENT: the implementation must be idempotent. That is, calling it multiple times must
     * not cause harmful effects.
     */
    void run() throws Exception;
}
