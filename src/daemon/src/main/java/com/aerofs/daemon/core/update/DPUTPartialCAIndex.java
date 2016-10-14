package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;

import static com.aerofs.daemon.lib.db.CoreSchema.T_CA;

public class DPUTPartialCAIndex implements IDaemonPostUpdateTask {
    @Inject IDBCW _dbcw;

    @Override
    public void run() throws Exception {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            s.execute("drop index " + T_CA + "0");
            CoreSchema.createPartialCAIndex(s);
        });
    }
}
