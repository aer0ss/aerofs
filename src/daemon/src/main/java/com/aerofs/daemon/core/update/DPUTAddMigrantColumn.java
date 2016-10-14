package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.phy.linked.db.LinkedStorageSchema;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.CfgStorageType;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static com.aerofs.daemon.core.polaris.db.PolarisSchema.*;
import static com.aerofs.daemon.core.phy.linked.db.LinkedStorageSchema.*;
import static com.aerofs.daemon.lib.db.SyncSchema.*;

public class DPUTAddMigrantColumn implements IDaemonPostUpdateTask {
    @Inject private IDBCW _dbcw;
    @Inject private CfgStorageType storageType;

    @Override
    public void run() throws Exception {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            // we internally started using polaris already...
            if (_dbcw.tableExists(T_META_BUFFER)) {
                s.executeUpdate("alter table " + T_META_BUFFER
                        + " add column " + C_META_BUFFER_MIGRANT + _dbcw.uniqueIdType());
            }

            // update all meta changes tables (one per store...)
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = s.executeQuery("select name from sqlite_master" +
                    " where type='table' and name LIKE '" + T_META_CHANGE + "_%'")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            for (String table : tables) {
                s.executeUpdate("alter table " + table
                        + " add column " + C_META_CHANGE_MIGRANT + _dbcw.uniqueIdType());
            }

            if (_dbcw.tableExists(T_LSA)) {
                s.executeUpdate("alter table " + T_LSA
                        + " add column " + C_LSA_REV + " text");
            }


            if (storageType.get() == StorageType.LINKED) {
                if (_dbcw.tableExists(T_PSA)) {
                    s.executeUpdate("alter table " + T_PSA
                            + " add column " + C_PSA_REV + " text");
                }

                LinkedStorageSchema.createHistoryTables_(s, _dbcw);
            }
        });
    }
}
