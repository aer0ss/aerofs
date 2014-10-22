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

public class SubmissionScheduler<T extends Submitter>
{
    public static class Factory<T extends Submitter>
    {
        private final CoreScheduler _sched;
        private final VersionUpdater _vu;
        private final T _submitter;

        @Inject
        public Factory(CoreScheduler sched, VersionUpdater vu, T submitter)
        {
            _sched = sched;
            _vu = vu;
            _submitter = submitter;
        }

        public SubmissionScheduler<T> create(SIndex sidx)
        {
            return new SubmissionScheduler<>(this, sidx);
        }
    }

    private final Factory<T> _f;
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

    private SubmissionScheduler(Factory<T> f, SIndex sidx)
    {
        _f = f;
        _sidx = sidx;
        _sched = new AsyncWorkScheduler(name(), _f._sched, cb -> {
            try {
               _f._submitter.submit_(_sidx, cb);
            } catch (SQLException e) {
                cb.onFailure_(e);
            }
        });

        // TODO: META/CONTENT split?
        _f._vu.addListener_((soid, t) -> startOnCommit_(t));
    }

    private String name()
    {
        return _f._submitter.name() + "[" +  _sidx + "]";
    }

    public void start_()
    {
        _sched.schedule_();
    }

    public void startOnCommit_(Trans t)
    {
        _tlSubmit.get(t);
    }

    public void stop_()
    {
        _sched.stop_();
    }
}
