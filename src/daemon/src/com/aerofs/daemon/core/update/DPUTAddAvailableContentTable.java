package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;

import static com.aerofs.daemon.core.polaris.db.PolarisSchema.*;
import static com.aerofs.daemon.core.polaris.db.PolarisSchema.C_VERSION_OID;
import static com.aerofs.daemon.lib.db.CoreSchema.*;

public class DPUTAddAvailableContentTable implements IDaemonPostUpdateTask {
    @Inject private IDBCW _dbcw;

    @Override
    public void run() throws Exception {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            if (_dbcw.tableExists(T_AVAILABLE_CONTENT)) {
                s.execute("delete from " + T_AVAILABLE_CONTENT);
            } else {
                createAvailableContentTable(s, _dbcw);
            }

            s.execute("insert into " + T_AVAILABLE_CONTENT + "("
                    + C_AVAILABLE_CONTENT_SIDX + ","
                    + C_AVAILABLE_CONTENT_OID + ","
                    + C_AVAILABLE_CONTENT_VERSION + ")"
                    + " select "
                    + C_VERSION_SIDX + ","
                    + C_VERSION_OID + ","
                    + C_VERSION_TICK
                    + " from " + T_VERSION
                    + " join " + T_OA
                    + " on " + C_VERSION_SIDX + "=" + C_OA_SIDX
                    + " and " + C_VERSION_OID + "=" + C_OA_OID
                    + " where " + C_OA_TYPE + "=" + OA.Type.FILE.ordinal());
        });
    }
}
