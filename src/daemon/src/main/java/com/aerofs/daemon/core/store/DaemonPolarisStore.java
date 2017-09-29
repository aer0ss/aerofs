package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.IVersionUpdater;
import com.aerofs.daemon.core.collector.ContentFetcher;
import com.aerofs.daemon.core.collector.SenderFilters;
import com.aerofs.daemon.core.notification.ISyncNotificationSubscriber;
import com.aerofs.daemon.core.polaris.fetch.ChangeFetchScheduler;
import com.aerofs.daemon.core.polaris.submit.MetaChangeSubmitter;
import com.aerofs.daemon.core.polaris.submit.SubmissionScheduler;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import java.sql.SQLException;

public class DaemonPolarisStore extends PolarisStore
{
    private final SubmissionScheduler<MetaChangeSubmitter> _mcss;
    private final ISyncNotificationSubscriber _snsub;

    public static class Factory extends PolarisStore.Factory
    {
        @Inject private SubmissionScheduler.Factory<MetaChangeSubmitter> _factMCSS;
        @Inject private IVersionUpdater _vu;
        @Inject private ISyncNotificationSubscriber _snsub;

        @Override
        public Store create_(SIndex sidx) throws SQLException
        {
            return new DaemonPolarisStore(this, sidx, _factCFS.create(sidx), _factCF.create_(sidx),
                    _factSF.create_(sidx), _vu, _snsub);
        }
    }

    private DaemonPolarisStore(Factory f, SIndex sidx, ChangeFetchScheduler cfs, ContentFetcher cf,
                               SenderFilters sf, IVersionUpdater vu,
                               ISyncNotificationSubscriber snsub) throws SQLException
    {
        super(f, sidx, cfs, cf, sf, vu);
        _mcss = f._factMCSS.create(sidx);
        _snsub = snsub;
        vu.addListener_((socid, t) -> {
            if (socid.sidx().equals(sidx) && socid.cid().isMeta()) _mcss.startOnCommit_(t);
        });
    }

    public SubmissionScheduler<MetaChangeSubmitter> metaSubmitter()
    {
        return _mcss;
    }

    @Override
    public void onPauseSync_()
    {
        super.onPauseSync_();
        _mcss.stop_();
        _snsub.unsubscribe_(this);
    }

    @Override
    public void onResumeSync_()
    {
        super.onResumeSync_();
        _mcss.start_();
        _snsub.subscribe_(this);
    }

    @Override
    public void startSubmissions() {
        super.startSubmissions();
        _mcss.schedule_();
    }
}