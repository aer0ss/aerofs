package com.aerofs.daemon.core.mock.logical;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;

import java.io.File;
import java.sql.SQLException;

/**
 * See MockRoot for usage
 */
public class LogicalObjectsPrinter
{
    public static void printRecursively(DirectoryService ds) throws SQLException, ExNotFound, ExNotDir
    {
        printRecursively(ds.resolveNullable_(new Path()), ds);
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
            Util.l().info(str);
            soidParent = null;
        } else if (oa.isDir()) {
            // don't print the trailing slash for the root directory
            Util.l().info(str + (str.endsWith(File.separator) ? "" : File.separator));
            soidParent = oa.soid();
        } else {
            assert oa.isAnchor();
            Util.l().info(str + "*");
            soidParent = ds.followAnchorNullable_(oa);
        }

        if (soidParent != null) {
            for (OID child : ds.getChildren_(soidParent)) {
                printRecursively(new SOID(soidParent.sidx(), child), ds);
            }
        }
    }

}
