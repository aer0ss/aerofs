package com.aerofs.daemon.core.collector;

import java.sql.SQLException;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.lib.id.SOCID;
import com.google.inject.Inject;
import org.slf4j.Logger;

/**
 * Abstracts interaction with the version subsystem and logical FS away from {@link Collector} to
 * reduce coupling and make testing easier
 */
class CollectorSkipRule
{
    private static final Logger l = Loggers.getLogger(CollectorSkipRule.class);

    private final DirectoryService _ds;
    private final NativeVersionControl _nvc;

    @Inject
    public CollectorSkipRule(NativeVersionControl nvc, DirectoryService ds)
    {
        _ds = ds;
        _nvc = nvc;
    }

    /**
     * @return whether the the collector should avoid downloading the SOCID even if it's in the CS
     * table.
     */
    public boolean shouldSkip_(SOCID socid) throws SQLException
    {
        if (_nvc.getKMLVersion_(socid).isZero_()) {
            // skip components with zero KML
            l.debug("skip {}: no kml", socid);
            return true;
        } else if (socid.cid().isMeta()) {
            // never skip metadata
            return false;
        } else {
            // skip the content if the object is expelled. this won't avoid
            // downloading expelled objects entirely. Because 1) the OA might
            // not be present now, or 2) the object becomes expelled in the
            // middle of content download. These conditions will be checked
            // by the download subsystem. We do the check here to filter
            // out as many expelled files as possible before entering them
            // to the download subsystem.
            OA oa = _ds.getOANullable_(socid.soid());
            return (oa != null && oa.isExpelled());
        }
    }
}
