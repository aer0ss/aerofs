package com.aerofs.gui;

import java.util.concurrent.Executor;

import org.eclipse.swt.widgets.Widget;

import javax.annotation.Nonnull;

/**
 * An executor that runs everything in the GUI thread.
 */
public class GUIExecutor implements Executor
{
    private final Widget _w;

    /**
     * @param w the executor will run the runnable only  if w is still valid at the time of
     * execution.
     */
    public GUIExecutor(Widget w)
    {
        _w = w;
    }

    @Override
    public void execute(@Nonnull Runnable runnable)
    {
        GUI.get().safeAsyncExec(_w, runnable);
    }
}
