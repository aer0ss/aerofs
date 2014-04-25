package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.phy.linked.db.LinkedStorageSchema;
import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.injectable.InjectableDriver;

import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * At last we no longer cater to the lowest common denominator.
 *
 * To resolve long-standing issues with case sensitive filenames on Linux and
 * invalid filenames on Windows, a comprehensive solution was adopted to
 * decouple logical and physical path.
 */
public class DPUTCaseSensitivityHellYeah implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;
    private final InjectableDriver _dr;

    private static final String T_OA_OLD = T_OA + "old";

    DPUTCaseSensitivityHellYeah(CoreDBCW dbcw, InjectableDriver dr)
    {
        _dbcw = dbcw.get();
        _dr = dr;
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, new IDatabaseOperation() {
            @Override
            public void run_(Statement s) throws SQLException
            {
                // Update collation of the name column in the OA table
                // Unfortunately, SQlite does not offer a way to alter existing columns, hence the
                // following gymnastic:
                //      - rename old OA table
                //      - drop old indices to avoid name conflcits
                //      - re-create fresh OA table and assorted indices with appropriate collation
                //      - copy content form old to new table
                //      - delete old table
                //      - vacuum
                s.executeUpdate("alter table " + T_OA + " rename to " + T_OA_OLD);
                s.executeUpdate("drop index " + T_OA + "0");
                s.executeUpdate("drop index " + T_OA + "1");
                CoreSchema.createOATableAndIndices_(s, _dbcw, _dr);

                s.executeUpdate("insert into " + T_OA
                        + " select "
                        + C_OA_SIDX + ","
                        + C_OA_OID + ","
                        + C_OA_NAME + ","
                        + C_OA_PARENT + ","
                        + C_OA_TYPE + ", "
                        + C_OA_FID + ","
                        + C_OA_FLAGS
                        + " from "
                        + T_OA_OLD
                        );

                s.executeUpdate("drop table " + T_OA_OLD);

                // sigh, cannot be done within a transaction
                //s.executeUpdate("vacuum");

                // create new tables used to handle NROs
                if (Cfg.storageType() == StorageType.LINKED) {
                    new LinkedStorageSchema().create_(s, _dbcw);
                }
            }
        });
    }
}
