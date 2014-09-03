/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * A number of old bugs, chief among which a DTLS bug that corrupted SIDs on the sender side,
 * resulted in the accumulation of so-called "ghost" KMLs: ticks propagated to all devices by
 * AntiEntropy but which no device actually "have".
 *
 * These ghost KMLs prevent the Collector queue from being cleared (and sometime contribute to
 * bloom filters getting full) and cause a flood of junk messages on the network, wasting both
 * bandwidth and processing power.
 *
 * However, due to the design of AnitEntropy, in particular the way incremental version vector
 * updates are propagated and efficiently filtered via the use of knowledge vectors, removing
 * those ticks is a complex and somewhat brutal affair:
 *
 * 1. tick must be removed on all devices at once else they will propagate again, hence 2.
 * 2. the core magic must be bumped to create an artificial network partition between "clean"
 * and "dirty" devices (i.e. those that updated and those that did not)
 * 3. any tick removed requires a corresponding rollback of the version vector as there is no
 * sure way of distinguishing a "real" tick from a "ghost" tick
 * 4. after removing ticks the maxticks table needs to be updated
 * 5. immigration adds even more complexity (see below...)
 *
 * Ideally we wouldn't need to worry about bloom filters as they relate to "real" versions (i.e.
 * objects whose with local tick changes). Unfortunately cleaning the collector queue will most
 * likely lead to all bloom filters being discarded before AntiEntropy has a chance torun and
 * restore real KMLs, leading to a nosync.
 *
 * Therefore we need to clean the table of pulled devices to force AntiEntropy to request a full
 * bloom filter (i.e. from BASE) on the next GVC to each (SIndex, remote DID) pair.
 *
 */
public class DPUTCleanupGhostKML implements IDaemonPostUpdateTask
{
    private static final Logger l = Loggers.getLogger(DPUTCleanupGhostKML.class);

    private final IDBCW _dbcw;

    public DPUTCleanupGhostKML(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            clearImmigrantKMLs(s, _dbcw.getConnection());
            clearNativeKMLs(s, _dbcw.getConnection());
            updateMaxTicks(s);
            clearCollectorQueue(s);
            clearPulledDevices(s);
        });
    }

    /**
     * As always, immigration bring its own special crazyness...
     *
     * My understanding (and please, do correct me if I'm wrong) is that each (immDid, immTick)
     * pair maps to a native (did, tick) pair where immDid is the device on which the migration
     * occurs, immTick the immigrant tick on that device for that migration and (did, tick) is
     * one element of the native version vector of the given object at the time of the migration.
     *
     * As a consequence, removing a KML which was obtained through immigration requires slightly
     * more work than removing a KML obtained "natively" as we also need to remove the immigrant
     * tick (and rollback the immigrant vector accordingly) to ensure that the tick will be
     * restored on the next AntiEntropy run (assuming it was not a "ghost")
     */
    private static void clearImmigrantKMLs(Statement s, Connection c) throws SQLException
    {
        // for each (SIndex,DID) find smallest immigrant tick associated to a native KML
        ResultSet rs = s.executeQuery("select "
                + C_IV_SIDX + ","
                + C_IV_IMM_DID + ","
                + "min(" + C_IV_IMM_TICK + ")"
                + " from " + T_IV
                + " inner join " + T_VER
                + " on "
                + C_IV_SIDX + "=" + C_VER_SIDX + " and "
                + C_IV_OID + "=" + C_VER_OID + " and "
                + C_IV_CID + "=" + C_VER_CID + " and "
                + C_IV_DID + "=" + C_VER_DID + " and "
                + C_IV_TICK + "=" + C_VER_TICK
                + " where " + C_VER_KIDX + "=-1"
                + " group by " + C_IV_SIDX + "," + C_IV_IMM_DID);

        List<SDB> rollbackBoundary = Lists.newArrayList();
        try {
            while (rs.next()) {
                rollbackBoundary.add(new SDB(rs.getInt(1), rs.getBytes(2), rs.getLong(3)));
            }
        } finally {
            rs.close();
        }

        KnowledgeRollback kr = new KnowledgeRollback(c, T_IK,
                C_IK_SIDX, C_IK_IMM_DID, C_IK_IMM_TICK);

        PreparedStatement ps = c.prepareStatement(DBUtil.deleteWhere(
                T_IV,
                C_IV_SIDX + "=? and "
                        + C_IV_IMM_DID + "=? and "
                        + C_IV_IMM_TICK + ">=?"));

        // cannot remove ticks while the ResultSet is open, hence the second loop
        for (SDB sdb : rollbackBoundary) {
            if (Cfg.did().equals(new DID(sdb.did))) {
                l.warn("cannot rollback local ticks for {}", sdb.sidx);
                continue;
            }

            kr.rollback(sdb.sidx, sdb.did, sdb.minImmTick);

            // NB: we may remove immigrant ticks associated with locally present ticks
            // but this is fine because:
            //  * we only remove ticks anterior to the new knowledge vector
            //  * we don't remove the associated native ticks and the core handles
            //  gracefully the reception of immigrant ticks whose associated native
            //  tick is already known since that situation can arise naturally
            ps.setInt(1, sdb.sidx);
            ps.setBytes(2, sdb.did);
            ps.setLong(3, sdb.sidx);
            ps.executeUpdate();
        }
    }

    private static class SDB
    {
        final int sidx;
        final byte[] did;
        final long minImmTick;

        SDB(int s, byte[] d, long t) { sidx = s; did = d; minImmTick = t; }
    }

    private static class KnowledgeRollback
    {
        private final PreparedStatement psGet;
        private final PreparedStatement psSet;
        private final PreparedStatement psDel;

        KnowledgeRollback(Connection c, String table, String c_sidx, String c_did, String c_tick)
                throws SQLException
        {
            psGet = c.prepareStatement(DBUtil.selectWhere(table, c_tick,
                    c_sidx + "=? and " + c_did + "=?"));

            psSet = c.prepareStatement("replace into " + table + "("
                    + c_sidx + ","
                    + c_did + ","
                    + c_tick + ") values(?,?,?)");

            psDel = c.prepareStatement("delete from " + table
                    + " where " + c_sidx + "=? and " + c_did + "=?");
        }

        private void rollback(int sidx, byte[] did, long tick) throws SQLException
        {
            long kwlg = getKnowledge(sidx, did);

            // compute new knowledge, which must be:
            //   * below the old knowledge
            //   * below any tick that was removed
            kwlg = Math.min(kwlg, Math.max(tick - 1, 0));
            Preconditions.checkState(kwlg >= 0);

            PreparedStatement ps;
            // NB: inserting 0 ticks would cause AssertionError later on
            if (kwlg > 0) {
                ps = psSet;
                ps.setLong(3, kwlg);
            } else {
                ps = psDel;
            }
            ps.setInt(1, sidx);
            ps.setBytes(2, did);
            ps.executeUpdate();
        }

        private long getKnowledge(int sidx, byte[] did) throws SQLException
        {
            psGet.setInt(1, sidx);
            psGet.setBytes(2, did);
            try (ResultSet rs = psGet.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static void clearNativeKMLs(Statement s, Connection c) throws SQLException
    {
        // for each (SIndex,DID) find the smallest KML
        ResultSet rs = s.executeQuery("select "
                + C_VER_SIDX + ","
                + C_VER_DID + ","
                + "min(" + C_VER_TICK + ")"
                + " from " + T_VER
                + " where " + C_VER_KIDX + "=-1"
                + " group by " + C_VER_SIDX + "," + C_VER_DID);

        KnowledgeRollback kr = new KnowledgeRollback(c, T_KWLG,
                C_KWLG_SIDX, C_KWLG_DID, C_KWLG_TICK);

        // rollback knowledge vector
        try {
            while (rs.next()) {
                int sidx = rs.getInt(1);
                byte[] did = rs.getBytes(2);
                long tick = rs.getLong(3);

                // there is no knowledge to rollback for local ticks
                // NB: KnowledgeRollback would not break but making
                // this distinction explicit improves clarity.
                if (Cfg.did().equals(new DID(did))) continue;

                kr.rollback(sidx, did, tick);
            }
        } finally {
            rs.close();
        }

        // delete all KMLs except those generated on the local device
        // as they cannot be restored through AntiEntropy
        PreparedStatement ps = c.prepareStatement("delete from " + T_VER
                + " where " + C_VER_KIDX + "=-1 and " + C_VER_DID + "!=?");
        ps.setBytes(1, Cfg.did().getBytes());
        ps.executeUpdate();
    }

    private static void updateMaxTicks(Statement s) throws SQLException
    {
        // clear then re-insert
        // using "replace into" would leave maxticks for objects which only had KMLs...
        s.executeUpdate("delete from " + T_MAXTICK);

        s.executeUpdate("insert into " + T_MAXTICK + "("
                + C_MAXTICK_SIDX + ","
                + C_MAXTICK_OID + ","
                + C_MAXTICK_CID + ","
                + C_MAXTICK_DID + ","
                + C_MAXTICK_MAX_TICK + ")"
                + " select "
                + C_VER_SIDX + ","
                + C_VER_OID + ","
                + C_VER_CID + ","
                + C_VER_DID + ","
                + "max(" + C_VER_TICK + ")"
                + " from " + T_VER
                + " group by " + C_VER_SIDX + "," + C_VER_OID + "," + C_VER_CID + "," + C_VER_DID);
    }

    private static void clearCollectorQueue(Statement s) throws SQLException
    {
        s.executeUpdate("delete from " + T_CS);

        // KMLs generated by this device (local mod in a store, expel store, readmit)
        // cannot be cleaned as they could not be restored through AntiEntropy
        // so we have to keep them in the collector queue
        s.executeUpdate("insert into " + T_CS + "("
                + C_CS_SIDX + ","
                + C_CS_OID + ","
                + C_CS_CID + ")"
                + " select " + C_VER_SIDX + "," + C_VER_OID + "," + C_VER_CID
                + " from " + T_VER
                + " where " + C_VER_KIDX + "=-1");
    }

    private static void clearPulledDevices(Statement s) throws SQLException
    {
        s.executeUpdate("delete from " + T_PD);
    }
}
