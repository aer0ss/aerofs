package com.aerofs.daemon.core.admin;

import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.analytics.AnalyticsEvents.SimpleEvents;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.event.admin.EISetExpelled;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
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

    @Inject
    public HdSetExpelled(Expulsion expulsion, DirectoryService ds, TransManager tm, Analytics analytics)
    {
        _expulsion = expulsion;
        _ds = ds;
        _tm = tm;
        _analytics = analytics;
    }

    @Override
    protected void handleThrows_(EISetExpelled ev, Prio prio) throws Exception
    {
        SOID soid = _ds.resolveThrows_(ev._path);
        Trans t = _tm.begin_();
        Throwable rollbackCause = null;
        _analytics.track(ev._expelled ? SimpleEvents.EXCLUDE_FOLDER : SimpleEvents.INCLUDE_FOLDER);
        try {
            _expulsion.setExpelled_(ev._expelled, soid, t);
            t.commit_();

        // See {@link com.aerofs.daemon.lib.db.trans.Trans#end_()} for the reason of these blocks
        } catch (Exception e) {
            rollbackCause = e;
            throw e;
        } catch (Error e) {
            rollbackCause = e;
            throw e;
        } finally {
            t.end_(rollbackCause);
        }
    }
}
