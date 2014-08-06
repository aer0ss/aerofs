/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.group;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.GroupID;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.aerofs.base.id.OrganizationID;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_SG_COMMON_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_SG_EXTERNAL_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SG_GID;
import static com.aerofs.sp.server.lib.SPSchema.C_SG_ORG_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_SG;

public class GroupDatabase extends AbstractSQLDatabase
{
    @Inject
    public GroupDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public boolean hasGroup(GroupID gid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_SG, C_SG_GID + "=?", "count(*)"));
        ps.setInt(1, gid.getInt());
        ResultSet rs = ps.executeQuery();

        try {
            return DBUtil.binaryCount(rs);
        } finally {
            rs.close();
        }
    }

    public void createGroup(GroupID gid, String commonName, OrganizationID orgId,
            @Nullable byte[] externalId)
            throws SQLException, ExAlreadyExist
    {
        // Create the group row.
        PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_SG, C_SG_GID, C_SG_COMMON_NAME, C_SG_ORG_ID, C_SG_EXTERNAL_ID));

        ps.setInt(1, gid.getInt());
        ps.setString(2, commonName);
        ps.setInt(3, orgId.getInt());
        ps.setBytes(4, externalId);

        try {
            ps.executeUpdate();
        } catch (SQLException e) {
            throwOnConstraintViolation(e, "group ID already exists");
            throw e;
        }
    }

    public void deleteGroup(GroupID gid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.deleteWhere(T_SG, C_SG_GID + "=?"));

        ps.setInt(1, gid.getInt());
        ps.executeUpdate();
    }

    public void setCommonName(GroupID gid, String commonName)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                updateWhere(T_SG, C_SG_GID + "=?", C_SG_COMMON_NAME)
        );

        ps.setString(1, commonName);
        ps.setInt(2, gid.getInt());
        Util.verify(ps.executeUpdate() == 1);
    }

    public String getCommonName(GroupID gid)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryGroup(gid, C_SG_COMMON_NAME);
        try {
            return rs.getString(1);
        } finally {
            rs.close();
        }
    }

    public OrganizationID getOrganizationID(GroupID gid)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryGroup(gid, C_SG_ORG_ID);
        try {
            return new OrganizationID(rs.getInt(1));
        } finally {
            rs.close();
        }
    }

    public @Nullable byte[] getExternalIdNullable(GroupID gid)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryGroup(gid, C_SG_EXTERNAL_ID);
        try {
            return rs.getBytes(1);
        } finally {
            rs.close();
        }
    }

    private ResultSet queryGroup(GroupID gid, String ... fields)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_SG, C_SG_GID + "=?", fields));
        ps.setInt(1, gid.getInt());
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            throw new ExNotFound("group " + gid + " not found");
        } else {
            return rs;
        }
    }

    /**
     * List groups using a search prefix for the group common name.
     *
     * The same pattern is used for searching for users in an organization.
     *
     * TODO (MP) de-dupe code with org db if this pattern is used elsewhere.
     * @see com.aerofs.sp.server.lib.organization.OrganizationDatabase#listUsers
     */
    public List<GroupID> listGroups(OrganizationID orgId, int offset, int maxResults,
            @Nullable String searchPrefix)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(DBUtil.selectWhere(
                T_SG,
                C_SG_ORG_ID + "=?" + andCommonNameLike(searchPrefix),
                C_SG_GID)
                + " order by " + C_SG_COMMON_NAME + " limit ? offset ?");

        int index = 0;
        ps.setInt(++index, orgId.getInt());
        if (searchPrefix != null) ps.setString(++index, searchPrefix + "%");
        ps.setInt(++index, maxResults);
        ps.setInt(++index, offset);

        ResultSet rs = ps.executeQuery();
        try {
            return groupsResultSet2List(rs);
        } finally {
            rs.close();
        }
    }

    private static String andCommonNameLike(String searchPrefix)
    {
        if (searchPrefix == null) {
            return "";
        } else {
            return " and " + C_SG_COMMON_NAME + " like ?";
        }
    }

    private List<GroupID> groupsResultSet2List(ResultSet rs)
            throws SQLException
    {
        List<GroupID> groups = Lists.newArrayList();
        while (rs.next()) groups.add(new GroupID(rs.getInt(1)));
        return groups;
    }
}
