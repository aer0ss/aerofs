/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
 */
public class DPUTCleanupGhostKML implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTCleanupGhostKML(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, new IDatabaseOperation()
        {
            @Override
            public void run_(Statement s) throws SQLException
            {
                clearImmigrantKMLs(s, _dbcw.getConnection());
                clearNativeKMLs(s, _dbcw.getConnection());
                updateMaxTicks(s);
                clearCollectorQueue(s);
            }
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
        // find immigrant ticks associated to native KMLs
        ResultSet rs = s.executeQuery("select "
                + C_IV_SIDX + ","
                + C_IV_OID + ","
                + C_IV_CID + ","
                + C_IV_DID + ","
                + C_IV_IMM_DID + ","
                + C_IV_IMM_TICK
                + " from " + T_IV
                + " inner join " + T_VER
                + " on "
                + C_IV_SIDX + "=" + C_VER_SIDX + " and "
                + C_IV_OID + "=" + C_VER_OID + " and "
                + C_IV_CID + "=" + C_VER_CID + " and "
                + C_IV_DID + "=" + C_VER_DID + " and "
                + C_IV_TICK + "=" + C_VER_TICK
                + " where "
                + C_VER_KIDX + "=-1");

        Set<TTD> immTicksToDelete = Sets.newHashSet();
        Map<SD, Long> rollbackBoundary = Maps.newHashMap();
        try {
            while (rs.next()) {
                SIndex sidx = new SIndex(rs.getInt(1));
                OID oid = new OID(rs.getBytes(2));
                CID cid = new CID(rs.getInt(3));
                DID did = new DID(rs.getBytes(4));
                DID immDid = new DID(rs.getBytes(5));
                long immTick = rs.getLong(6);

                immTicksToDelete.add(new TTD(new SOCID(sidx, oid, cid), did));

                SD sd = new SD(sidx, immDid);
                Long minImmTick = rollbackBoundary.get(sd);
                rollbackBoundary.put(sd,
                        minImmTick == null ? immTick : Math.min(minImmTick, immTick));
            }
        } finally {
            rs.close();
        }

        rollbackImmigrantKnowledge(c, rollbackBoundary);
        removeImmigrantTicks(c, immTicksToDelete);
    }

    private static void rollbackImmigrantKnowledge(Connection c, Map<SD, Long> rollbackBoundary)
            throws SQLException
    {
        KnowledgeRollback kr = new KnowledgeRollback(c, T_IK,
                C_IK_SIDX, C_IK_IMM_DID, C_IK_IMM_TICK);

        // rollback immigrant knowledge vector
        for (Entry<SD, Long> e : rollbackBoundary.entrySet()) {
            SD sd = e.getKey();
            kr.rollback(sd._sidx.getInt(), sd._did.getBytes(), e.getValue());
        }
    }

    private static void removeImmigrantTicks(Connection c, Set<TTD> immTicksToDelete)
            throws SQLException
    {
        PreparedStatement ps = c.prepareStatement(DBUtil.deleteWhere(
                T_IV,
                C_IV_SIDX + "=? and "
                        + C_IV_OID + "=? and "
                        + C_IV_CID + "=? and "
                        + C_IV_DID + "=?"));

        for (TTD ttd : immTicksToDelete) {
            ps.setInt(1, ttd._socid.sidx().getInt());
            ps.setBytes(2, ttd._socid.oid().getBytes());
            ps.setInt(3, ttd._socid.cid().getInt());
            ps.setBytes(4, ttd._did.getBytes());
            ps.executeUpdate();
        }
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
            // we MUST NOT insert 0 ticks in the db! (or AessrtionError will ensue...
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
            ResultSet rs = psGet.executeQuery();
            try {
                return rs.next() ? rs.getLong(1) : 0;
            } finally {
                rs.close();
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
                kr.rollback(sidx, did, tick);
            }
        } finally {
            rs.close();
        }

        // delete all KMLs
        s.executeUpdate("delete from " + T_VER + " where " + C_VER_KIDX + "=-1");
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
    }



    /**
     * Helper class for clearing immigrant ticks
     */
    static class TTD
    {
        public final SOCID _socid;
        public final DID _did;

        public TTD(SOCID socid, DID did) { _socid = socid; _did = did; }

        @Override
        public boolean equals(Object o)
        {
            return o != null && o instanceof TTD
                    && ((TTD)o)._socid.equals(_socid)
                    && ((TTD)o)._did.equals(o);
        }

        @Override
        public int hashCode()
        {
            return _socid.hashCode() ^ _did.hashCode();
        }
    }

    /**
     * Helper class for immigrant vector rollback
     */
    static class SD
    {
        public final SIndex _sidx;
        public final DID _did;

        public SD(SIndex sidx, DID did) { _sidx = sidx; _did = did; }

        @Override
        public boolean equals(Object o)
        {
            return o != null && o instanceof SD
                    && ((SD)o)._sidx.equals(_sidx)
                    && ((SD)o)._did.equals(o);
        }

        @Override
        public int hashCode()
        {
            return _sidx.hashCode() ^ _did.hashCode();
        }
    }
}
