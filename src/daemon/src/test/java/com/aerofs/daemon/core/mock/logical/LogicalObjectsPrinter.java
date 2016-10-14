package com.aerofs.daemon.core.mock.logical;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOKID;
import org.slf4j.Logger;

import java.io.File;
import java.sql.SQLException;
import java.util.Map.Entry;

/**
 * See MockRoot for usage
 */
public class LogicalObjectsPrinter
{
    private static final Logger l = Loggers.getLogger(LogicalObjectsPrinter.class);

    public static void printRecursively(SID sid, DirectoryService ds)
            throws SQLException, ExNotFound, ExNotDir
    {
        printRecursively(ds.resolveNullable_(Path.root(sid)), ds);
    }

    private static void printRecursively(SOID soid, DirectoryService ds)
            throws SQLException, ExNotFound, ExNotDir
    {
        OA oa = ds.getOANullable_(soid);
        assert oa.soid().equals(soid);
        Path path = ds.resolve_(oa);

        String str = oa.soid() + (oa.isExpelled() ? " X " : " - ") + path;
        SOID soidParent;    // not null to recurse down to children
        if (oa.isFile()) {
            for (Entry<KIndex, CA> e : oa.cas().entrySet()) {
                str += " " + e.getKey() + ":{" + e.getValue()
                        + "," + ds.getCAHash_(new SOKID(soid, e.getKey())) + "}";
            }
            l.info(str);
            soidParent = null;
        } else if (oa.isDir()) {
            // don't print the trailing slash for the root directory
            l.info(str + (str.endsWith(File.separator) ? "" : File.separator));
            soidParent = oa.soid();
        } else {
            assert oa.isAnchor();
            l.info(str + "*");
            soidParent = ds.followAnchorNullable_(oa);
        }

        if (soidParent != null) {
            for (OID child : ds.getChildren_(soidParent)) {
                printRecursively(new SOID(soidParent.sidx(), child), ds);
            }
        }
    }

}
