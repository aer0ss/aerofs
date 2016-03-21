/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.organization;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.aerofs.sp.authentication.AddressPattern;
import com.aerofs.sp.server.lib.organization.Organization.TwoFactorEnforcementLevel;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.*;
import java.util.Collection;
import java.util.List;

import static com.aerofs.lib.db.DBUtil.*;
import static com.aerofs.sp.server.lib.SPSchema.*;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * N.B. only Organization.java may refer to this class
 */
public class OrganizationDatabase extends AbstractSQLDatabase
{
    private AddressPattern _internalAddressPattern = new AddressPattern();

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

        try (PreparedStatement ps = prepareStatement(
                    DBUtil.insert(T_ORGANIZATION, C_O_ID))) {

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
        try (PreparedStatement ps = prepareStatement(selectWhere(T_ORGANIZATION, C_O_ID + "=?", "count(*)"))) {
            ps.setInt(1, orgID.getInt());
            try (ResultSet rs = ps.executeQuery()) {
                return binaryCount(rs);
            }
        }
    }

    public @Nonnull String getName(OrganizationID orgID)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = queryOrg(orgID, C_O_NAME);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, orgID);
            return Objects.firstNonNull(rs.getString(1), "An Awesome Team");
        }
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setName(OrganizationID orgID, String name)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(
                updateWhere(T_ORGANIZATION, C_O_ID + "=?", C_O_NAME))) {

            ps.setString(1, name);
            ps.setInt(2, orgID.getInt());

            Util.verify(ps.executeUpdate() == 1);
        }
    }

    /**
     * @throws ExNotFound if the organization doesn't exist
     */
    public String getContactPhone(final OrganizationID orgID) throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = queryOrg(orgID, C_O_CONTACT_PHONE);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, orgID);
            return Strings.nullToEmpty(rs.getString(1));
        }
    }

    public void setContactPhone(final OrganizationID orgID, final String contactPhone)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(
                updateWhere(T_ORGANIZATION, C_O_ID + "=?", C_O_CONTACT_PHONE))) {

            ps.setString(1, contactPhone);
            ps.setInt(2, orgID.getInt());

            Util.verify(ps.executeUpdate() == 1);
        }
    }

    /**
     * @return the quota in bytes, or null if no quota is enforced by that org
     */
    public @Nullable Long getQuotaPerUser(final OrganizationID orgID) throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = queryOrg(orgID, C_O_QUOTA_PER_USER)) {
            try (ResultSet rs = ps.executeQuery()) {
                throwIfEmptyResultSet(rs, orgID);
                Long quota = rs.getLong(1);
                return rs.wasNull() ? null : quota;
            }
        }
    }

    /**
     * @param quota The new quota in bytes, or null to remove the quota
     */
    public void setQuotaPerUser(final OrganizationID orgID, @Nullable Long quota)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(updateWhere(T_ORGANIZATION, C_O_ID + "=?",
                C_O_QUOTA_PER_USER))) {

            if (quota != null) ps.setLong(1, quota);
            else ps.setNull(1, Types.BIGINT);

            ps.setInt(2, orgID.getInt());

            Util.verify(ps.executeUpdate() == 1);
        }
    }

    private PreparedStatement queryOrg(OrganizationID orgID, String field)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_ORGANIZATION, C_O_ID + "=?", field));
        ps.setInt(1, orgID.getInt());
        return ps;
    }

    // N.B. will return with cursor on the first element of the result set
    private void throwIfEmptyResultSet(ResultSet rs, OrganizationID orgID)
            throws SQLException, ExNotFound
    {
        if (!rs.next()) {
            throw new ExNotFound("org " + orgID);
        }
    }

    private static String activeNonTeamServerUser()
    {
        return C_USER_DEACTIVATED + "=0 and " + C_USER_ID + " not like ':%' ";
    }

    /**
     * @param rs Result set of tuples of the form (id, first name, last name).
     * @return  List of users in the result set.
     */
    private List<UserID> usersResultSetToList(ResultSet rs)
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
     * @param searchString The search term to use in our where clause.
     * @return List of users under the organization {@code orgId} between
     * [offset, offset + maxResults].
     *
     * @see com.aerofs.sp.server.lib.group.GroupDatabase#listGroups
     */
    public List<UserID> listUsers(OrganizationID orgId, int offset, int maxResults,
            @Nullable String searchString)
            throws SQLException
    {
        String condition;
        if (searchString == null) {
            condition = DBUtil.andConditions(C_USER_ORG_ID + "=?", activeNonTeamServerUser());
        } else {
            condition = DBUtil.andConditions(C_USER_ORG_ID + "=?", activeNonTeamServerUser(), autoCompleteMatching(false));
        }
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_USER, condition, C_USER_ID)
                + " order by " + C_USER_ID + " limit ? offset ?")) {
            int index = 1;
            ps.setInt(index++, orgId.getInt());
            if (searchString != null) {
                // need to populate all the extra conditions used for searching
                index = populateAutocompleteStatement(index, ps, searchString, false);
            }
            ps.setInt(index++, maxResults);
            ps.setInt(index++, offset);

            try (ResultSet rs = ps.executeQuery()) {
                return usersResultSetToList(rs);
            }
        }
    }

    /**
     * functions similarly to listUsers, but will also check for users in the autocomplete table
     */
    public List<User.EmailAndName> searchAutocompleteUsers(OrganizationID orgID, int offset, int maxResults, String searchPrefix)
            throws SQLException
    {
        String condition = DBUtil.andConditions(C_USER_ORG_ID + "=?", activeNonTeamServerUser(), autoCompleteMatching(false));
        // so we don't have to do fullname lookups on users from the T_USER and T_ACU tables, we get all the fields here - however since the structure
        // of the tables isn't exactly the same we add the extra column of true and false to be able to differentiate the two
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_USER, condition, C_USER_ID, C_USER_FIRST_NAME, C_USER_LAST_NAME, "false") + " union " +
                DBUtil.selectWhere(T_ACU, autoCompleteMatching(true), C_ACU_EMAIL, C_ACU_FULLNAME, C_ACU_LASTNAME, "true") +
                " order by " + C_USER_ID + " limit ? offset ?")) {
            int index = 1;
            ps.setInt(index++, orgID.getInt());
            index = populateAutocompleteStatement(index, ps, searchPrefix, true);
            // second time for the additional query to autocomplete user's table
            index = populateAutocompleteStatement(index, ps, searchPrefix, true);
            ps.setInt(index++, maxResults);
            ps.setInt(index++, offset);

            try (ResultSet rs = ps.executeQuery()) {
                return autocompleteResultSetToEmailsAndNames(rs);
            }
        }
    }

    private List<User.EmailAndName> autocompleteResultSetToEmailsAndNames(ResultSet rs)
            throws SQLException
    {
        List<User.EmailAndName> results = Lists.newArrayList();
        while(rs.next()) {
            boolean isFromAutocomplete = rs.getInt(4) != 0;
            if (isFromAutocomplete) {
                results.add(new User.EmailAndName(rs.getString(1), trimLastNameFromFullName(rs.getString(2), rs.getString(3)), rs.getString(3)));
            } else {
                results.add(new User.EmailAndName(rs.getString(1), rs.getString(2), rs.getString(3)));
            }
        }
        return results;
    }

    private String trimLastNameFromFullName(String fullName, String lastName)
    {
        // trim off the last name and a space from the end of the full name
        return fullName.substring(0, fullName.length() - (lastName.length() + 1));
    }

    /**
     * we don't take or use the search term while generating the statement to mitigate chances of
     * SQL Injection. this means you'll need to call populateAutocompleteStatement if you use the
     * condition this method returns.
     *
     * @param usingExtraTable whether the query is for SP_USER table or the extra SP_AUTOCOMPLETE_USERS
     */
    private static String autoCompleteMatching(boolean usingExtraTable)
    {
        return DBUtil.orConditions(userEmailLike(usingExtraTable), userFullNameLike(usingExtraTable), userLastNameLike(usingExtraTable));
    }

    // returns the next index to populate after the autocomplete clause
    private static int populateAutocompleteStatement(int firstIndex, PreparedStatement statement,
                        String searchString, boolean prefixMatch)
            throws SQLException
    {
        searchString = DBUtil.escapeLikeOperators(searchString);

        // do not prepend the wildcard character for auto-complete, prefix matching
        searchString = (prefixMatch ? "" : "%") + searchString + "%";

        statement.setString(firstIndex++, searchString);
        statement.setString(firstIndex++, searchString);
        statement.setString(firstIndex++, searchString);
        return firstIndex;
    }

    private static String userEmailLike(boolean extraTable)
    {
        return (extraTable ? C_ACU_EMAIL : C_USER_ID) + " like ?";
    }

    private static String userFullNameLike(boolean extraTable)
    {
        if (extraTable) {
            return C_ACU_FULLNAME + " like ?";
        } else {
            // search against first name + <space> + last name
            return "CONCAT(" + C_USER_FIRST_NAME + ", ' ', " + C_USER_LAST_NAME + ") like ?";
        }
    }

    private static String userLastNameLike(boolean extraTable)
    {
        return (extraTable ? C_ACU_LASTNAME : C_USER_LAST_NAME) + " like ?";
    }

    public List<UserID> listWhitelistedUsers(OrganizationID orgId)
        throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(
                T_USER,
                C_USER_ORG_ID + "=? and " + C_USER_WHITELISTED + "=1 and " + activeNonTeamServerUser(),
                C_USER_ID))) {
            ps.setInt(1, orgId.getInt());

            try (ResultSet rs = ps.executeQuery()) {
                return usersResultSetToList(rs);
            }
        }
    }

    /**
     * @param orgId ID of the organization.
     * @return Number of users in the organization {@code orgId}.
     */
    public int countUsers(OrganizationID orgId)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_USER,
                C_USER_ORG_ID + "=? and " + activeNonTeamServerUser(), "count(*)"))) {

            ps.setInt(1, orgId.getInt());
            try (ResultSet rs = ps.executeQuery()) {
                return count(rs);
            }
        }
    }

    /**
     * @param orgId ID of the organization.
     * @param searchString search string to match user count on
     * @return Number of users in the organization {@code orgId}.
     */
    public int countUsersWithSearchString(OrganizationID orgId, String searchString)
            throws SQLException
    {
        String condition = DBUtil.andConditions(C_USER_ORG_ID + "=?", activeNonTeamServerUser(),
                autoCompleteMatching(false));

        try (PreparedStatement ps = prepareStatement(selectWhere(T_USER, condition,
                "count(*)"))) {

            int index = 1;
            ps.setInt(index++, orgId.getInt());

            if (searchString != null) {
                // need to populate all the extra conditions used for autocomplete
                populateAutocompleteStatement(index, ps, searchString, false);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return count(rs);
            }
        }
    }

    public int countInternalUsers(OrganizationID orgId)
            throws SQLException
    {
        String internalAddressPatternQuery = "";
        if (_internalAddressPattern.getPattern() != null) {
            internalAddressPatternQuery =
                    " and " + C_USER_ID + " regexp '" + _internalAddressPattern.getPattern().toString() + "'";
        }

        try (PreparedStatement ps = prepareStatement(selectWhere(T_USER,
                C_USER_ORG_ID + "=? and " + activeNonTeamServerUser() + internalAddressPatternQuery,
                "count(*)"))) {

            ps.setInt(1, orgId.getInt());

            try (ResultSet rs = ps.executeQuery()) {
                return count(rs);
            }
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
        try (PreparedStatement ps = prepareStatement("select count(*) from " +
                T_USER + " where " + C_USER_ORG_ID + "=? and " +
                C_USER_AUTHORIZATION_LEVEL + "=? and " + activeNonTeamServerUser())) {

            ps.setInt(1, orgId.getInt());
            ps.setInt(2, authlevel.ordinal());

            try (ResultSet rs = ps.executeQuery()) {
                return count(rs);
            }
        }
    }

    /**
     * @param orgId the organization being queried for shared folders
     * @param maxResults the maximum length of the returned list (for paging)
     * @param offset offset into the list of all shared folders to return from
     */
    public Collection<SID> listSharedFolders(OrganizationID orgId, int maxResults, int offset)
            throws SQLException
    {
        return listSharedFolders(orgId, maxResults, offset, null);
    }


    public Collection<SID> listSharedFolders(OrganizationID orgId, int maxResults,
            int offset, String searchString)
            throws SQLException
    {
        // Return results that are sorted:
        // 1. Alphabetical order
        // 2. Uppercase before lowercase after 1 is applied.
        try (PreparedStatement ps = prepareStatement(selectDistinctWhere(
                V_SFV,
                C_SFV_USER_ID + " =? " + andNameLikeString(searchString),
                C_SFV_SID)
                + "order by " + C_SFV_NAME + ", binary(" + C_SFV_NAME + ") ASC limit ? offset ?")) {

            int index = 1;
            ps.setString(index++, orgId.toTeamServerUserID().getString());

            if (searchString != null) {
                ps.setString(index++, "%" + DBUtil.escapeLikeOperators(searchString) + "%");
            }

            ps.setInt(index++, maxResults);
            ps.setInt(index++, offset);

            try (ResultSet rs = ps.executeQuery()) {
                List<SID> list = Lists.newArrayList();
                while (rs.next()) {
                    SID sid = new SID(rs.getBytes(1));
                    checkState(!sid.isUserRoot());
                    Util.verify(list.add(sid));
                }
                return list;
            }
        }
    }

    /**
     * TODO (WW) this method is O(# shared folders). It can be improved.
     */
    public int countSharedFolders(OrganizationID orgId)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(
                V_SFV,
                C_SFV_USER_ID +  " =? ",
                "count(*)"))) {

            ps.setString(1, orgId.toTeamServerUserID().getString());

            try (ResultSet rs = ps.executeQuery()) {
                return DBUtil.count(rs);
            }
        }
    }

    public int countSharedFoldersWithSearchString(OrganizationID orgId, String searchString)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(
                V_SFV,
                C_SFV_USER_ID +  " =? " + andNameLikeString(searchString),
                "count(*)"))) {

            ps.setString(1, orgId.toTeamServerUserID().getString());

            if (searchString != null) {
                ps.setString(2, "%" + DBUtil.escapeLikeOperators(searchString) + "%");
            }

            try (ResultSet rs = ps.executeQuery()) {
                return DBUtil.count(rs);
            }
        }
    }

    private String andNameLikeString(String searchString)
    {
        if (searchString == null) {
            return "";
        } else {
            return " and " + C_SFV_NAME + " like ?";
        }
    }

    public void setTwoFactorEnforcementLevel(OrganizationID id, TwoFactorEnforcementLevel level)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(updateWhere(T_ORGANIZATION,
                C_O_ID + "=?", C_O_TWO_FACTOR_ENFORCEMENT_LEVEL))) {

            ps.setInt(1, level.ordinal());
            ps.setInt(2, id.getInt());

            Util.verify(ps.executeUpdate() == 1);
        }
    }

    public TwoFactorEnforcementLevel getTwoFactorEnforcementLevel(OrganizationID id)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = queryOrg(id, C_O_TWO_FACTOR_ENFORCEMENT_LEVEL);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, id);
            return TwoFactorEnforcementLevel.valueOf(rs.getInt(1));
        }
    }

    public ImmutableList<OrganizationID> getOrganizationIDs()
            throws SQLException
    {
        ImmutableList.Builder<OrganizationID> builder = ImmutableList.builder();
        try (PreparedStatement ps = prepareStatement(select(T_ORGANIZATION, C_O_ID));
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                builder.add(new OrganizationID(rs.getInt(1)));
            }
            return builder.build();
        }
    }

    public UniqueID createToken()
            throws SQLException, ExAlreadyExist
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.insert(T_SAT, C_SAT_TOKEN))) {
            UniqueID token = UniqueID.generate();
            ps.setString(1, token.toStringFormal());
            ps.executeUpdate();
            return token;
        } catch (SQLException e) {
            throwOnConstraintViolation(e, "token creation collision");
            throw e;
        }
    }

    public boolean hasToken(UniqueID token)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_SAT, C_SAT_TOKEN + "=?", "count(*)")))
        {
            ps.setString(1, token.toStringFormal());
            try (ResultSet rs = ps.executeQuery()) {
                return binaryCount(rs);
            }
        }
    }

    public void deleteToken(UniqueID token)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(deleteWhere(T_SAT, C_SAT_TOKEN + "=?")))
        {
            ps.setString(1, token.toStringFormal());
            int deleted = ps.executeUpdate();
            if (deleted != 1) {
                throw new ExNotFound("no such token");
            }
        }
    }
}
