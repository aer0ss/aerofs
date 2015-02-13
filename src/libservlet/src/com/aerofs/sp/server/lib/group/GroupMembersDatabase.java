/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.lib.group;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.id.GroupID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.google.common.collect.Lists;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_GM_GID;
import static com.aerofs.sp.server.lib.SPSchema.C_GM_MEMBER_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_GM;

public class GroupMembersDatabase extends AbstractSQLDatabase
{
    @Inject
    public GroupMembersDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public void addMember(GroupID gid, UserID memberId)
            throws SQLException, ExAlreadyExist
    {
        // Create the group row.
        PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_GM, C_GM_GID, C_GM_MEMBER_ID));

        ps.setInt(1, gid.getInt());
        ps.setString(2, memberId.getString());

        try {
            ps.executeUpdate();
        } catch (SQLException e) {
            throwOnConstraintViolation(e, "user " + memberId.getString() + " already in group");
            throw e;
        }
    }

    public void removeMember(GroupID gid, UserID memberId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.deleteWhere(T_GM, C_GM_GID + "=? and " + C_GM_MEMBER_ID + "=?"));

        ps.setInt(1, gid.getInt());
        ps.setString(2, memberId.getString());
        ps.executeUpdate();
    }

    public boolean hasMember(GroupID gid, UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(
                T_GM,
                C_GM_GID + "=? and " + C_GM_MEMBER_ID + "=?",
                "count(*)"));

        ps.setInt(1, gid.getInt());
        ps.setString(2, userId.getString());
        ResultSet rs = ps.executeQuery();

        try {
            return DBUtil.binaryCount(rs);
        } finally {
            rs.close();
        }
    }

    public List<UserID> listMembers(GroupID gid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(DBUtil.selectWhere(
                T_GM,
                C_GM_GID + "=?",
                C_GM_MEMBER_ID)
                + " order by " + C_GM_MEMBER_ID);

        ps.setInt(1, gid.getInt());

        ResultSet rs = ps.executeQuery();
        try {
            return membersResultSetToList(rs);
        } finally {
            rs.close();
        }
    }

    private List<UserID> membersResultSetToList(ResultSet rs)
            throws SQLException
    {
        List<UserID> users = Lists.newArrayList();
        while (rs.next()) users.add(UserID.fromInternal(rs.getString(1)));
        return users;
    }

    private List<GroupID> groupsResultSetToList(ResultSet rs)
            throws SQLException
    {
        List<GroupID> groups = Lists.newArrayList();
        while(rs.next()) groups.add(GroupID.fromInternal(rs.getInt(1)));
        return groups;
    }

    public void deleteMembersFor(GroupID gid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.deleteWhere(T_GM, C_GM_GID + "=?"));

        ps.setInt(1, gid.getInt());
        ps.executeUpdate();
    }

    public List<GroupID> listGroupsFor(UserID userID)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(DBUtil.selectWhere(
                T_GM,
                C_GM_MEMBER_ID + "=?",
                C_GM_GID));

        ps.setString(1, userID.getString());
        ResultSet rs = ps.executeQuery();
        try {
            return groupsResultSetToList(rs);
        } finally {
            rs.close();
        }
    }
}
