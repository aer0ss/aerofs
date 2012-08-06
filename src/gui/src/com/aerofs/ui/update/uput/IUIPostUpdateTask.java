package com.aerofs.ui.update.uput;

public interface IUIPostUpdateTask
{
    /**
     * this is called within a non-UI thread, after the daemon has been launched, and before other
     * methods of this interface are called. It's also called after DaemonPostUpdateTasks.
     *
     * REQUIREMENT: the method must be idempotent. That is, calling it multiple times must not cause
     * harmful effects.
     */
    void run() throws Exception;

    /**
     * called *after* run() is called
     */
    boolean isRebootSuggested();

    /**
     * called *after* run() is called.
     */
    boolean isShutdownRequired();

    /**
     * called *after* run() is called
     *
     * @return null for no notes
     */
    String[] getNotes();
}
