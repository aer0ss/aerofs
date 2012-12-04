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
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;
import com.aerofs.servlets.lib.db.AbstractSQLDatabase;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aerofs.lib.db.DBUtil.binaryCount;
import static com.aerofs.lib.db.DBUtil.count;
import static com.aerofs.lib.db.DBUtil.deleteWhere;
import static com.aerofs.lib.db.DBUtil.insert;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_ROLE;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STORE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_FI_SID;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ACL_EPOCH;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_AC;
import static com.aerofs.sp.server.lib.SPSchema.T_FI;
import static com.aerofs.sp.server.lib.SPSchema.T_SF;
import static com.aerofs.sp.server.lib.SPSchema.T_USER;

/**
 * N.B. only User.java may refer to this class
 */
public class SharedFolderDatabase extends AbstractSQLDatabase
{
    private final static Logger l = Util.l(SharedFolderDatabase.class);

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
     *
     * @pre the SID doesn't exist
     */
    public void add(SID sid, String name) throws SQLException
    {
        PreparedStatement ps = prepareStatement(insert(T_SF, C_SF_ID, C_SF_NAME));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, name);

        // Update returns 1 on successful insert
        Util.verify(ps.executeUpdate() == 1);
    }

    public boolean isOwner(SID sid, UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=? and " + C_AC_ROLE + " = ?",
                "count(*)"));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, userId.toString());
        ps.setInt(3, Role.OWNER.ordinal());

        ResultSet rs = ps.executeQuery();
        try {
            return binaryCount(rs);
        } finally {
            rs.close();
        }
    }

    public void addACL(SID sid, Iterable<SubjectRolePair> pairs)
            throws SQLException, ExAlreadyExist
    {
        PreparedStatement ps = prepareStatement(insert(T_AC,
                C_AC_STORE_ID, C_AC_USER_ID, C_AC_ROLE));

        int pairCount = 0;
        for (SubjectRolePair pair : pairs) {
            ps.setBytes(1, sid.getBytes());
            ps.setString(2, pair._subject.toString());
            ps.setInt(3, pair._role.ordinal());
            ps.addBatch();
            ++pairCount;
        }

        try {
            // throw if any query's affected rows != 1, meaning ACL entry already exists
            executeBatch(ps, pairCount, 1); // update the roles for all users
        } catch (ExSizeMismatch e) {
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

    public @Nullable Role getRoleNullable(SID sid, UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC, C_AC_STORE_ID + "=? and " +
                C_AC_USER_ID + "=?", C_AC_ROLE));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, userId.toString());
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

    public Set<UserID> getACLUsers(SID sid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                selectWhere(T_AC, C_AC_STORE_ID + "=?", C_AC_USER_ID));

        ps.setBytes(1, sid.getBytes());

        ResultSet rs = ps.executeQuery();
        try {
            Set<UserID> subjects = Sets.newHashSet();
            while (rs.next()) subjects.add(UserID.fromInternal(rs.getString(1)));
            return subjects;
        } finally {
            rs.close();
        }
    }

    public Map<UserID, Long> incrementACLEpoch(Set<UserID> users)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement("update " + T_USER +
                " set " + C_USER_ACL_EPOCH + "=" + C_USER_ACL_EPOCH + "+1 where " + C_USER_ID +
                "=?");

        for (UserID user : users) {
            l.info("increment epoch for " + user);
            ps.setString(1, user.toString());
            ps.addBatch();
        }

        executeBatchWarn(ps, users.size(), 1);

        return getACLEpochs(users);
    }

    /**
     * @param users set of users for whom you want the acl epoch number
     * @return a map of user -> epoch number
     */
    private Map<UserID, Long> getACLEpochs(Set<UserID> users)
            throws SQLException
    {
        PreparedStatement ps = preapreGetACLEpochStatemet();
        Map<UserID, Long> epochs = Maps.newHashMap();
        for (UserID user : users) epochs.put(user, queryGetACLEpoch(ps, user));
        return epochs;
    }

    private long getACLEpoch(UserID user)
            throws SQLException
    {
        PreparedStatement ps = preapreGetACLEpochStatemet();
        return queryGetACLEpoch(ps, user);
    }

    private long queryGetACLEpoch(PreparedStatement ps, UserID user)
            throws SQLException
    {
        ps.setString(1, user.toString());
        ResultSet rs = ps.executeQuery();
        try {
            Util.verify(rs.next());
            long epoch = rs.getLong(1);
            assert !rs.next();
            return epoch;
        } finally {
            rs.close();
        }
    }

    private PreparedStatement preapreGetACLEpochStatemet()
            throws SQLException
    {
        return  prepareStatement(selectWhere(T_USER, C_USER_ID + "=?", C_USER_ACL_EPOCH));
    }

    public void deleteACL(SID sid, Collection<UserID> subjects)
            throws ExNotFound, SQLException
    {
        PreparedStatement ps = prepareStatement(
                deleteWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?"));

        for (UserID subject : subjects) {
            ps.setBytes(1, sid.getBytes());
            ps.setString(2, subject.toString());
            ps.addBatch();
        }

        try {
            executeBatch(ps, subjects.size(), 1);
        } catch (ExSizeMismatch e) {
            throw new ExNotFound();
        }
    }

    public boolean hasOwner(SID sid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                selectWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_ROLE + "=?", "count(*)"));

        ps.setBytes(1, sid.getBytes());
        ps.setInt(2, Role.OWNER.ordinal());

        ResultSet rs = ps.executeQuery();
        try {
            return count(rs) != 0;
        } finally {
            rs.close();
        }
    }

    public void updateACL(SID sid, Iterable<SubjectRolePair> pairs)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_ROLE));

        int pairCount = 0;
        for (SubjectRolePair pair : pairs) {
            ps.setInt(1, pair._role.ordinal());
            ps.setBytes(2, sid.getBytes());
            ps.setString(3, pair._subject.toString());
            ps.addBatch();
            ++pairCount;
        }

        try {
            // throw if any query's affected rows != 1, meaning ACL entry doesn't exist
            executeBatch(ps, pairCount, 1); // update the roles for all users
        } catch (ExSizeMismatch e) {
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

        // remove all invitations
        ps = prepareStatement(DBUtil.deleteWhere(T_FI, C_FI_SID + "=?"));
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

    public static class GetACLResult
    {
        public final long _epoch;
        public final Map<SID, List<SubjectRolePair>> _sid2srps;

        GetACLResult(long epoch, Map<SID, List<SubjectRolePair>> sid2srps)
        {
            _epoch = epoch;
            _sid2srps = sid2srps;
        }
    }

    /**
     * TODO (WW) This method, as well as getACLEpoch* methods MUST be refactored. getACLEpoch*
     * should belong to User, and this method should be split into two parts, with one part in User
     * and the other in SharedFolder:
     *
     *      if (user.hasACLEpochChagned()) {
     *          acls = Maps.new...;
     *          for (sf : user.getAllSharedFolders()) acls.put(sf.getACL());
     *      }
     */
    public GetACLResult getACL(long userEpoch, UserID user)
            throws SQLException
    {
        //
        // first check if the user actually needs to get the acl
        //

        long epoch = getACLEpoch(user);
        assert epoch >= userEpoch : userEpoch + " > " + epoch;

        if (epoch == userEpoch) {
            l.info("server epoch:" + epoch + " matches user epoch:" + userEpoch);
            return new GetACLResult(epoch, Collections.<SID, List<SubjectRolePair>>emptyMap());
        }

        //
        // apparently the user is out of date
        //

        PreparedStatement ps = prepareStatement("select acl_master." +
                C_AC_STORE_ID + ", acl_master." + C_AC_USER_ID + ", acl_master." +
                C_AC_ROLE + " from " + T_AC + " as acl_master inner join " + T_AC +
                " as acl_filter using (" + C_AC_STORE_ID + ") where acl_filter." +
                C_AC_USER_ID + "=?");

        ps.setString(1, user.toString());

        Map<SID, List<SubjectRolePair>> sid2srps = Maps.newHashMap();

        ResultSet rs = ps.executeQuery();
        try {
            while (rs.next()) {
                SID sid = new SID(rs.getBytes(1));

                if (!sid2srps.containsKey(sid)) {
                    sid2srps.put(sid, new LinkedList<SubjectRolePair>());
                }

                UserID subject = UserID.fromInternal(rs.getString(2));
                Role role = Role.fromOrdinal(rs.getInt(3));

                sid2srps.get(sid).add(new SubjectRolePair(subject, role));
            }
        } finally {
            rs.close();
        }

        return new GetACLResult(epoch, sid2srps);
    }
}
