/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.google.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.CoreSchema.C_EPOCH_AUDIT_PUSH;
import static com.aerofs.daemon.lib.db.CoreSchema.T_EPOCH;
import static com.google.common.base.Preconditions.checkState;

/**
 * Default implementation of {@link com.aerofs.daemon.lib.db.IAuditDatabase}.
 */
public class AuditDatabase extends AbstractDatabase implements IAuditDatabase
{
    // FIXME (AG): merge epoch-{get,set} code in ACL, SyncStatus and Audit

    @Inject
    public AuditDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    private PreparedStatement _psGetEpoch;
    @Override
    public long getLastReportedActivityRow_()
            throws SQLException
    {
        try {
            if (_psGetEpoch == null) {
                // always select the first and only row in the epoch table
                _psGetEpoch = c().prepareStatement(DBUtil.select(T_EPOCH, C_EPOCH_AUDIT_PUSH));
            }

            long localEpoch = 0;
            ResultSet rs = _psGetEpoch.executeQuery();
            try {
                Util.verify(rs.next()); // there should be one entry
                localEpoch = rs.getLong(1);
                Util.verify(!rs.next()); // ... and only one entry
            } finally {
                rs.close();
            }
            return localEpoch;

        } catch (SQLException e) {
            DBUtil.close(_psGetEpoch);
            _psGetEpoch = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psUpdateEpoch;
    @Override
    public void setLastReportedActivityRow_(long activityRowIndex, Trans t)
            throws SQLException
    {
        try {
            if (_psUpdateEpoch == null) {
                _psUpdateEpoch = c().prepareStatement(DBUtil.update(T_EPOCH, C_EPOCH_AUDIT_PUSH));
            }
            _psUpdateEpoch.setLong(1, activityRowIndex);

            int affectedRows = _psUpdateEpoch.executeUpdate();
            checkState(affectedRows == 1, "audit push epoch not updated");
        } catch (SQLException e) {
            DBUtil.close(_psUpdateEpoch);
            _psUpdateEpoch = null;
            throw detectCorruption(e);
        }
    }
}
