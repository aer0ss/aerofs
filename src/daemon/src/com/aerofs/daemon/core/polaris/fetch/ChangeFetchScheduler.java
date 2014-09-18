/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.polaris.async.AsyncWorkScheduler;
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
        private final CoreScheduler _sched;

        @Inject
        public Factory(ChangeFetcher fetcher, CoreScheduler sched)
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
    private final AsyncWorkScheduler _sched;

    private ChangeFetchScheduler(Factory f, SIndex sidx)
    {
        _f = f;
        _sidx = sidx;
        _sched = new AsyncWorkScheduler("fetch[" + _sidx + "]", _f._sched, cb -> {
            try {
                _f._fetcher.fetch_(_sidx, cb);
            } catch (Throwable t) {
               cb.onFailure_(t);
            }
        });
    }

    public void schedule_()
    {
        _sched.schedule_();
    }

    public void stop_()
    {
        _sched.stop_();
    }
}
