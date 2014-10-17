/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.sp.server.lib.organization.Organization.TwoFactorEnforcementLevel;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.aerofs.lib.db.DBUtil.binaryCount;
import static com.aerofs.sp.server.lib.SPSchema.C_O_QUOTA_PER_USER;
import static com.aerofs.sp.server.lib.SPSchema.C_O_TWO_FACTOR_ENFORCEMENT_LEVEL;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_DEACTIVATED;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_WHITELISTED;
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
import static com.google.common.base.Preconditions.checkState;

/**
 * N.B. only Organization.java may refer to this class
 */
public class OrganizationDatabase extends AbstractSQLDatabase
{
    @Inject
    public OrganizationDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    /**
     * @throws ExAlreadyExist if the organization ID already exists
     */
    public void insert(OrganizationID organizationId)
            throws SQLException, ExAlreadyExist
    {
        checkNotNull(organizationId, "organizationId cannot be null");

        try {
            PreparedStatement ps = prepareStatement(
                    DBUtil.insert(T_ORGANIZATION, C_O_ID));

            ps.setInt(1, organizationId.getInt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throwOnConstraintViolation(e, "organization ID already exists");
            throw e;
        }
    }

    /**
     * @return whether there is an organization with the given org id in the DB
     */
    public boolean exists(OrganizationID orgID)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_ORGANIZATION, C_O_ID + "=?", "count(*)"));
        ps.setInt(1, orgID.getInt());
        try (ResultSet rs = ps.executeQuery()) {
            return binaryCount(rs);
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
        try (ResultSet rs = queryOrg(orgID, C_O_STRIPE_CUSTOMER_ID)) {
            String id = rs.getString(1);
            return id == null ? null : StripeCustomerID.create(id);
        }
    }

    public @Nonnull String getName(OrganizationID orgID)
            throws SQLException, ExNotFound
    {
        try (ResultSet rs = queryOrg(orgID, C_O_NAME)) {
            return Objects.firstNonNull(rs.getString(1), "An Awesome Team");
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
     * @throws ExNotFound if the organization doesn't exist
     */
    public String getContactPhone(final OrganizationID orgID) throws SQLException, ExNotFound
    {
        try (ResultSet rs = queryOrg(orgID, C_O_CONTACT_PHONE)) {
            return Strings.nullToEmpty(rs.getString(1));
        }
    }

    public void setContactPhone(final OrganizationID orgID, final String contactPhone)
            throws SQLException
    {
        final PreparedStatement ps = prepareStatement(
                updateWhere(T_ORGANIZATION, C_O_ID + "=?", C_O_CONTACT_PHONE));

        ps.setString(1, contactPhone);
        ps.setInt(2, orgID.getInt());

        Util.verify(ps.executeUpdate() == 1);
    }

    public void setStripeCustomerID(final OrganizationID orgID,
            @Nullable final String stripeCustomerID)
            throws SQLException
    {
        final PreparedStatement ps = prepareStatement(
                updateWhere(T_ORGANIZATION, C_O_ID + "=?", C_O_STRIPE_CUSTOMER_ID));

        if (stripeCustomerID != null) ps.setString(1, stripeCustomerID);
        else ps.setNull(1, Types.VARCHAR);

        ps.setInt(2, orgID.getInt());

        Util.verify(ps.executeUpdate() == 1);
    }

    /**
     * @return the quota in bytes, or null if no quota is enforced by that org
     */
    public @Nullable Long getQuotaPerUser(final OrganizationID orgID) throws SQLException, ExNotFound
    {
        try (ResultSet rs = queryOrg(orgID, C_O_QUOTA_PER_USER)) {
            Long quota = rs.getLong(1);
            return rs.wasNull() ? null : quota;
        }
    }

    /**
     * @param quota The new quota in bytes, or null to remove the quota
     */
    public void setQuotaPerUser(final OrganizationID orgID, @Nullable Long quota)
            throws SQLException
    {
        final PreparedStatement ps = prepareStatement(updateWhere(T_ORGANIZATION, C_O_ID + "=?",
                C_O_QUOTA_PER_USER));

        if (quota != null) ps.setLong(1, quota);
        else ps.setNull(1, Types.BIGINT);

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

    private static String andActiveNonTeamServerUser()
    {
        return " and " + C_USER_DEACTIVATED + "=0 and " + C_USER_ID + " not like ':%' ";
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
        PreparedStatement psLU = prepareStatement(DBUtil.selectWhere(T_USER,
                C_USER_ORG_ID + "=? " + andActiveNonTeamServerUser(),
                C_USER_ID)
                + " order by " + C_USER_ID + " limit ? offset ?");

        psLU.setInt(1, orgId.getInt());
        psLU.setInt(2, maxResults);
        psLU.setInt(3, offset);

        try (ResultSet rs = psLU.executeQuery()) {
            return usersResultSet2List(rs);
        }
    }

    public List<UserID> listWhitelistedUsers(OrganizationID orgId)
        throws SQLException
    {
        PreparedStatement ps = prepareStatement(DBUtil.selectWhere(
                T_USER,
                C_USER_ORG_ID + "=? and " + C_USER_WHITELISTED + "=1" + andActiveNonTeamServerUser(),
                C_USER_ID));
        ps.setInt(1, orgId.getInt());

        try (ResultSet rs = ps.executeQuery()) {
            return usersResultSet2List(rs);
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
                C_USER_ORG_ID + "=?" + andActiveNonTeamServerUser(), "count(*)"));

        ps.setInt(1, orgId.getInt());
        try (ResultSet rs = ps.executeQuery()) {
            return count(rs);
        }
    }

    /**
     * @param authlevel Authorization level of the users.
     * @param orgId ID of the organization.
     * @return Number of users in the organization with the given authorization level.
     */
    public int countUsersAtLevel(AuthorizationLevel authlevel, OrganizationID orgId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement("select count(*) from " +
                T_USER + " where " + C_USER_ORG_ID + "=? and " +
                C_USER_AUTHORIZATION_LEVEL + "=?" + andActiveNonTeamServerUser());

        ps.setInt(1, orgId.getInt());
        ps.setInt(2, authlevel.ordinal());

        try (ResultSet rs = ps.executeQuery()) {
            return count(rs);
        }
    }

    private static String andNotUserRoot(String sidColumn)
    {
        // check the version nibble: 0 for regular store, 3 for root store, see SID.java
        return " and substr(hex(" + sidColumn + "),13,1)='0' ";
    }

    /**
     * @param orgId the organization being queried for shared folders
     * @param maxResults the maximum length of the returned list (for paging)
     * @param offset offset into the list of all shared folders to return from
     */
    public Collection<SID> listSharedFolders(OrganizationID orgId, int maxResults, int offset)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_USER_ID + "=?" + andNotUserRoot(C_AC_STORE_ID),
                C_AC_STORE_ID)
                + " limit ? offset ?");

        ps.setString(1, orgId.toTeamServerUserID().getString());
        ps.setInt(2, maxResults);
        ps.setInt(3, offset);

        try (ResultSet rs = ps.executeQuery()) {
            Set<SID> set = Sets.newHashSet();
            while (rs.next()) {
                SID sid = new SID(rs.getBytes(1));
                checkState(!sid.isUserRoot());
                Util.verify(set.add(sid));
            }
            return set;
        }
    }

    /**
     * TODO (WW) this method is O(# shared folders). It can be improved.
     */
    public int countSharedFolders(OrganizationID orgId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_USER_ID + "=?" + andNotUserRoot(C_AC_STORE_ID),
                "count(*)"));

        ps.setString(1, orgId.toTeamServerUserID().getString());

        try (ResultSet rs = ps.executeQuery()) {
            return DBUtil.count(rs);
        }
    }

    public void setTwoFactorEnforcementLevel(OrganizationID id, TwoFactorEnforcementLevel level)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_ORGANIZATION,
                C_O_ID + "=?", C_O_TWO_FACTOR_ENFORCEMENT_LEVEL));

        ps.setInt(1, level.ordinal());
        ps.setInt(2, id.getInt());

        Util.verify(ps.executeUpdate() == 1);
    }

    public TwoFactorEnforcementLevel getTwoFactorEnforcementLevel(OrganizationID id)
            throws SQLException, ExNotFound
    {
        try (ResultSet rs = queryOrg(id, C_O_TWO_FACTOR_ENFORCEMENT_LEVEL)) {
            return TwoFactorEnforcementLevel.valueOf(rs.getInt(1));
        }
    }
}
