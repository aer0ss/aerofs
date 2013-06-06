/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.IStartable;
import com.google.inject.Inject;

public interface ILinker extends IStartable
{
    public void init_();

    public void scan(ScanCompletionCallback callback);

    /**
     * @return whether an initial scan is needed to safely restore state upon reinstall
     *
     * NB: this should only be called once (on the first launch of a freshly installed client)
     *
     * See also:
     * {@link com.aerofs.daemon.core.first_launch.FirstLaunch}
     * {@link com.aerofs.daemon.core.first_launch.OIDGenerator}
     */
    boolean firstScanNeeded();

    public static class NullLinker implements ILinker
    {
        private final CoreScheduler _sched;

        @Inject
        public NullLinker(CoreScheduler sched)
        {
            _sched = sched;
        }

        @Override
        public void init_() {}

        @Override
        public void start_() {}

        @Override
        public boolean firstScanNeeded() { return false; }

        @Override
        public void scan(final ScanCompletionCallback callback)
        {
            _sched.schedule(new AbstractEBSelfHandling() {
                @Override
                public void handle_()
                {
                    callback.done_();
                }
            }, 0);
        }
    }
}
