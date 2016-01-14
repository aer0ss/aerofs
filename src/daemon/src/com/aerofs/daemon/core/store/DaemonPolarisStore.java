package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.IVersionUpdater;
import com.aerofs.daemon.core.collector.Collector2;
import com.aerofs.daemon.core.collector.SenderFilters;
import com.aerofs.daemon.core.polaris.fetch.ChangeFetchScheduler;
import com.aerofs.daemon.core.polaris.submit.MetaChangeSubmitter;
import com.aerofs.daemon.core.polaris.submit.SubmissionScheduler;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import java.sql.SQLException;

public class DaemonPolarisStore extends PolarisStore
{
    private final SubmissionScheduler<MetaChangeSubmitter> _mcss;

    public static class Factory extends PolarisStore.Factory
    {
        @Inject private SubmissionScheduler.Factory<MetaChangeSubmitter> _factMCSS;
        @Inject private IVersionUpdater _vu;

        public Store create_(SIndex sidx) throws SQLException
        {
            return new DaemonPolarisStore(this, sidx, _factCFS.create(sidx), _factCF.create_(sidx),
                    _factSF.create_(sidx), _vu);
        }
    }

    private DaemonPolarisStore(Factory f, SIndex sidx, ChangeFetchScheduler cfs, Collector2 cf,
                               SenderFilters sf, IVersionUpdater vu) throws SQLException
    {
        super(f, sidx, cfs, cf, sf, vu);
        _mcss = f._factMCSS.create(sidx);
        vu.addListener_((k, t) -> {
            if (k.sidx().equals(sidx) && k.cid().isMeta()) _mcss.startOnCommit_(t);
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
    }

    @Override
    public void onResumeSync_()
    {
        super.onResumeSync_();
        _mcss.start_();
    }

    @Override
    public void startSubmissions() {
        super.startSubmissions();
        _mcss.start_();
    }
}
