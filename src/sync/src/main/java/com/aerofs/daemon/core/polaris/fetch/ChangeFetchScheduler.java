/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.daemon.core.polaris.async.AsyncWorkGroupScheduler;
import com.aerofs.daemon.core.polaris.async.AsyncWorkGroupScheduler.TaskState;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

/**
 * Schedule fetch of changes for a given store.
 *
 * Repeated fetch until no more changes are available and exponential backoff in case of error.
 *
 * See {@link ChangeFetcher}
 */
public class ChangeFetchScheduler
{
    public static class Factory
    {
        private final ChangeFetcher _fetcher;
        private final AsyncWorkGroupScheduler _sched;

        @Inject
        public Factory(ChangeFetcher fetcher, AsyncWorkGroupScheduler sched)
        {
            _fetcher = fetcher;
            _sched = sched;
        }

        public ChangeFetchScheduler create(SIndex sidx)
        {
            return new ChangeFetchScheduler(this, sidx);
        }
    }

    private final Factory _f;
    private final SIndex _sidx;
    private final TaskState _sched;

    private ChangeFetchScheduler(Factory f, SIndex sidx)
    {
        _f = f;
        _sidx = sidx;
        _sched = f._sched.register_("fetch[" + _sidx + "]", cb -> {
            try {
                _f._fetcher.fetch_(_sidx, cb);
            } catch (Throwable t) {
               cb.onFailure_(t);
            }
        });
    }

    public void start_()
    {
        _sched.start_();
    }

    public void stop_()
    {
        _sched.stop_();
    }

    public void schedule_()
    {
        _sched.schedule_();
    }
}
