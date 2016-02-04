/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.daemon.core.polaris.async.AsyncWorkGroupScheduler;
import com.aerofs.daemon.core.polaris.async.AsyncWorkGroupScheduler.TaskState;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;


public class SubmissionScheduler<T extends Submitter>
{
    public static class Factory<T extends Submitter>
    {
        private final AsyncWorkGroupScheduler _sched;
        private final T _submitter;

        @Inject
        public Factory(AsyncWorkGroupScheduler sched, T submitter)
        {
            _sched = sched;
            _submitter = submitter;
        }

        public SubmissionScheduler<T> create(SIndex sidx)
        {
            return new SubmissionScheduler<>(this, sidx);
        }
    }

    private final Factory<T> _f;
    private final SIndex _sidx;
    private final TaskState _sched;

    private final TransLocal<Void> _tlSubmit = new TransLocal<Void>() {
        @Override
        protected Void initialValue(Trans t)
        {
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_()
                {
                    _sched.schedule_();
                }
            });
            return null;
        }
    };

    private SubmissionScheduler(Factory<T> f, SIndex sidx)
    {
        _f = f;
        _sidx = sidx;
        _sched = _f._sched.register_(name(), cb -> {
            try {
               _f._submitter.submit_(_sidx, cb);
            } catch (Exception e) {
                cb.onFailure_(e);
            }
        });
    }

    private String name()
    {
        return _f._submitter.name() + "[" +  _sidx + "]";
    }

    public void start_()
    {
        _sched.start_();
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
