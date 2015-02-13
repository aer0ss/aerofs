/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

public class DPUTCreateLeaveQueueTable implements IDaemonPostUpdateTask
{
    private static final Logger l = Loggers.getLogger(DPUTCreateLeaveQueueTable.class);
    private final IDBCW _dbcw;

    public DPUTCreateLeaveQueueTable(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            CoreSchema.createUpdateQueueTable(s, _dbcw);

            ResultSet rs = s.executeQuery(DBUtil.selectWhere(T_OA,
                    C_OA_TYPE + "=" + Type.ANCHOR.ordinal(),
                    C_OA_SIDX, C_OA_OID));

            /**
             * Theoretically we do not allow nested stores however the user may move anchor
             * around, effectively achieving some nested stores and leaving dead anchors in the
             * db.
             *
             * It would be very bad if we ended up leaving a store just because its anchor was
             * migrated so we keep track of all anchors, live and dead, and only leave stores
             * for which we have found a dead anchor but no live one.
             */
            Set<OID> deletedAnchors = Sets.newHashSet();
            Set<OID> liveAnchors = Sets.newHashSet();
            try {
                while (rs.next()) {
                    SOID soid = new SOID(new SIndex(rs.getInt(1)), new OID(rs.getBytes(2)));
                    if (isDeleted_(soid)) {
                        l.info("dead anchor " + soid);
                        deletedAnchors.add(soid.oid());
                    } else {
                        l.info("live anchor " + soid);
                        liveAnchors.add(soid.oid());
                    }
                }
            } finally {
                rs.close();
            }

            for (OID oid : deletedAnchors) {
                if (!liveAnchors.contains(oid)) addLeaveCommand_(SID.anchorOID2storeSID(oid));
            }
        });
    }

    void addLeaveCommand_(SID sid) throws SQLException
    {
        l.info("schedule leave store " + sid);
        PreparedStatement ps = _dbcw.getConnection().prepareStatement(
                DBUtil.insert(T_SPQ, C_SPQ_SID));
        ps.setBytes(1, sid.getBytes());
        int rows = ps.executeUpdate();
        assert rows == 1;
    }

    boolean isDeleted_(SOID soid) throws SQLException
    {
        SIndex sidx = soid.sidx();
        OID parent = getParent_(sidx, soid.oid());
        while (!parent.isRoot() && !parent.isTrash()) parent = getParent_(sidx, parent);
        return parent.isTrash();
    }

    OID getParent_(SIndex sidx, OID oid) throws SQLException
    {
        PreparedStatement ps = _dbcw.getConnection().prepareStatement(
                DBUtil.selectWhere(T_OA, C_OA_SIDX + "=? and " + C_OA_OID + "=?", C_OA_PARENT));
        ps.setInt(1, sidx.getInt());
        ps.setBytes(2, oid.getBytes());
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? new OID(rs.getBytes(1)) : null;
        }
    }
}
