package com.aerofs.daemon.core.update;

import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;

public class DPUTDropLegacyTables extends PhoenixDPUT {
    private @Inject IDBCW _dbcw;

    @Override
    public void runPhoenix() throws Exception {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            for (String table : ImmutableSet.of(
                    "v", "k", "iv", "ik", "t", "cs", "gt"
            )) {
                s.executeUpdate("drop table if exists " + table);
            }
            // TODO: vacuum?
        });
    }
}
