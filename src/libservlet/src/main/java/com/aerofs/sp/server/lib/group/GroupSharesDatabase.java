/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.lib.group;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.GroupID;
import com.aerofs.ids.SID;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.aerofs.lib.db.DBUtil.insertedOrUpdatedOneRow;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_GS_GID;
import static com.aerofs.sp.server.lib.SPSchema.C_GS_ROLE;
import static com.aerofs.sp.server.lib.SPSchema.C_GS_SID;
import static com.aerofs.sp.server.lib.SPSchema.T_GS;

public class GroupSharesDatabase extends AbstractSQLDatabase
{
    @Inject
    public GroupSharesDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public void addSharedFolder(GroupID gid, SID sid, Permissions role)
            throws SQLException, ExAlreadyExist
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_GS, C_GS_GID, C_GS_SID, C_GS_ROLE))) {

            ps.setInt(1, gid.getInt());
            ps.setBytes(2, sid.getBytes());
            ps.setInt(3, role.bitmask());

            try {
                ps.executeUpdate();
            } catch (SQLException e) {
                throwOnConstraintViolation(e, "share " + sid.toStringFormal() + " already in group");
                throw e;
            }
        }
    }

    public void removeSharedFolder(GroupID gid, SID sid)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.deleteWhere(T_GS, C_GS_GID + "=? and " + C_GS_SID + "=?"))) {

            ps.setInt(1, gid.getInt());
            ps.setBytes(2, sid.getBytes());
            ps.executeUpdate();
        }
    }

    public boolean inSharedFolder(GroupID gid, SID sid)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_GS,
                C_GS_GID + "=? and " + C_GS_SID + "=?", "count(*)"))) {

            ps.setInt(1, gid.getInt());
            ps.setBytes(2, sid.getBytes());
            try (ResultSet rs = ps.executeQuery()) {
                return DBUtil.binaryCount(rs);
            }
        }
    }

    public List<SID> listSharedFolders(GroupID gid)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(
                T_GS,
                C_GS_GID + "=?",
                C_GS_SID))) {

            ps.setInt(1, gid.getInt());

            try (ResultSet rs = ps.executeQuery()) {
                return sharesResultSetToList(rs);
            }
        }
    }

    public List<GroupID> listJoinedGroups(SID sid)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(
                T_GS,
                C_GS_SID + "=?",
                C_GS_GID))) {

            ps.setBytes(1, sid.getBytes());

            try (ResultSet rs = ps.executeQuery()) {
                return groupsResultSetToList(rs);
            }
        }
    }

    public List<GroupIDAndRole> listJoinedGroupsAndRoles(SID sid)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(
                T_GS,
                C_GS_SID + "=?",
                C_GS_GID, C_GS_ROLE))) {

            ps.setBytes(1, sid.getBytes());

            try (ResultSet rs = ps.executeQuery()) {
                return groupRolesResultToList(rs);
            }
        }
    }

    private List<SID> sharesResultSetToList(ResultSet rs)
            throws SQLException
    {
        List<SID> shares = Lists.newArrayList();
        while (rs.next()) shares.add(new SID(rs.getBytes(1)));
        return shares;
    }

    private List<GroupID> groupsResultSetToList(ResultSet rs)
            throws SQLException
    {
        List<GroupID> shares = Lists.newArrayList();
        while (rs.next()) shares.add(GroupID.fromInternal(rs.getInt(1)));
        return shares;
    }

    private List<GroupIDAndRole> groupRolesResultToList(ResultSet rs)
            throws SQLException
    {
        List<GroupIDAndRole> shares = Lists.newArrayList();
        while (rs.next()) shares.add(new GroupIDAndRole(GroupID.fromInternal(rs.getInt(1)),
                Permissions.fromBitmask(rs.getInt(2))));
        return shares;
    }

    public void deleteSharesFor(GroupID gid)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.deleteWhere(T_GS, C_GS_GID + "=?"))) {

            ps.setInt(1, gid.getInt());
            ps.executeUpdate();
        }
    }

    public Permissions getRole(GroupID gid, SID sid)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(
                T_GS,
                C_GS_GID + "=? and " + C_GS_SID + "=?",
                C_GS_ROLE))) {

            ps.setInt(1, gid.getInt());
            ps.setBytes(2, sid.getBytes());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ExNotFound("gid " + gid.getInt() + " sid " + sid.toStringFormal() +
                            "not found");
                }
                return Permissions.fromBitmask(rs.getInt(1));
            }
        }
    }

    public void setRoleForSharedFolder(GroupID gid, SID sid, Permissions role)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(updateWhere(
                T_GS,
                C_GS_GID + "=? and " + C_GS_SID + "=?",
                C_GS_ROLE))) {

            ps.setInt(1, role.bitmask());
            ps.setInt(2, gid.getInt());
            ps.setBytes(3, sid.getBytes());

            if (ps.executeUpdate() != 1) {
                throw new ExNotFound("gid " + gid.getInt() + " sid " + sid.toStringFormal() +
                        "not found");
            }
        }
    }

    public static class GroupIDAndRole
    {
        @Nonnull public final GroupID _groupID;
        @Nonnull public final Permissions _permissions;

        public GroupIDAndRole(@Nonnull GroupID groupID, @Nonnull Permissions permissions)
        {
            _groupID = groupID;
            _permissions = permissions;
        }

        @Override
        public boolean equals(Object that)
        {
            return this == that ||
                    (that instanceof GroupIDAndRole &&
                            _groupID.equals(((GroupIDAndRole)that)._groupID) &&
                            _permissions.equals(((GroupIDAndRole)that)._permissions));
        }

        @Override
        public int hashCode()
        {
            return _groupID.hashCode() ^ _permissions.hashCode();
        }
    }
}