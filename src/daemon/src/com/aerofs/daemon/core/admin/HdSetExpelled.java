package com.aerofs.daemon.core.admin;

import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.analytics.AnalyticsEvents.SimpleEvents;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.event.admin.EISetExpelled;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdSetExpelled extends AbstractHdIMC<EISetExpelled>
{
    private final Expulsion _expulsion;
    private final DirectoryService _ds;
    private final TransManager _tm;
    private final Analytics _analytics;
    private final IPhysicalStorage _ps;

    @Inject
    public HdSetExpelled(Expulsion expulsion, DirectoryService ds, IPhysicalStorage ps,
            TransManager tm, Analytics analytics)
    {
        _expulsion = expulsion;
        _ds = ds;
        _ps = ps;
        _tm = tm;
        _analytics = analytics;
    }

    @Override
    protected void handleThrows_(EISetExpelled ev) throws Exception
    {
        SOID soid = _ds.resolveThrows_(ev._path);
        Trans t = _tm.begin_();
        _analytics.track(ev._expelled ? SimpleEvents.EXCLUDE_FOLDER : SimpleEvents.INCLUDE_FOLDER);
        try {
            // explicit expulsion is used to save space so we should not simply move files to rev
            // but completely delete them
            _ps.discardRevForTrans_(t);

            _expulsion.setExpelled_(ev._expelled, soid, t);
            t.commit_();
        } catch (Exception|Error e) {
            l.warn("rollback triggered ", e);
            throw e;
        } finally {
            t.end_();
        }
    }
}
