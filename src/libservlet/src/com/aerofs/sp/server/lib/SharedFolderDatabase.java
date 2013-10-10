/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.Util;
import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableCollection.Builder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import static com.aerofs.lib.db.DBUtil.binaryCount;
import static com.aerofs.lib.db.DBUtil.count;
import static com.aerofs.lib.db.DBUtil.deleteWhere;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_EXTERNAL;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_PENDING;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_ROLE;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_SHARER;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STORE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_NAME;
import static com.aerofs.sp.server.lib.SPSchema.T_AC;
import static com.aerofs.sp.server.lib.SPSchema.T_SF;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * N.B. only User.java may refer to this class
 */
public class SharedFolderDatabase extends AbstractSQLDatabase
{
    public SharedFolderDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public boolean has(SID sid) throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_SF, C_SF_ID + "=?", "count(*)"));

        ps.setBytes(1, sid.getBytes());
        ResultSet rs = ps.executeQuery();
        try {
            return binaryCount(rs);
        } finally {
            rs.close();
        }
    }

    /**
     * Add the given sid to the shared folder table
     */
    public void insert(SID sid, String name)
            throws SQLException, ExAlreadyExist
    {
        PreparedStatement ps = prepareStatement(DBUtil.insert(T_SF, C_SF_ID, C_SF_NAME));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, name);

        // Update returns 1 on successful insert
        try {
            Util.verify(ps.executeUpdate() == 1);
        } catch (SQLException e) {
            throwOnConstraintViolation(e, "shared folder ID already exists");
            throw e;
        }
    }

    public void insertMemberACL(SID sid, Iterable<SubjectRolePair> pairs)
            throws SQLException, ExAlreadyExist
    {
        insertACL(sid, pairs, null, false);
    }

    public void insertPendingACL(SID sid, @Nonnull UserID sharer, Iterable<SubjectRolePair> pairs)
            throws SQLException, ExAlreadyExist
    {
        checkNotNull(sharer);
        insertACL(sid, pairs, sharer, true);
    }

    private void insertACL(SID sid, Iterable<SubjectRolePair> pairs, @Nullable UserID sharer,
            boolean pending)
            throws SQLException, ExAlreadyExist
    {
        PreparedStatement ps = prepareStatement(DBUtil.insert(T_AC, C_AC_STORE_ID, C_AC_USER_ID,
                C_AC_ROLE, C_AC_PENDING, C_AC_SHARER));

        int pairCount = 0;
        for (SubjectRolePair pair : pairs) {
            ps.setBytes(1, sid.getBytes());
            ps.setString(2, pair._subject.getString());
            ps.setInt(3, pair._role.ordinal());
            ps.setBoolean(4, pending);
            if (sharer != null) {
                ps.setString(5, sharer.getString());
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.addBatch();
            ++pairCount;
        }

        try {
            // throw if any query's affected rows != 1, meaning ACL entry already exists
            // TODO (WW) this is not a proper design. Reconsider.
            executeBatch(ps, pairCount, 1); // update the roles for all users
        } catch (ExBatchSizeMismatch e) {
            /**
             * We enforce a strict API distinction between ACL creation and ACL update
             * To ensure that SP calls are not abused (i.e shareFolder should not be used to change
             * existing permissions and updateACL should not give access to new users (as it would
             * leave the DB in an intermediate state where users have access to a folder but did not
             * receive an email about it)
             */
            throw new ExAlreadyExist();
        }
    }

    private static class ExBatchSizeMismatch extends SQLException
    {
        private static final long serialVersionUID = 0;

        ExBatchSizeMismatch(String s) { super(s); }
    }

    /**
     * Execute a batch DB update and check for size mismatch in the result
     * TODO (WW) this method is not a proper design. Reconsider.
     */
    private static void executeBatch(PreparedStatement ps, int batchSize,
            int expectedRowsAffectedPerBatchEntry)
            throws SQLException
    {
        int[] batchUpdates = ps.executeBatch();
        if (batchUpdates.length != batchSize) {
            throw new ExBatchSizeMismatch("mismatch in batch size exp:" + batchSize + " act:"
                    + batchUpdates.length);
        }

        for (int rowsPerBatchEntry : batchUpdates) {
            if (rowsPerBatchEntry != expectedRowsAffectedPerBatchEntry) {
                throw new ExBatchSizeMismatch("unexpected number of affected rows " +
                        "exp:" + expectedRowsAffectedPerBatchEntry + " act:" + rowsPerBatchEntry);
            }
        }
    }

    public void setPending(SID sid, UserID userId, boolean pending)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_PENDING));

        ps.setBoolean(1, pending);
        ps.setBytes(2, sid.getBytes());
        ps.setString(3, userId.getString());

        int rows = ps.executeUpdate();

        if (rows != 1) throw new ExNotFound();
    }

    // see docs/design/sharing_and_migration.txt for information about the external flag
    public void setExternal(SID sid, UserID userId, boolean external)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_EXTERNAL));

        ps.setBoolean(1, external);
        ps.setBytes(2, sid.getBytes());
        ps.setString(3, userId.getString());

        int rows = ps.executeUpdate();

        if (rows != 1) throw new ExNotFound();
    }

    // see docs/design/sharing_and_migration.txt for information about the external flag
    public boolean isExternal(SID sid, UserID id) throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_EXTERNAL));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, id.getString());

        ResultSet rs = ps.executeQuery();
        try {
            return rs.next() ? rs.getBoolean(1) : false;
        } finally {
            rs.close();
        }
    }

    public @Nullable Role getMemberRoleNullable(SID sid, UserID userId)
            throws SQLException
    {
        return getRoleNullable(sid, userId, C_AC_PENDING + "=0");
    }

    public @Nullable Role getPendingRoleNullable(SID sid, UserID userId)
            throws SQLException
    {
        return getRoleNullable(sid, userId, C_AC_PENDING + "=1");
    }

    public @Nullable Role getMemberOrPendingRoleNullable(SID sid, UserID userId)
            throws SQLException
    {
        return getRoleNullable(sid, userId, "");
    }

    private @Nullable Role getRoleNullable(SID sid, UserID userId, String filter)
            throws SQLException
    {
        String pendingFilter = filter.isEmpty() ? "" : " and " + filter;
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?" + pendingFilter,
                C_AC_ROLE));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, userId.getString());

        ResultSet rs = ps.executeQuery();
        try {
            if (!rs.next()) { // there is no entry in the ACL table for this storeid/userid
                return null;
            } else {
                Role userRole = Role.fromOrdinal(rs.getInt(1));
                assert !rs.next();
                return userRole;
            }
        } finally {
            rs.close();
        }
    }

    @Nullable
    public UserID getSharerNullable(SID sid, UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_SHARER));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, userId.getString());

        ResultSet rs = ps.executeQuery();
        try {
            if (!rs.next()) {
                return null;
            } else {
                String sharer = rs.getString(1);
                assert !rs.next();
                return sharer == null ? null : UserID.fromInternal(sharer);
            }
        } finally {
            rs.close();
        }
    }

    public ImmutableCollection<UserID> getMembers(SID sid) throws SQLException
    {
        return getUsers(sid, false);
    }

    private ImmutableCollection<UserID> getUsers(SID sid, boolean pending)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                selectWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_PENDING + "=?", C_AC_USER_ID));

        ps.setBytes(1, sid.getBytes());
        ps.setBoolean(2, pending);

        ResultSet rs = ps.executeQuery();
        try {
            Builder<UserID> users = ImmutableList.builder();
            while (rs.next()) users.add(UserID.fromInternal(rs.getString(1)));
            return users.build();
        } finally {
            rs.close();
        }
    }

    /**
     * @return all the users, including members, pending users, and Team Servers
     */
    public ImmutableCollection<UserID> getAllUsers(SID sid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                selectWhere(T_AC, C_AC_STORE_ID + "=?", C_AC_USER_ID));

        ps.setBytes(1, sid.getBytes());

        ResultSet rs = ps.executeQuery();
        try {
            Builder<UserID> users = ImmutableList.builder();
            while (rs.next()) users.add(UserID.fromInternal(rs.getString(1)));
            return users.build();
        } finally {
            rs.close();
        }
    }

    public List<SubjectRolePair> getMemberACL(SID sid) throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_PENDING + "=?",
                C_AC_USER_ID, C_AC_ROLE));

        ps.setBytes(1, sid.getBytes());
        ps.setBoolean(2, false);

        ResultSet rs = ps.executeQuery();
        try {
            List<SubjectRolePair> srps = Lists.newArrayList();
            while (rs.next()) {
                srps.add(new SubjectRolePair(UserID.fromInternal(rs.getString(1)),
                        Role.fromOrdinal(rs.getInt(2))));
            }
            return srps;
        } finally {
            rs.close();
        }
    }

    public void deleteMemberOrPendingACL(SID sid, UserID userID)
            throws ExNotFound, SQLException
    {
        PreparedStatement ps = prepareStatement(
                deleteWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?"));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, userID.getString());
        if (ps.executeUpdate() != 1) throw new ExNotFound();
    }

    public boolean hasOwnerMemberOrPending(SID sid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_ROLE + "=?",
                "count(*)"));

        ps.setBytes(1, sid.getBytes());
        ps.setInt(2, Role.OWNER.ordinal());

        ResultSet rs = ps.executeQuery();
        try {
            return count(rs) != 0;
        } finally {
            rs.close();
        }
    }

    public void updateMemberACL(SID sid, UserID userID, Role role)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=? and " + C_AC_PENDING + "=?",
                C_AC_ROLE));

        ps.setInt(1, role.ordinal());
        ps.setBytes(2, sid.getBytes());
        ps.setString(3, userID.getString());
        ps.setBoolean(4, false);        // ignore pending entries

        if (ps.executeUpdate() != 1) throw new ExNotFound();
    }

    public void updateACL(SID sid, UserID userID, Role role)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_ROLE));

        ps.setInt(1, role.ordinal());
        ps.setBytes(2, sid.getBytes());
        ps.setString(3, userID.getString());

        if (ps.executeUpdate() != 1) throw new ExNotFound();
    }

    public void delete(SID sid)
            throws SQLException
    {
        PreparedStatement ps;

        // remove all ACLs
        ps = prepareStatement(DBUtil.deleteWhere(T_AC, C_AC_STORE_ID + "=?"));
        ps.setBytes(1, sid.getBytes());
        Util.verify(ps.executeUpdate() > 0);

        // remove shared folder
        ps = prepareStatement(DBUtil.deleteWhere(T_SF, C_SF_ID + "=?"));
        ps.setBytes(1, sid.getBytes());
        Util.verify(ps.executeUpdate() > 0);
    }

    public @Nonnull String getName(SID sid)
            throws SQLException, ExNotFound
    {
        ResultSet rs = querySharedFolder(sid, C_SF_NAME);
        try {
            return rs.getString(1);
        } finally {
            rs.close();
        }
    }

    private ResultSet querySharedFolder(SID sid, String field)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_SF, C_SF_ID + "=?", field));
        ps.setBytes(1, sid.getBytes());
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            throw new ExNotFound("shared folder " + sid);
        } else {
            return rs;
        }
    }
}
