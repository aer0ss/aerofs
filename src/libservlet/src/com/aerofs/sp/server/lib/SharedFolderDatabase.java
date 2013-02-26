/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.Util;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.aerofs.lib.db.DBUtil.binaryCount;
import static com.aerofs.lib.db.DBUtil.count;
import static com.aerofs.lib.db.DBUtil.deleteWhere;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_PENDING;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_ROLE;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_SHARER;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STORE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_NAME;
import static com.aerofs.sp.server.lib.SPSchema.T_AC;
import static com.aerofs.sp.server.lib.SPSchema.T_SF;

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

    public void insertMemberACL(SID sid, UserID sharer, Iterable<SubjectRolePair> pairs)
            throws SQLException, ExAlreadyExist
    {
        insertACL(sid, pairs, sharer, false);
    }

    public void insertPendingACL(SID sid, UserID sharer, Iterable<SubjectRolePair> pairs)
            throws SQLException, ExAlreadyExist
    {
        insertACL(sid, pairs, sharer, true);
    }

    private void insertACL(SID sid, Iterable<SubjectRolePair> pairs, UserID sharer, boolean pending)
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
            ps.setString(5, sharer.getString());
            ps.addBatch();
            ++pairCount;
        }

        try {
            // throw if any query's affected rows != 1, meaning ACL entry already exists
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

    public Set<UserID> getMembers(SID sid) throws SQLException
    {
        return getUsers(sid, false);
    }

    private Set<UserID> getUsers(SID sid, boolean pending)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                selectWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_PENDING + "=?", C_AC_USER_ID));

        ps.setBytes(1, sid.getBytes());
        ps.setBoolean(2, pending);

        ResultSet rs = ps.executeQuery();
        try {
            Set<UserID> subjects = Sets.newHashSet();
            while (rs.next()) subjects.add(UserID.fromInternal(rs.getString(1)));
            return subjects;
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

    public void deleteMemberOrPendingACL(SID sid, Collection<UserID> subjects)
            throws ExNotFound, SQLException
    {
        PreparedStatement ps = prepareStatement(
                deleteWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?"));

        for (UserID subject : subjects) {
            ps.setBytes(1, sid.getBytes());
            ps.setString(2, subject.getString());
            ps.addBatch();
        }

        try {
            executeBatch(ps, subjects.size(), 1);
        } catch (ExBatchSizeMismatch e) {
            throw new ExNotFound();
        }
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

    public void updateMemberACL(SID sid, Iterable<SubjectRolePair> pairs)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=? and " + C_AC_PENDING + "=?",
                C_AC_ROLE));

        int pairCount = 0;
        for (SubjectRolePair pair : pairs) {
            ps.setInt(1, pair._role.ordinal());
            ps.setBytes(2, sid.getBytes());
            ps.setString(3, pair._subject.getString());
            ps.setBoolean(4, false);        // ignore pending entries
            ps.addBatch();
            ++pairCount;
        }

        try {
            // throw if any query's affected rows != 1, meaning ACL entry doesn't exist
            executeBatch(ps, pairCount, 1); // update the roles for all users
        } catch (ExBatchSizeMismatch e) {
            /**
             * We enforce a strict API distinction between ACL creation and ACL update
             * To ensure that SP calls are not abused (i.e shareFolder should not be used to change
             * existing permissions and updateACL should not give access to new users (as it would
             * leave the DB in an intermediate state where users have access to a folder but did not
             * receive an email about it)
             */
            throw new ExNotFound();
        }
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
