/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableCollection.Builder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.lib.db.DBUtil.binaryCount;
import static com.aerofs.lib.db.DBUtil.count;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_OI_INVITEE;
import static com.aerofs.sp.server.lib.SPSchema.C_OI_INVITER;
import static com.aerofs.sp.server.lib.SPSchema.C_OI_ORG_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_OI;

public class OrganizationInvitationDatabase extends AbstractSQLDatabase
{
    public OrganizationInvitationDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public void insert(UserID inviter, UserID invitee, OrganizationID org)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_OI, C_OI_INVITER, C_OI_INVITEE, C_OI_ORG_ID));

        ps.setString(1, inviter.toString());
        ps.setString(2, invitee.toString());
        ps.setInt(3, org.getInt());

        ps.executeUpdate();
    }

    public void delete(UserID invitee, OrganizationID org)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.deleteWhere(T_OI, C_OI_INVITEE + " =? and " + C_OI_ORG_ID + " =?"));

        ps.setString(1, invitee.toString());
        ps.setInt(2, org.getInt());

        ps.executeUpdate();
    }

    public UserID getInviter(UserID invitee, OrganizationID org)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_OI,
                C_OI_INVITEE + "=? and " + C_OI_ORG_ID + "=?", C_OI_INVITER));

        ps.setString(1, invitee.toString());
        ps.setInt(2, org.getInt());

        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            throw new ExNotFound();
        }

        try {
            return UserID.fromInternal(rs.getString(1));
        } finally {
            rs.close();
        }
    }

    /**
     * List all the organizations that a user has been invited to join.
     */
    public List<OrganizationID> getInvitedOrganizations(UserID invitee)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_OI, C_OI_INVITEE + "=?",
                C_OI_ORG_ID));

        ps.setString(1, invitee.toString());
        ResultSet rs = ps.executeQuery();

        List<OrganizationID> result = Lists.newLinkedList();
        try {
            while (rs.next()) {
                result.add(new OrganizationID(rs.getInt(1)));
            }
        } finally {
            rs.close();
        }

        return result;
    }

    /**
     * List all the user that have been invited to an organization.
     */
    public ImmutableCollection<UserID> getInvitedUsers(OrganizationID orgID)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_OI, C_OI_ORG_ID + "=?",
                C_OI_INVITEE));

        ps.setInt(1, orgID.getInt());
        ResultSet rs = ps.executeQuery();

        Builder<UserID> builder = ImmutableList.builder();
        try {
            while (rs.next()) {
                builder.add(UserID.fromInternal(rs.getString(1)));
            }
        } finally {
            rs.close();
        }

        return builder.build();
    }

    public int countInvitations(OrganizationID orgID)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_OI, C_OI_ORG_ID + "=?", "count(*)"));

        ps.setInt(1, orgID.getInt());

        ResultSet rs = ps.executeQuery();
        try {
            return count(rs);
        } finally {
            rs.close();
        }
    }

    public boolean hasInvite(UserID invitee, OrganizationID orgID)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_OI,
                C_OI_INVITEE + "=? and " + C_OI_ORG_ID + "=?", "count(*)"));

        ps.setString(1, invitee.toString());
        ps.setInt(2, orgID.getInt());

        ResultSet rs = ps.executeQuery();
        try {
            return binaryCount(rs);
        } finally {
            rs.close();
        }
    }
}
