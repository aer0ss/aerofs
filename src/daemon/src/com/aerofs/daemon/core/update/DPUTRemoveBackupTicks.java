/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * Storing backup ticks in a separate table is a huge bottleneck when deleting
 * or restoring large stores as ticks need to be copied then deleted. This cost
 * was previously hidden by the cost of object cleanup but with scalable deletion
 * it jumped to the forefront.
 *
 * The version system will eventually be drastically simplified as part of the
 * Polaris project but it will be months before that bears fruit, hence this
 * patch.
 */
public class DPUTRemoveBackupTicks implements IDaemonPostUpdateTask
{
    private final static Logger l = Loggers.getLogger(DPUTRemoveBackupTicks.class);

    @Inject private IDBCW _dbcw;
    @Inject private CfgLocalDID _localDID;

    private final static String
            // Backup Ticks
            T_BKUPT         = "bt",
            C_BKUPT_SIDX    = "bt_s",       // SIndex
            C_BKUPT_OID     = "bt_o",       // OID
            C_BKUPT_CID     = "bt_c",       // CID
            C_BKUPT_TICK    = "bt_t",       // Tick

            // Immigrant Backup Ticks
            T_IBT           = "ibt",
            C_IBT_SIDX      = "ibt_i",      // SIndex
            C_IBT_OID       = "ibt_o",      // OID
            C_IBT_CID       = "ibt_c",      // CID
            C_IBT_IMM_TICK  = "ibt_it",     // immigrant Tick
            C_IBT_DID       = "ibt_d",      // DID
            C_IBT_TICK      = "ibt_t";      // Tick

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            // NB: need this wrapping as the DPUT could be interrupted during vacuum
            if (_dbcw.tableExists(T_BKUPT)) {
                moveBackupTicks_(s);
            }

            // vacuum cannot be called inside a transaction
            s.execute("commit");

            l.info("vacuum");
            s.execute("vacuum");
            l.info("vacuumed");

            // sqlite jdbc needs to always be inside a transaction
            s.execute("begin exclusive transaction");
        });
    }

    private void moveBackupTicks_(Statement s) throws SQLException
    {
        int n;

        n = s.executeUpdate("insert into " + T_MAXTICK + "("
                + C_MAXTICK_SIDX + ","
                + C_MAXTICK_OID + ","
                + C_MAXTICK_CID + ","
                + C_MAXTICK_DID + ","
                + C_MAXTICK_MAX_TICK + ")"
                + " select "
                + C_BKUPT_SIDX + ","
                + C_BKUPT_OID + ","
                + C_BKUPT_CID + ","
                + "x'" + _localDID.get().toStringFormal() + "',"
                + C_BKUPT_TICK
                + " from " + T_BKUPT);

        l.info("moved {} native backup ticks", n);

        n = s.executeUpdate("insert into " + T_IV + "("
                + C_IV_SIDX + ","
                + C_IV_OID + ","
                + C_IV_CID + ","
                + C_IV_DID + ","
                + C_IV_TICK + ","
                + C_IV_IMM_DID + ","
                + C_IV_IMM_TICK + ")"
                + " select "
                + C_IBT_SIDX + ","
                + C_IBT_OID + ","
                + C_IBT_CID + ","
                + C_IBT_DID + ","
                + C_IBT_TICK + ","
                + "x'" + _localDID.get().toStringFormal() + "',"
                + C_IBT_IMM_TICK
                + " from " + T_IBT);

        l.info("moved {} immigrant backup ticks", n);

        s.executeUpdate("drop table " + T_BKUPT);
        s.executeUpdate("drop table " + T_IBT);

        l.info("dropped backup tables");
    }
}
