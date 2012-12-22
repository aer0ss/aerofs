/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.AbstractSQLDatabase;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.sp.server.lib.organization.OrganizationID;
import com.google.common.collect.Lists;

import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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

    public void addOrganizationInvitation(UserID inviter, UserID invitee, OrganizationID org)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_OI, C_OI_INVITER, C_OI_INVITEE, C_OI_ORG_ID));

        ps.setString(1, inviter.toString());
        ps.setString(2, invitee.toString());
        ps.setInt(3, org.getInt());

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
     * Get a list of all the organizations that a user has been invited to join.
     */
    public List<OrganizationID> getAllInvitedOrganizations(UserID invitee)
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
}