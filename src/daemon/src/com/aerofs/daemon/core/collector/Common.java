package com.aerofs.daemon.core.collector;

import java.sql.SQLException;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.lib.id.SOCID;

public class Common
{
    /**
     * @return whether the the collector should avoid downloading the SOCID even if it's in the CS
     * table.
     */
    public static boolean shouldSkip_(NativeVersionControl nvc, DirectoryService ds, SOCID socid)
            throws SQLException
    {
        if (nvc.getKMLVersion_(socid).isZero_()) {
            // skip components with zero KML
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
            OA oa = ds.getOANullable_(socid.soid());
            return (oa != null && oa.isExpelled());
        }
    }
}
