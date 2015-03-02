/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Fixes corner cases left unaddressed by DPUTUpdateSIDGeneration
 */
public class DPUTMigrateDeadAnchorsAndEmigratedNames implements IDaemonPostUpdateTask
{
    private static final Logger l = Loggers.getLogger(
            DPUTMigrateDeadAnchorsAndEmigratedNames.class);

    private final IDBCW _dbcw;

    // copied from CoreSchema to prevent future schema changes from breaking the migration
    private final static String
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
            C_OA_NAME     = "o_n",          // String
            C_OA_FLAGS    = "o_l",          // int
    // Content Attributes
            T_CA            = "c",
            C_CA_OID        = "c_o",        // OID
    // Max ticks
            T_MAXTICK          = "t",
            C_MAXTICK_OID      = "t_o",
    // Collector Sequence
            T_CS             = "cs",
            C_CS_OID         = "cs_o",      // OID
    // Expulsion Table
            T_EX             = "e",
            C_EX_OID         = "e_o",       // OID
    // Activity Log Table. See IActivityLogDatabase.ActivityRow for field details.
            T_AL             = "ao",
            C_AL_OID         = "ao_o",      // OID
    // Sync status push queue
            T_SSPQ          = "sspq",
            C_SSPQ_OID      = "sspq_o";

    public DPUTMigrateDeadAnchorsAndEmigratedNames(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            updateAnchorOIDs(s);
            updateUnknownAnchorOIDs(s);
            updateDeletedObjectNames(s);
        });
    }

    /**
     * update SID occurences in the name of deleted objects (see EmigrantUtil)
     */
    private void updateDeletedObjectNames(Statement s) throws SQLException
    {
        PreparedStatement ps = _dbcw.getConnection().prepareStatement(
                "update " + T_OA + " set " + C_OA_NAME + "=?" +
                " where " + C_OA_SIDX + "=? and " + C_OA_OID + "=?");

        /**
         * flags!=0 : only consider expelled objects
         */
        ResultSet rs = s.executeQuery("select " +
                C_OA_SIDX + "," + C_OA_OID + "," + C_OA_PARENT + "," + C_OA_NAME +
                " from " + T_OA + " where " + C_OA_FLAGS + "!=0");
        try {
            while (rs.next()) {
                int sidx = rs.getInt(1);
                OID oid = new OID(rs.getBytes(2));
                OID parent = new OID(rs.getBytes(3));
                String name = rs.getString(4);

                /**
                 * emigrated objects are a subset of expelled objects
                 */
                if (!isEmigrantName(name) || !parent.isTrash()) continue;

                byte[] sid = extractSID(name);
                if (sid == null) {
                    l.info("skip " + name);
                    continue;
                }

                l.info("fix emigrant target: " + BaseUtil.hexEncode(sid));

                /**
                 * There is no guarantee that the emigrantTargetSID is a non-root store but this
                 * information is only used to avoid redownloading content so assuming that the
                 * target SID is a non-root store will, at worst, cause a few false negatives and
                 * subsequent redownloads...
                 */
                UniqueID.setVersionNibble(sid, 0);

                ps.setString(1, oid.toStringFormal() + "." + new SID(sid).toStringFormal());
                ps.setInt(2, sidx);
                ps.setBytes(3, oid.getBytes());
                ps.addBatch();
            }
        } finally {
            rs.close();
        }

        ps.executeBatch();
    }

    private static final int ID_STRING_LEN = SID.ZERO.toStringFormal().length();

    private static boolean isEmigrantName(String name)
    {
        return name.length() == (2 * ID_STRING_LEN + 1) && name.charAt(ID_STRING_LEN) == '.';
    }

    private static @Nullable byte[] extractSID(String name)
    {
        try {
            return BaseUtil.hexDecode(name, ID_STRING_LEN + 1, 2 * ID_STRING_LEN + 1);
        } catch (ExFormatError e) {
            return null;
        }
    }

    /**
     * Update all locally known anchors, even if the store they refer to is not locally known
     */
    private void updateAnchorOIDs(Statement s) throws SQLException
    {
        ResultSet rs = s.executeQuery("select " + C_OA_SIDX + "," + C_OA_OID + " from " + T_OA +
                " where " + C_OA_TYPE + "=" + Type.ANCHOR.ordinal());

        Map<SIndex, Map<OID, OID>> oldAnchors = Maps.newHashMap();
        try {
            while (rs.next()) {
                SIndex sidx = new SIndex(rs.getInt(1));
                byte[] oid = rs.getBytes(2);
                assert oid.length == UniqueID.LENGTH;

                int v = UniqueID.getVersionNibble(oid);
                if (v == 0) continue;

                l.info("migrate anchor: " + BaseUtil.hexEncode(oid));

                byte[] fixedOID = Arrays.copyOf(oid, oid.length);
                UniqueID.setVersionNibble(fixedOID, 0);

                Map<OID, OID> m = oldAnchors.get(sidx);
                if (m == null) {
                    m = Maps.newHashMap();
                    oldAnchors.put(sidx, m);
                }
                m.put(OID.legacyValue(oid), new OID(fixedOID));
            }
        } finally {
            rs.close();
        }

        Connection c = _dbcw.getConnection();

        /**
         * Update all tables referring to OIDs for all valid anchors
         */
        for (Entry<SIndex, Map<OID, OID>> e : oldAnchors.entrySet()) {
            SIndex sidx = e.getKey();
            Map<OID, OID> anchors = e.getValue();

            for (Entry<OID, OID> anchor : anchors.entrySet()) {
                DPUTUpdateSIDGeneration.updateOID(c,  anchor.getKey().getBytes(),
                        anchor.getValue().getBytes());
            }

            DPUTUpdateSIDGeneration.updateSenderFilters(s, c, sidx, anchors);
            DPUTUpdateSIDGeneration.updateCollectorFilters(s, c, sidx, anchors);
        }
    }


    /**
     * There may be some cases where we have information (e.g. version vectors) about an object that
     * is not present in the OA table. In this case we still need to fix invalid OIDs to avoid
     * assert failures but because we don't have the object type we may miss some anchor OIDs that
     * happen to be structurally valid non-anchor OIDs...
     *
     * Let's hope this corner case does not pop up and we'll worry about it if it does...
     */
    private void updateUnknownAnchorOIDs(Statement s) throws SQLException
    {
        updateUnknownAnchorOIDs(s, T_OA, C_OA_OID);
        updateUnknownAnchorOIDs(s, T_OA, C_OA_PARENT);
        updateUnknownAnchorOIDs(s, T_CA, C_CA_OID);
        updateUnknownAnchorOIDs(s, T_PRE, C_PRE_OID);
        updateUnknownAnchorOIDs(s, T_VER, C_VER_OID);
        updateUnknownAnchorOIDs(s, T_BKUPT, C_BKUPT_OID);
        updateUnknownAnchorOIDs(s, T_IV, C_IV_OID);
        updateUnknownAnchorOIDs(s, T_IBT, C_IBT_OID);
        updateUnknownAnchorOIDs(s, T_MAXTICK, C_MAXTICK_OID);
        updateUnknownAnchorOIDs(s, T_CS, C_CS_OID);
        updateUnknownAnchorOIDs(s, T_EX, C_EX_OID);
        updateUnknownAnchorOIDs(s, T_AL, C_AL_OID);
        updateUnknownAnchorOIDs(s, T_SSPQ, C_SSPQ_OID);
    }

    private void updateUnknownAnchorOIDs(Statement s, String table, String column)
            throws SQLException
    {
        PreparedStatement ps = _dbcw.getConnection().prepareStatement(
                "update " + table + " set " + column + "=? where " + column + "=?");

        ResultSet rs = s.executeQuery("select " + column + " from " + table);
        try {
            while (rs.next()) {
                byte[] oid = rs.getBytes(1);
                assert oid.length == UniqueID.LENGTH;
                int v = UniqueID.getVersionNibble(oid);
                // 0: valid anchor, 4: valid non-anchor, anything else: invalid anchor, fix...
                if (v != 0 && v != 4) {
                    l.info(table + ": migrate dead anchor: " + BaseUtil.hexEncode(oid));

                    byte[] fixedAnchor = Arrays.copyOf(oid, oid.length);
                    UniqueID.setVersionNibble(fixedAnchor, 0);

                    ps.setBytes(1, fixedAnchor);
                    ps.setBytes(2, oid);
                    ps.addBatch();
                }
            }
        } finally {
            rs.close();
        }

        ps.executeBatch();
    }
}
