package com.aerofs.lib;


/**
 * All methods in this class are thread-safe
 */

// TODO remove this class after "throttled" ritual notification has been implemented

public class DelayedRunner {

    private final Object _lock = new Integer(0);
    private final Runnable _activity;
    private final IExecutor _executor;
    private boolean _scheduled;
    private boolean _ongoing;

    public static interface IExecutor {
        /**
         * wait for a certain amount of time, and then call runner.execute(),
         * which in turn calls activity in the same thread
         */
        void execute(DelayedRunner runner);
    }

    public DelayedRunner(final String name, final long delay, Runnable activity)
    {
        _activity = activity;
        _executor = new IExecutor() {
            @Override
            public void execute(final DelayedRunner runner)
            {
                new Thread(new Runnable() {
                    @Override
                    public void run()
                    {
                        ThreadUtil.sleepUninterruptable(delay);
                        runner.execute();
                    }
                }, name).start();
            }
        };
    }

    public void schedule()
    {
        synchronized (_lock) {
            if (_scheduled) return;
            _scheduled = true;
            if (_ongoing) return;
        }

        _executor.execute(this);
    }

    public void execute()
    {
        synchronized (_lock) {
            _scheduled = false;
            _ongoing = true;
        }

        _activity.run();

        synchronized (_lock) {
            _ongoing = false;
            if (_scheduled) _executor.execute(this);
        }
    }
}
