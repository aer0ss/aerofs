/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Migrate DB from old OID->SID generation to new scheme that allows bidirectional mapping.
 *
 * See {@link com.aerofs.base.id.SID} for details of the new scheme
 *
 * We must not under any circumstance break existing shared folders in an irrecoverable way and this
 * transition is potentially risky in that regard. Therefore, we back up the old SID table to be
 * able to recover in case something goes wrong.
 */
public class DPUTUpdateSIDGeneration implements IDaemonPostUpdateTask
{
    private final static Logger l = Loggers.getLogger(DPUTUpdateSIDGeneration.class);

    private final IDBCW _dbcw;
    private final CfgLocalUser _cfgLocalUser;

    // copied from CoreSchema to prevent future schema changes from breaking the migration
    private final static String
    // SIndex-SID map. It maintains bidirectional map between SIndexes and SIDs
            T_SID           = "i",
            C_SID_SIDX      = "i_i",        // SIndex
            C_SID_SID       = "i_d",        // SID
    // Versions
            T_VER           = "v",
            C_VER_OID       = "v_o",        // OID
    // Backup Ticks
            T_BKUPT         = "bt",
            C_BKUPT_OID     = "bt_o",       // OID
    // Immigrant Versions
            T_IV            = "iv",
            C_IV_OID        = "iv_o",       // OID
    // Immigrant Backup Ticks
            T_IBT           = "ibt",
            C_IBT_OID       = "ibt_o",      // OID
    // Prefixes
            T_PRE           = "r",          // prefix table
            C_PRE_OID       = "r_o",        // OID
    // Object Attributes
            T_OA          = "o",
            C_OA_SIDX     = "o_s",          // SIndex
            C_OA_OID      = "o_o",          // OID
            C_OA_PARENT   = "o_p",          // OID
            C_OA_TYPE     = "o_d",          // Type
    // Content Attributes
            T_CA            = "c",
            C_CA_OID        = "c_o",        // OID
    // Max ticks
            T_MAXTICK          = "t",
            C_MAXTICK_OID      = "t_o",
    // Collector Filter
            T_CF             = "cf",
            C_CF_SIDX        = "cf_s",      // SIndex
            C_CF_DID         = "cf_d",      // DID
            C_CF_FILTER      = "cf_f",      // BFOID
    // Collector Sequence
            T_CS             = "cs",
            C_CS_OID         = "cs_o",      // OID
    // Sender Filter
            T_SF             = "sf",
            C_SF_SIDX        = "sf_s",      // SIndex
            C_SF_SFIDX       = "sf_i",      // SenderFilterIndex
            C_SF_FILTER      = "sf_f",      // BFOID
    // Alias Table
            T_ALIAS              = "al",
            C_ALIAS_SOURCE_OID   = "al_s",  // Aliased oid
            C_ALIAS_TARGET_OID   = "al_t",  // Target
    // Expulsion Table
            T_EX             = "e",
            C_EX_OID         = "e_o",       // OID
    // Activity Log Table. See IActivityLogDatabase.ActivityRow for field details.
            T_AL             = "ao",
            C_AL_OID         = "ao_o",      // OID
    // Sync status push queue
            T_SSPQ          = "sspq",
            C_SSPQ_OID      = "sspq_o";

    private final String
            T_SID_BACKUP = "bak_" + T_SID,
            C_SID_BACKUP_SIDX = "bak_" + C_SID_SIDX,
            C_SID_BACKUP_SID = "bak_" + C_SID_SID;

    public DPUTUpdateSIDGeneration(CfgLocalUser cfgLocalUser, CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
        _cfgLocalUser = cfgLocalUser;
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            Connection c = _dbcw.getConnection();

            // check that anchors are not aliased
            // NB: this is not strictly required by this task but after talking with Mark about
            // the likelihood of this happening we decided to do it just in case...
            Util.verify(countAnchors(T_ALIAS, C_ALIAS_SOURCE_OID) == 0);
            Util.verify(countAnchors(T_ALIAS, C_ALIAS_TARGET_OID) == 0);

            // Create backup SID table
            s.executeUpdate("create table " + T_SID_BACKUP + "(" +
                    C_SID_BACKUP_SIDX + " integer primary key, " +
                    C_SID_BACKUP_SID + _dbcw.uniqueIdType() + " unique not null" +
                    ")" + _dbcw.charSet());

            // fill backup SID table with content from SID table
            s.executeUpdate("insert into " + T_SID_BACKUP + "(" +
                    C_SID_BACKUP_SIDX + "," +
                    C_SID_BACKUP_SID + ")" +
                    " select " +
                    C_SID_SIDX + "," +
                    C_SID_SID +
                    " from " +
                    T_SID);

            // Fix SIDs
            // NB: the root SID obtained this way will already have the version nibble set to 3
            byte[] rootSID = SID.rootSID(_cfgLocalUser.get()).getBytes();

            int rootCount = 0;
            Map<SIndex, Map<OID, OID>> anchorFixes = Maps.newHashMap();
            try (ResultSet rs = s.executeQuery("select " +
                    C_SID_BACKUP_SIDX + "," + C_SID_BACKUP_SID + " from " + T_SID_BACKUP)) {
                while (rs.next()) {
                    int sidx = rs.getInt(1);
                    byte[] sid = rs.getBytes(2);

                    // make a copy of sid and force version nibble to 3 to detect root sid
                    byte[] fixedSID = Arrays.copyOf(sid, SID.LENGTH);
                    SID.setVersionNibble(fixedSID, 3);

                    if (Arrays.equals(fixedSID, rootSID)) {
                        // fix root SID: set version nibble to 3
                        l.info("store " + sidx + ":" + BaseUtil.hexEncode(sid) + " [root]");
                        updateSID(sidx, fixedSID);
                        ++rootCount;
                    } else {
                        l.info("store " + sidx + ":" + BaseUtil.hexEncode(sid));

                        // fix regular SID: set version nibble to 0
                        SID.setVersionNibble(fixedSID, 0);
                        updateSID(sidx, fixedSID);

                        // keep track of fixed anchors to update bloom filters
                        for (SIndex parent : getStoresWithAnchor(sid)) {
                            l.info("  anchor in " + parent);
                            Map<OID, OID> m = anchorFixes.get(parent);
                            if (m == null) {
                                m = Maps.newHashMap();
                                anchorFixes.put(parent, m);
                            }
                            m.put(OID.legacyValue(sid), new OID(fixedSID));
                        }

                        // fix anchor OID in all table that directly reference it
                        updateOID(c, sid, fixedSID);
                    }
                }
            }

            // Make sure there was exactly one root store candidate. If the user updates AeroFS
            // from a very old version, the core may not have created the root store yet.
            // Therefore, we also allow zero root stores.
            //
            assert rootCount == 0 || rootCount == 1 : rootCount;

            // update bloom filters (sender/collector)
            for (Entry<SIndex, Map<OID, OID>> store : anchorFixes.entrySet()) {
                l.info("update filters for " + store.getValue().size() + " anchors in store "
                        + store.getKey());
                updateSenderFilters(s, c, store.getKey(), store.getValue());
                updateCollectorFilters(s, c, store.getKey(), store.getValue());
            }
        });
    }

    private void updateSID(int sidx, byte[] sid) throws SQLException
    {
        PreparedStatement ps = _dbcw.getConnection().prepareStatement(
                "update " + T_SID +
                " set " + C_SID_SID + "=?" +
                " where " + C_SID_SIDX + "=?");
        ps.setBytes(1, sid);
        ps.setInt(2, sidx);

        int rows = ps.executeUpdate();
        Util.verify(rows == 1);
    }

    /**
     * Update anchor OIDs in place for all table that have an OID column
     */
    static void updateOID(Connection c, byte[] oldOID, byte[] newOID) throws SQLException
    {
        updateOID(c, T_OA, C_OA_OID, oldOID, newOID);
        updateOID(c, T_OA, C_OA_PARENT, oldOID, newOID);
        updateOID(c, T_CA, C_CA_OID, oldOID, newOID);
        updateOID(c, T_PRE, C_PRE_OID, oldOID, newOID);
        updateOID(c, T_VER, C_VER_OID, oldOID, newOID);
        updateOID(c, T_BKUPT, C_BKUPT_OID, oldOID, newOID);
        updateOID(c, T_IV, C_IV_OID, oldOID, newOID);
        updateOID(c, T_IBT, C_IBT_OID, oldOID, newOID);
        updateOID(c, T_MAXTICK, C_MAXTICK_OID, oldOID, newOID);
        updateOID(c, T_CS, C_CS_OID, oldOID, newOID);
        updateOID(c, T_EX, C_EX_OID, oldOID, newOID);
        updateOID(c, T_AL, C_AL_OID, oldOID, newOID);
        updateOID(c, T_SSPQ, C_SSPQ_OID, oldOID, newOID);
    }

    private static void updateOID(Connection c, String table, String column, byte[] oldOID, byte[] newOID)
            throws SQLException
    {
        PreparedStatement ps = c.prepareStatement(
                "update " + table +
                " set " + column + "=?" +
                " where " + column + "=?");
        ps.setBytes(1, newOID);
        ps.setBytes(2, oldOID);
        ps.executeUpdate();
    }

    /**
     * Count occurence of anchor OIDs in a given table
     */
    private int countAnchors(String table, String column) throws SQLException
    {
        PreparedStatement ps = _dbcw.getConnection().prepareStatement("select count(1)" +
                " from " + table +
                " join " + T_OA + " on " + column + "=" + C_OA_OID +
                " where " + C_OA_TYPE + "=?");
        ps.setInt(1, Type.ANCHOR.ordinal());

        try (ResultSet rs = ps.executeQuery()) {
            Util.verify(rs.next());
            int count = rs.getInt(1);
            Util.verify(!rs.next());
            return count;
        }
    }

    /**
     * NB: only one store should have the anchor admitted but multiple ones may have it in their
     * trash
     */
    private Set<SIndex> getStoresWithAnchor(byte[] oldOID) throws SQLException
    {
        PreparedStatement ps = _dbcw.getConnection().prepareStatement("select " + C_OA_SIDX +
                " from " + T_OA + " where " + C_OA_OID + "=? and " + C_OA_TYPE + "=?");
        ps.setBytes(1, oldOID);
        ps.setInt(2, Type.ANCHOR.ordinal());

        Set<SIndex> r = Sets.newHashSet();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                r.add(new SIndex(rs.getInt(1)));
            }
            return r;
        }
    }

    static void updateSenderFilters(Statement s, Connection c, SIndex sidx, Map<OID, OID> anchors)
            throws SQLException
    {
        updateFilters(s, c, T_SF, C_SF_SIDX, C_SF_SFIDX, C_SF_FILTER, sidx, anchors,
                (rs, ps, idx) -> ps.setLong(idx, rs.getLong(idx)));
    }

    static void updateCollectorFilters(Statement s, Connection c, SIndex sidx, Map<OID, OID> anchors)
            throws SQLException
    {
        updateFilters(s, c, T_CF, C_CF_SIDX, C_CF_DID, C_CF_FILTER, sidx, anchors,
                (rs, ps, idx) -> ps.setBytes(idx, rs.getBytes(idx)));
    }

    private static interface IFieldTransfer
    {
        void transfer(ResultSet rs, PreparedStatement ps, int idx) throws SQLException;
    }

    /**
     * Update all bloom filters for a given store in a given table
     */
    private static void updateFilters(Statement s, Connection c, String table, String cSidx,
            String cSecondaryKey, String cFilter, SIndex sidx, Map<OID, OID> anchors,
            IFieldTransfer f) throws SQLException
    {
        int n = 0;
        PreparedStatement ps = c.prepareStatement(
                "replace into " + table + "(" +
                        cSidx + "," + cSecondaryKey + "," + cFilter + ") values (?,?,?)");

        try (ResultSet rs = s.executeQuery(
                "select " + cFilter + "," + cSecondaryKey + " from " + table
                        + " where " + cSidx + "=" + sidx.getInt())) {
            while (rs.next()) {
                BFOID bf = new BFOID(rs.getBytes(1));

                updateBloomFilter(bf, anchors);

                ps.setInt(1, sidx.getInt());
                f.transfer(rs, ps, 2);
                ps.setBytes(3, bf.getBytes());
                ps.addBatch();
                ++n;
            }
        }
        int[] rowCounts = ps.executeBatch();
        assert rowCounts.length == n;
        for (int rowCount : rowCounts) assert rowCount == 1;
    }

    /**
     * For all old anchors present in the bloom filter, add the new "fixed" anchor
     *
     * False positives likely to ensue but the code should be able to deal with it
     */
    private static void updateBloomFilter(BFOID bf, Map<OID, OID> anchors)
    {
        Set<OID> present = Sets.newHashSet();
        for (Entry<OID, OID> anchor : anchors.entrySet()) {
            if (bf.contains_(anchor.getKey())) present.add(anchor.getValue());
        }
        for (OID oid : present) bf.add_(oid);
    }
}
