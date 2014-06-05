/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * This database is used to keep track of shared folders of which the user is a member but which
 * he has opted to join as "external roots". Such folders cannot be auto-joined as they require
 * the user to provide a per-device path at which the shared folder will be "linked".
 *
 * When the ACL subsystem learns about a new shared folder, it will either be auto-joined or placed
 * in this database if the "external" flag is set. The UI can then query the list of pending roots
 * and "link" each of them to a physical location at the user's request.
 *
 * As the name indicates, this database only hold pending roots. External shared folders should be
 * removed from it when linked.
 */
public class PendingRootDatabase extends AbstractDatabase
{
    @Inject
    public PendingRootDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    private final PreparedStatementWrapper _pswGetPendingRoot = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_PENDING_ROOT, C_PENDING_ROOT_SID + "=?", C_PENDING_ROOT_NAME));
    public @Nullable String getPendingRoot(SID sid) throws SQLException
    {
        try {
            PreparedStatement ps = _pswGetPendingRoot.get(c());
            ps.setBytes(1, sid.getBytes());
            ResultSet rs = ps.executeQuery();
            try {
                return rs.next() ? rs.getString(1) : null;
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _pswGetPendingRoot.close();
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswGetPendingRoots = new PreparedStatementWrapper(
            DBUtil.select(T_PENDING_ROOT, C_PENDING_ROOT_SID, C_PENDING_ROOT_NAME));
    public Map<SID, String> getPendingRoots() throws SQLException
    {
        try {
            ResultSet rs = _pswGetPendingRoots.get(c()).executeQuery();
            try {
                Map<SID, String> m = Maps.newHashMap();
                while (rs.next()) {
                    m.put(new SID(rs.getBytes(1)), rs.getString(2));
                }
                return m;
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _pswGetPendingRoots.close();
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswAddPendingRoot = new PreparedStatementWrapper(
            DBUtil.insert(T_PENDING_ROOT, C_PENDING_ROOT_SID, C_PENDING_ROOT_NAME));
    public void addPendingRoot(SID sid, String name, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswAddPendingRoot.get(c());
            ps.setBytes(1, sid.getBytes());
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            _pswAddPendingRoot.close();
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswRemovePendingRoot = new PreparedStatementWrapper(
            DBUtil.deleteWhere(T_PENDING_ROOT, C_PENDING_ROOT_SID + "=?"));
    public void removePendingRoot(SID sid, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswRemovePendingRoot.get(c());
            ps.setBytes(1, sid.getBytes());
            ps.executeUpdate();
        } catch (SQLException e) {
            _pswRemovePendingRoot.close();
            throw detectCorruption(e);
        }
    }
}
