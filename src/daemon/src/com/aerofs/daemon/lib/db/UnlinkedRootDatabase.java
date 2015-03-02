/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.ids.SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
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
 * in this database if the "external" flag is set. The UI can then query the list of unlinked roots
 * and "link" each of them to a physical location at the user's request.
 *
 * As the name indicates, this database only hold unlinked roots. External shared folders should be
 * removed from it when linked.
 */
public class UnlinkedRootDatabase extends AbstractDatabase
{
    @Inject
    public UnlinkedRootDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    private final PreparedStatementWrapper _pswGetUnlinkedRoot = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_UNLINKED_ROOT, C_UNLINKED_ROOT_SID + "=?", C_UNLINKED_ROOT_NAME));
    public @Nullable String getUnlinkedRoot(SID sid) throws SQLException
    {
        try {
            PreparedStatement ps = _pswGetUnlinkedRoot.get(c());
            ps.setBytes(1, sid.getBytes());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            _pswGetUnlinkedRoot.close();
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswGetUnlinkedRoots = new PreparedStatementWrapper(
            DBUtil.select(T_UNLINKED_ROOT, C_UNLINKED_ROOT_SID, C_UNLINKED_ROOT_NAME));
    public Map<SID, String> getUnlinkedRoots() throws SQLException
    {
        try {
            try (ResultSet rs = _pswGetUnlinkedRoots.get(c()).executeQuery()) {
                Map<SID, String> m = Maps.newHashMap();
                while (rs.next()) {
                    m.put(new SID(rs.getBytes(1)), rs.getString(2));
                }
                return m;
            }
        } catch (SQLException e) {
            _pswGetUnlinkedRoots.close();
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswAddUnlinkedRoot = new PreparedStatementWrapper(
            DBUtil.insert(T_UNLINKED_ROOT, C_UNLINKED_ROOT_SID, C_UNLINKED_ROOT_NAME));
    public void addUnlinkedRoot(SID sid, String name, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswAddUnlinkedRoot.get(c());
            ps.setBytes(1, sid.getBytes());
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            _pswAddUnlinkedRoot.close();
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswRemoveUnlinkedRoot = new PreparedStatementWrapper(
            DBUtil.deleteWhere(T_UNLINKED_ROOT, C_UNLINKED_ROOT_SID + "=?"));
    public void removeUnlinkedRoot(SID sid, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswRemoveUnlinkedRoot.get(c());
            ps.setBytes(1, sid.getBytes());
            ps.executeUpdate();
        } catch (SQLException e) {
            _pswRemoveUnlinkedRoot.close();
            throw detectCorruption(e);
        }
    }
}
