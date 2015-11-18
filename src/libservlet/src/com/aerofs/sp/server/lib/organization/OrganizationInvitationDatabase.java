/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.organization;

import com.aerofs.lib.db.DBUtil;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.UserID;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.aerofs.base.id.OrganizationID;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableCollection.Builder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.*;
import java.util.List;

import static com.aerofs.lib.db.DBUtil.binaryCount;
import static com.aerofs.lib.db.DBUtil.count;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.sp.server.lib.SPSchema.*;

public class OrganizationInvitationDatabase extends AbstractSQLDatabase
{
    @Inject
    public OrganizationInvitationDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public void insert(UserID inviter, UserID invitee, OrganizationID org,
            @Nullable String signUpCode)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_OI, C_OI_INVITER, C_OI_INVITEE, C_OI_ORG_ID, C_OI_SIGNUP_CODE))) {

            ps.setString(1, inviter.getString());
            ps.setString(2, invitee.getString());
            ps.setInt(3, org.getInt());
            if (signUpCode == null) ps.setNull(4, Types.VARCHAR);
            else ps.setString(4, signUpCode);

            ps.executeUpdate();
        }
    }

    public void update(UserID inviter, UserID invitee, OrganizationID org)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.updateWhere(
                T_OI,
                C_OI_INVITEE + "=? and " + C_OI_ORG_ID + "=?",
                C_OI_INVITER, C_OI_TIMESTAMP))) {

            java.util.Date today = new java.util.Date();
            ps.setString(1, inviter.getString());
            ps.setTimestamp(2, new Timestamp(today.getTime()));
            ps.setString(3, invitee.getString());
            ps.setInt(4, org.getInt());

            ps.executeUpdate();
        }
    }

    public void delete(UserID invitee, OrganizationID org)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.deleteWhere(T_OI, C_OI_INVITEE + " =? and " + C_OI_ORG_ID + " =?"))) {

            ps.setString(1, invitee.getString());
            ps.setInt(2, org.getInt());

            int count = ps.executeUpdate();
            assert count <= 1;
            if (count == 0) throw new ExNotFound();
        }
    }

    public UserID getInviter(UserID invitee, OrganizationID org)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_OI,
                C_OI_INVITEE + "=? and " + C_OI_ORG_ID + "=?", C_OI_INVITER))) {

            ps.setString(1, invitee.getString());
            ps.setInt(2, org.getInt());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new ExNotFound();
                return UserID.fromInternal(rs.getString(1));
            }
        }
    }

    public String getCode(UserID invitee, OrganizationID org)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_OI,
                C_OI_INVITEE + "=? and " + C_OI_ORG_ID + "=?", C_OI_SIGNUP_CODE))) {

            ps.setString(1, invitee.getString());
            ps.setInt(2, org.getInt());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new ExNotFound();
                return rs.getString(1);
            }
        }
    }

    /**
     * List all the organizations that a user has been invited to join.
     */
    public List<OrganizationID> getInvitedOrganizations(UserID invitee)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_OI, C_OI_INVITEE + "=?",
                C_OI_ORG_ID))) {

            ps.setString(1, invitee.getString());
            try (ResultSet rs = ps.executeQuery()) {
                List<OrganizationID> result = Lists.newLinkedList();
                while (rs.next()) {
                    result.add(new OrganizationID(rs.getInt(1)));
                }
                return result;
            }
        }
    }

    static public class GetBySignUpCodeResult {
        public UserID _userID;
        public OrganizationID _orgID;
    }

    public @Nullable GetBySignUpCodeResult getBySignUpCodeNullable(String signUpCode)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_OI, C_OI_SIGNUP_CODE + "=?",
                C_OI_ORG_ID, C_OI_INVITEE))) {

            ps.setString(1, signUpCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                GetBySignUpCodeResult res = new GetBySignUpCodeResult();
                res._orgID = new OrganizationID(rs.getInt(1));
                res._userID = UserID.fromInternal(rs.getString(2));
                // There must be at most one result because there is a unique constraint on C_OI_SIGNUP_CODE
                assert !rs.next();
                return res;
            }
        }
    }

    /**
     * List all the user that have been invited to an organization.
     */
    public ImmutableCollection<UserID> getInvitedUsers(OrganizationID orgID)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_OI, C_OI_ORG_ID + "=?",
                C_OI_INVITEE))) {

            ps.setInt(1, orgID.getInt());
            Builder<UserID> builder = ImmutableList.builder();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    builder.add(UserID.fromInternal(rs.getString(1)));
                }
                return builder.build();
            }
        }
    }

    public int countInvitations(OrganizationID orgID)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_OI, C_OI_ORG_ID + "=?", "count(*)"))) {

            ps.setInt(1, orgID.getInt());

            try (ResultSet rs = ps.executeQuery()) {
                return count(rs);
            }
        }
    }

    public boolean hasInvite(UserID invitee, OrganizationID orgID)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_OI,
                C_OI_INVITEE + "=? and " + C_OI_ORG_ID + "=?", "count(*)"))) {

            ps.setString(1, invitee.getString());
            ps.setInt(2, orgID.getInt());

            try (ResultSet rs = ps.executeQuery()) {
                return binaryCount(rs);
            }
        }
    }
}
