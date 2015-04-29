package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.polaris.fetch.ChangeFetchScheduler;
import com.aerofs.daemon.core.polaris.fetch.ContentFetcher;
import com.aerofs.daemon.core.polaris.submit.ContentChangeSubmitter;
import com.aerofs.daemon.core.polaris.submit.MetaChangeSubmitter;
import com.aerofs.daemon.core.polaris.submit.SubmissionScheduler;
import com.aerofs.daemon.core.polaris.submit.Submitter;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import java.sql.SQLException;

public class PolarisStore extends ReadOnlyPolarisStore
{
    private final SubmissionScheduler<MetaChangeSubmitter> _mcss;
    private final SubmissionScheduler<ContentChangeSubmitter> _ccss;

    public static class Factory extends ReadOnlyPolarisStore.Factory
    {
        @Inject private SubmissionScheduler.Factory<MetaChangeSubmitter> _factMCSS;
        @Inject private SubmissionScheduler.Factory<ContentChangeSubmitter> _factCCSS;

        public Store create_(SIndex sidx) throws SQLException
        {
            return new PolarisStore(this, sidx, _factCFS.create(sidx), _factCF.create_(sidx));
        }
    }

    private PolarisStore(Factory f, SIndex sidx, ChangeFetchScheduler cfs, ContentFetcher cf)
            throws SQLException
    {
        super(f, sidx, cfs, cf);
        _mcss = f._factMCSS.create(sidx);
        _ccss = f._factCCSS.create(sidx);
    }

    public SubmissionScheduler<MetaChangeSubmitter> metaSubmitter()
    {
        return _mcss;
    }

    public SubmissionScheduler<ContentChangeSubmitter> contentSubmitter()
    {
        return _ccss;
    }

    @Override
    public void onPauseSync_()
    {
        super.onPauseSync_();
       _mcss.stop_();
       _ccss.stop_();
    }

    @Override
    public void onResumeSync_()
    {
        super.onResumeSync_();
        _mcss.start_();
        _ccss.start_();
    }

    @Override
    void postCreate_()
    {
        // start submitting local updates to polaris
        _mcss.start_();
        _ccss.start_();

        super.postCreate_();
    }

    @Override
    void preDelete_()
    {
        // stop submitting local updates to polaris
        _mcss.stop_();
        _ccss.stop_();

        super.preDelete_();
    }
}
