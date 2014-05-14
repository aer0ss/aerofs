/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.DBUtil;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

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

    private PreparedStatement _psGetPendingRoots;

    public Map<SID, String> getPendingRoots() throws SQLException
    {
        try {
            if (_psGetPendingRoots == null) {
                _psGetPendingRoots = c().prepareStatement(DBUtil.select(T_PENDING_ROOT,
                        C_PENDING_ROOT_SID, C_PENDING_ROOT_NAME));
            }

            ResultSet rs = _psGetPendingRoots.executeQuery();

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
            DBUtil.close(_psGetPendingRoots);
            _psGetPendingRoots = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psAddPendingRoot;
    public void addPendingRoot(SID sid, String name, Trans t) throws SQLException
    {
        try {
            if (_psAddPendingRoot == null) {
                _psAddPendingRoot = c().prepareStatement(DBUtil.insert(T_PENDING_ROOT,
                        C_PENDING_ROOT_SID, C_PENDING_ROOT_NAME));
            }

            _psAddPendingRoot.setBytes(1, sid.getBytes());
            _psAddPendingRoot.setString(2, name);
            _psAddPendingRoot.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psAddPendingRoot);
            _psAddPendingRoot = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psRemovePendingRoot;
    public void removePendingRoot(SID sid, Trans t) throws SQLException
    {
        try {
            if (_psRemovePendingRoot == null) {
                _psRemovePendingRoot = c().prepareStatement(DBUtil.deleteWhere(T_PENDING_ROOT,
                        C_PENDING_ROOT_SID + "=?"));
            }

            _psRemovePendingRoot.setBytes(1, sid.getBytes());
            _psRemovePendingRoot.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psRemovePendingRoot);
            _psRemovePendingRoot = null;
            throw detectCorruption(e);
        }
    }
}
