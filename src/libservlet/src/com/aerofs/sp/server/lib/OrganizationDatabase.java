/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.aerofs.lib.db.DBUtil.count;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STORE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_O_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_O_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_O_CONTACT_PHONE;
import static com.aerofs.sp.server.lib.SPSchema.C_O_STRIPE_CUSTOMER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_AUTHORIZATION_LEVEL;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ORG_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_AC;
import static com.aerofs.sp.server.lib.SPSchema.T_ORGANIZATION;
import static com.aerofs.sp.server.lib.SPSchema.T_USER;

/**
 * N.B. only Organization.java may refer to this class
 */
public class OrganizationDatabase extends AbstractSQLDatabase
{
    public OrganizationDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    /**
     * @throws ExAlreadyExist if the organization ID already exists
     */
    public void insert(OrganizationID organizationId, String organizationName,
            String organizationPhone, StripeCustomerID stripeCustomerId)
            throws SQLException, ExAlreadyExist
    {
        checkNotNull(organizationId, "organizationId cannot be null");
        checkNotNull(stripeCustomerId, "stripeCustomerId cannot be null");

        try {
            PreparedStatement ps = prepareStatement(
                    DBUtil.insert(T_ORGANIZATION, C_O_ID, C_O_NAME, C_O_CONTACT_PHONE,
                            C_O_STRIPE_CUSTOMER_ID));

            ps.setInt(1, organizationId.getInt());
            ps.setString(2, organizationName);
            ps.setString(3, organizationPhone);
            ps.setString(4, stripeCustomerId.getID());
            ps.executeUpdate();
        } catch (SQLException e) {
            throwOnConstraintViolation(e, "organization ID already exists");
            throw e;
        }
    }

    /**
     * @return null if the organization doesn't have a Stripe Customer ID. Even though insert()
     *      does enforce non-null Customer IDs we have legacy organizations that don't have the ID.
     * @throws ExNotFound if the organization doesn't exist
     */
    public @Nullable StripeCustomerID getStripeCustomerIDNullable(final OrganizationID orgID)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryOrg(orgID, C_O_STRIPE_CUSTOMER_ID);
        try {
            String id = rs.getString(1);
            return id == null ? null : StripeCustomerID.create(id);
        } finally {
            rs.close();
        }
    }

    public @Nonnull String getName(OrganizationID orgID)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryOrg(orgID, C_O_NAME);
        try {
            return rs.getString(1);
        } finally {
            rs.close();
        }
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setName(OrganizationID orgID, String name)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                updateWhere(T_ORGANIZATION, C_O_ID + "=?", C_O_NAME));

        ps.setString(1, name);
        ps.setInt(2, orgID.getInt());

        Util.verify(ps.executeUpdate() == 1);
    }

    /**
     * @return null if the organization doesn't have a phone number. Even though insert()
     *      does enforce non-null numbers we have legacy organizations that don't have the number.
     * @throws ExNotFound if the organization doesn't exist
     */
    @Nullable
    public String getContactPhoneNullable(final OrganizationID orgID) throws SQLException, ExNotFound
    {
        final ResultSet rs = queryOrg(orgID, C_O_CONTACT_PHONE);

        try {
            return rs.getString(1);
        } finally {
            rs.close();
        }
    }

    public void setContactPhone(final OrganizationID orgID, final String contactPhone)
            throws SQLException
    {
        final PreparedStatement ps = prepareStatement(updateWhere(T_ORGANIZATION, C_O_ID + "=?",
                C_O_CONTACT_PHONE));

        ps.setString(1, contactPhone);
        ps.setInt(2, orgID.getInt());

        Util.verify(ps.executeUpdate() == 1);
    }

    private ResultSet queryOrg(OrganizationID orgID, String field)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_ORGANIZATION, C_O_ID + "=?", field));
        ps.setInt(1, orgID.getInt());
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            throw new ExNotFound("org " + orgID);
        } else {
            return rs;
        }
    }

    private static String andNotTeamServer()
    {
        return " and " + C_USER_ID + " not like ':%' ";
    }

    /**
     * @param rs Result set of tuples of the form (id, first name, last name).
     * @return  List of users in the result set.
     */
    private List<UserID> usersResultSet2List(ResultSet rs)
            throws SQLException
    {
        List<UserID> users = Lists.newArrayList();
        while (rs.next()) users.add(UserID.fromInternal(rs.getString(1)));
        return users;
    }

    /**
     * @param orgId ID of the organization.
     * @param offset Starting index of the results list from the database.
     * @param maxResults Maximum number of results returned from the database.
     * @return List of users under the organization {@code orgId}
     * between [offset, offset + maxResults].
     */
    public List<UserID> listUsers(OrganizationID orgId, int offset, int maxResults)
            throws SQLException
    {
        PreparedStatement psLU = prepareStatement(
                "select " + C_USER_ID + " from " + T_USER +
                        " where " + C_USER_ORG_ID + "=? " + andNotTeamServer() + " order by " +
                        C_USER_ID + " limit ? offset ?");

        psLU.setInt(1, orgId.getInt());
        psLU.setInt(2, maxResults);
        psLU.setInt(3, offset);

        ResultSet rs = psLU.executeQuery();
        try {
            return usersResultSet2List(rs);
        } finally {
            rs.close();
        }
    }

    /**
     * @param orgId ID of the organization.
     * @param offset Starting index of the results list from the database.
     * @param maxResults Maximum number of results returned from the database.
     * @param search Search term that we want to match in user id.
     * @return List of users that contains the {@code search} term as part of their user IDs.
     * The users are under the organization {@code orgId}, and the list is between
     * [offset, offset + maxResults].
     */
    public List<UserID> searchUsers(OrganizationID orgId, int offset, int maxResults, String search)
            throws SQLException
    {
        PreparedStatement psSLU = prepareStatement("select " + C_USER_ID + " from " + T_USER +
                " where " + C_USER_ORG_ID + "=? and " + C_USER_ID + " like ? " +
                andNotTeamServer() + " order by " + C_USER_ID + " limit ? offset ?");

        psSLU.setInt(1, orgId.getInt());
        psSLU.setString(2, "%" + search + "%");
        psSLU.setInt(3, maxResults);
        psSLU.setInt(4, offset);

        ResultSet rs = psSLU.executeQuery();
        try {
            return usersResultSet2List(rs);
        } finally {
            rs.close();
        }
    }

    /**
     * @param orgId ID of the organization.
     * @param offset Starting index of the results list from the database.
     * @param maxResults Maximum number of results returned from the database.
     * @param authLevel Authorization level of the users.
     * @return List of users with the given authorization level {@code authLevel} under
     * the organization {@code orgId} between [offset, offset + maxResults].
     */
    public List<UserID> listUsersWithAuthorization(OrganizationID orgId, int offset, int maxResults,
            AuthorizationLevel authLevel)
            throws SQLException
    {
        PreparedStatement psLUA = prepareStatement(
                "select " + C_USER_ID + " from " + T_USER +
                        " where " + C_USER_ORG_ID + "=? and " +
                        C_USER_AUTHORIZATION_LEVEL + "=? " + andNotTeamServer() +
                        "order by " + C_USER_ID + " limit ? offset ?"
        );

        psLUA.setInt(1, orgId.getInt());
        psLUA.setInt(2, authLevel.ordinal());
        psLUA.setInt(3, maxResults);
        psLUA.setInt(4, offset);

        ResultSet rs = psLUA.executeQuery();
        try {
            return usersResultSet2List(rs);
        } finally {
            rs.close();
        }
    }

    /**
     * @param orgId ID of the organization.
     * @param offset Starting index of the results list from the database.
     * @param maxResults Maximum number of results returned from the database.
     * @param authLevel Authorization level of the users.
     * @param search String representing the search term that we want to match in user id.
     * @return List of users that contains the {@code search} term as part of their user IDs and
     * have the authorization level {@code authLevel}.
     * The users are under the organization {@code orgId}, and the list is between
     * [offset, offset + maxResults].
     */
    public List<UserID> searchUsersWithAuthorization(OrganizationID orgId, int offset,
            int maxResults, AuthorizationLevel authLevel, String search)
            throws SQLException
    {
        PreparedStatement psSUA = prepareStatement(
                "select " + C_USER_ID + " from " + T_USER +
                        " where " + C_USER_ORG_ID + "=? and " +
                        C_USER_ID + " like ? and " +
                        C_USER_AUTHORIZATION_LEVEL + "=? " + andNotTeamServer() +
                        "order by " + C_USER_ID + " limit ? offset ?"
        );

        psSUA.setInt(1, orgId.getInt());
        psSUA.setString(2, "%" + search + "%");
        psSUA.setInt(3, authLevel.ordinal());
        psSUA.setInt(4, maxResults);
        psSUA.setInt(5, offset);

        ResultSet rs = psSUA.executeQuery();
        try {
            return usersResultSet2List(rs);
        } finally {
            rs.close();
        }
    }

    /**
     * @param orgId ID of the organization.
     * @return Number of users in the organization {@code orgId}.
     */
    public int countUsers(OrganizationID orgId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_USER,
                C_USER_ORG_ID + "=?" + andNotTeamServer(), "count(*)"));

        ps.setInt(1, orgId.getInt());
        ResultSet rs = ps.executeQuery();
        try {
            return count(rs);
        } finally {
            rs.close();
        }
    }

    /**
     * @param orgId ID of the organization.
     * @param search Search term that we want to match in user id.
     * @return Number of users in the organization {@code orgId}
     * with user ids containing the search term {@code search}.
     */
    public int searchUsersCount(OrganizationID orgId, String search)
            throws SQLException
    {
        PreparedStatement psSCU = prepareStatement("select count(*) from " +
                T_USER + " where " + C_USER_ORG_ID + "=? and " + C_USER_ID + " like ?" +
                andNotTeamServer());

        psSCU.setInt(1, orgId.getInt());
        psSCU.setString(2, "%" + search + "%");

        ResultSet rs = psSCU.executeQuery();
        try {
            return count(rs);
        } finally {
            rs.close();
        }
    }

    /**
     * @param authlevel Authorization level of the users.
     * @param orgId ID of the organization.
     * @return Number of users in the organization with the given authorization level.
     */
    public int listUsersWithAuthorizationCount(AuthorizationLevel authlevel, OrganizationID orgId)
            throws SQLException
    {
        PreparedStatement psLUAC = prepareStatement("select count(*) from " +
                T_USER + " where " + C_USER_ORG_ID + "=? and " +
                C_USER_AUTHORIZATION_LEVEL + "=?" + andNotTeamServer());

        psLUAC.setInt(1, orgId.getInt());
        psLUAC.setInt(2, authlevel.ordinal());

        ResultSet rs = psLUAC.executeQuery();
        try {
            return count(rs);
        } finally {
            rs.close();
        }
    }

    /**
     * @param authLevel Authorization level of the users.
     * @param orgId ID of the organization.
     * @param search Search term that we want to match in user id.
     * @return Number of users in the organization {@code orgId} with user ids
     * containing the search term {@code search} and authorization level {@code authLevel}.
     */
    public int searchUsersWithAuthorizationCount(AuthorizationLevel authLevel,
            OrganizationID orgId, String search)
            throws SQLException
    {
        PreparedStatement psSUAC = prepareStatement(
                "select count(*) from " + T_USER + " where " + C_USER_ORG_ID + "=? and " +
                        C_USER_ID + " like ? and " +
                        C_USER_AUTHORIZATION_LEVEL + "=?" + andNotTeamServer());

        psSUAC.setInt(1, orgId.getInt());
        psSUAC.setString(2, "%" + search + "%");
        psSUAC.setInt(3, authLevel.ordinal());

        ResultSet rs = psSUAC.executeQuery();
        try {
            return count(rs);
        } finally {
            rs.close();
        }
    }

    /**
     * @param orgId the organization being queried for shared folders
     * @param maxResults the maximum length of the returned list (for paging)
     * @param offset offset into the list of all shared folders to return from
     */
    public Collection<SID> listSharedFolders(OrganizationID orgId, int maxResults, int offset)
            throws SQLException, ExBadArgs
    {
        throwIfListingSharedFoldersNotSupported(orgId);

        PreparedStatement ps = prepareStatement(
                selectWhere(T_AC, C_AC_USER_ID + "=? limit ? offset ?", C_AC_STORE_ID));

        ps.setString(1, orgId.toTeamServerUserID().toString());
        ps.setInt(2, maxResults);
        ps.setInt(3, offset);

        ResultSet rs = ps.executeQuery();
        try {
            Set<SID> set = Sets.newHashSet();
            while (rs.next()) {
                SID sid = new SID(rs.getBytes(1));
                // skip root stores
                if (!sid.isRoot()) Util.verify(set.add(sid));
            }
            return set;
        } finally {
            rs.close();
        }
    }

    /**
     * TODO (WW) this method is O(# shared folders). It can be improved.
     */
    public int countSharedFolders(OrganizationID orgId)
            throws ExBadArgs, SQLException
    {
        throwIfListingSharedFoldersNotSupported(orgId);

        PreparedStatement ps = prepareStatement(
                selectWhere(T_AC, C_AC_USER_ID + "=?", C_AC_STORE_ID));

        ps.setString(1, orgId.toTeamServerUserID().toString());

        ResultSet rs = ps.executeQuery();
        try {
            int count = 0;
            while (rs.next()) {
                SID sid = new SID(rs.getBytes(1));
                // skip root stores
                if (!sid.isRoot()) count++;
            }
            return count;
        } finally {
            rs.close();
        }
    }

    private void throwIfListingSharedFoldersNotSupported(OrganizationID orgId)
            throws ExBadArgs
    {
        // We use team server ids to identify shared folders belonging to an org. Since the default
        // org doesn't have team server ids, we can't support querying the default org.
        if (orgId.isDefault()) {
            throw new ExBadArgs("listing shared folders in the default org is not supported");
        }
    }
}
