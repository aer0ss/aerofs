package com.aerofs.ui;

import com.aerofs.lib.DelayedRunner;

// TODO remove this class after "throttled" ritual notification has been implemented

public class DelayedUIRunner {

    private final DelayedRunner _runner;

    public DelayedUIRunner(final Runnable activity)
    {
        this(UIParam.DEFAULT_REFRESH_DELAY, activity);
    }

    /**
     * @param run it will always run by the UI thread. N.B. must check if the
     * widget to refresh has been disposed.
     */
    public DelayedUIRunner(final int delay, final Runnable activity)
    {
        final Runnable runnable1 = new Runnable() {
            @Override
            public void run()
            {
                _runner.execute();
            }
        };

        final Runnable runnable2 = new Runnable() {
            @Override
            public void run()
            {
                UI.get().timerExec(delay, runnable1);
            }
        };

        _runner = new DelayedRunner(activity, new DelayedRunner.IExecutor() {
                @Override
                public void execute(final DelayedRunner runner)
                {
                    assert _runner == runner;
                    UI.get().asyncExec(runnable2);
                }
            });
    }

    public void schedule()
    {
        _runner.schedule();
    }
}
