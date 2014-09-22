/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.polaris.async.AsyncWorkScheduler;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import java.sql.SQLException;

/**
 * triggers:
 *    - startup
 *    - local change (batched, rate-limited)
 *    - ACL change (WRITE bit) TODO
 */
public class MetaChangeSubmissionScheduler
{
    public static class Factory
    {
        private final CoreScheduler _sched;
        private final VersionUpdater _vu;
        private final MetaChangeSubmitter _submitter;

        @Inject
        public Factory(CoreScheduler sched, VersionUpdater vu, MetaChangeSubmitter submitter)
        {
            _sched = sched;
            _vu = vu;
            _submitter = submitter;
        }

        public MetaChangeSubmissionScheduler create(SIndex sidx)
        {
            return new MetaChangeSubmissionScheduler(this, sidx);
        }
    }

    private final Factory _f;
    private final SIndex _sidx;
    private final AsyncWorkScheduler _sched;

    private final TransLocal<Void> _tlSubmit = new TransLocal<Void>() {
        @Override
        protected Void initialValue(Trans t)
        {
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_()
                {
                    start_();
                }
            });
            return null;
        }
    };

    private MetaChangeSubmissionScheduler(Factory f, SIndex sidx)
    {
        _f = f;
        _sidx = sidx;
        _sched = new AsyncWorkScheduler("submit[" +  _sidx + "]", _f._sched, cb -> {
           try {
               _f._submitter.submit_(_sidx, cb);
           } catch (SQLException e) {
               cb.onFailure_(e);
           }
        });

        _f._vu.addListener_((soid, t) -> _tlSubmit.get(t));
    }

    public void start_()
    {
        _sched.schedule_();
    }

    public void stop_()
    {
        _sched.stop_();
    }
}
