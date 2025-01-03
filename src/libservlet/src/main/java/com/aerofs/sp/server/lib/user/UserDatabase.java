/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.user;

import static com.aerofs.lib.db.DBUtil.binaryCount;
import static com.aerofs.lib.db.DBUtil.selectDistinctWhere;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_SHARER;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STATE;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STORE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_OWNER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_UNLINKED;
import static com.aerofs.sp.server.lib.SPSchema.C_OI_INVITEE;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_PUBLIC_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_SIGNUP_CODE_CODE;
import static com.aerofs.sp.server.lib.SPSchema.C_SIGNUP_CODE_TO;
import static com.aerofs.sp.server.lib.SPSchema.C_SIGNUP_CODE_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ACL_EPOCH;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_AUTHORIZATION_LEVEL;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_BYTES_USED;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_CREDS;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_DEACTIVATED;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_FIRST_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_LAST_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ORG_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_PASS_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_SIGNUP_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_TWO_FACTOR_ENFORCED;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_USAGE_WARNING_SENT;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_WHITELISTED;
import static com.aerofs.sp.server.lib.SPSchema.T_AC;
import static com.aerofs.sp.server.lib.SPSchema.T_DEVICE;
import static com.aerofs.sp.server.lib.SPSchema.T_OI;
import static com.aerofs.sp.server.lib.SPSchema.T_SF;
import static com.aerofs.sp.server.lib.SPSchema.T_SIGNUP_CODE;
import static com.aerofs.sp.server.lib.SPSchema.T_USER;
import static com.aerofs.sp.server.lib.SPSchema.V_SFV;
import static com.aerofs.sp.server.lib.SPSchema.C_SFV_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_SFV_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SFV_SID;
import static com.aerofs.sp.server.lib.SPSchema.C_SFV_STATE;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.aerofs.base.Base64;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.aerofs.sp.common.SharedFolderState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * N.B. only User.java may refer to this class
 */
public class UserDatabase extends AbstractSQLDatabase
{
    private static final Logger l = Loggers.getLogger(UserDatabase.class);

    @Inject
    public UserDatabase(IDatabaseConnectionProvider<Connection> provider) 
    {
        super(provider);
    }

    /**
     * Selects next paginated list of users, using optional pagination parameters
     * @param limit the maximum page size to select
     * @param startingAfter cursor - next page begins after this userID
     * @param endingBefore cursor - next page ends before this userID
     */
    public List<UserID> listUsers(Integer limit, UserID startingAfter, UserID endingBefore)
            throws SQLException, ExInvalidID 
    {
        String orderBy = startingAfter == null && endingBefore != null ? "DESC" : "ASC";
        try (PreparedStatement ps = prepareStatement(selectNextPageOfUsers(startingAfter, endingBefore, limit, orderBy))) {
            List<UserID> userIDs = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    userIDs.add(UserID.fromInternal(rs.getString(1)));
                }
                if(orderBy.equals("DESC")) {
                    Collections.reverse(userIDs);
                }
                return userIDs;
            }
        }
    }

    private String selectNextPageOfUsers(UserID startingAfter, UserID endingBefore, Integer limit, String orderBy) 
    {
        String startingAfterClause = startingAfter != null ? " AND u_id > '" + startingAfter + "'" : "";
        String endingBeforeClause = endingBefore != null ? " AND u_id < '" + endingBefore + "'" : "";
        String limitClause = limit > 0 ? " LIMIT " + limit : "";
        return selectWhere(T_USER,
                C_USER_DEACTIVATED + "=0" + startingAfterClause + endingBeforeClause
                        + " ORDER BY u_id " + orderBy + limitClause, "u_id");
    }

    /**
     * Note: this method always creates the user as non-verified.
     *
     * @throws ExAlreadyExist if the user ID already exists
     */
    public void insertUser(UserID id, FullName fullName, byte[] shaedSP, OrganizationID orgID,
                           AuthorizationLevel level)
            throws SQLException, ExAlreadyExist 
    {
        // if the user was previously deactivated, reactivate it
        if (isDeactivated(id)) {
            reactivate(id, fullName, shaedSP, orgID, level);
            return;
        }

        // we always create a user with initial epoch + 1 to ensure that the first time
        // a device is created it gets any acl updates that were made while the user
        // didn't have an entry in the user table

        try (PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_USER, C_USER_ID, C_USER_CREDS, C_USER_FIRST_NAME,
                        C_USER_LAST_NAME, C_USER_ORG_ID, C_USER_AUTHORIZATION_LEVEL,
                        C_USER_ACL_EPOCH, C_USER_DEACTIVATED))) {

            ps.setString(1, id.getString());
            ps.setString(2, Base64.encodeBytes(shaedSP));
            ps.setString(3, fullName._first);
            ps.setString(4, fullName._last);
            ps.setInt(5, orgID.getInt());
            ps.setInt(6, level.ordinal());
            //noinspection PointlessArithmeticExpression
            ps.setInt(7, LibParam.INITIAL_ACL_EPOCH + 1);
            ps.setBoolean(8, false);

            try {
                ps.executeUpdate();
            } catch (SQLException e) {
                throwOnConstraintViolation(e, "user " + id.getString() + " already exists");
                throw e;
            }
        }
    }

    private boolean isDeactivated(UserID userId) throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=1",
                "count(*)"))) {
            ps.setString(1, userId.getString());

            try (ResultSet rs = ps.executeQuery()) {
                return binaryCount(rs);
            }
        }
    }

    private void reactivate(UserID id, FullName fullName, byte[] shaedSP, OrganizationID orgID,
                            AuthorizationLevel level) throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.updateWhere(T_USER, C_USER_ID + "=?",
                C_USER_CREDS, C_USER_FIRST_NAME, C_USER_LAST_NAME, C_USER_ORG_ID,
                C_USER_AUTHORIZATION_LEVEL, C_USER_ACL_EPOCH, C_USER_DEACTIVATED))) {

            ps.setString(8, id.getString());
            ps.setString(1, Base64.encodeBytes(shaedSP));
            ps.setString(2, fullName._first);
            ps.setString(3, fullName._last);
            ps.setInt(4, orgID.getInt());
            ps.setInt(5, level.ordinal());
            //noinspection PointlessArithmeticExpression
            ps.setInt(6, LibParam.INITIAL_ACL_EPOCH + 1);
            ps.setBoolean(7, false);

            Util.verify(ps.executeUpdate() == 1);
        }
    }

    public boolean hasUser(UserID userId) throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0", "count(*)"))) {
            ps.setString(1, userId.getString());
            try (ResultSet rs = ps.executeQuery()) {
                return DBUtil.binaryCount(rs);
            }
        }
    }

    public boolean hasUsers() throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_USER,
                C_USER_DEACTIVATED + "=0", "count(*)"));
             ResultSet rs = ps.executeQuery()) {
            return DBUtil.count(rs) > 0;
        }
    }

    public @Nonnull OrganizationID getOrganizationID(UserID userId)
            throws SQLException, ExNotFound 
    {
        try (PreparedStatement ps = queryUser(userId, C_USER_ORG_ID);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, userId);
            return new OrganizationID(rs.getInt(1));
        }
    }

    public void setOrganizationID(UserID userId, OrganizationID orgId)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(updateWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0", C_USER_ORG_ID))) {

            ps.setInt(1, orgId.getInt());
            ps.setString(2, userId.getString());
            Util.verify(ps.executeUpdate() == 1);
        }
    }

    public @Nonnull FullName getFullName(UserID userId)
            throws SQLException, ExNotFound 
    {
        try (PreparedStatement ps = queryUser(userId, C_USER_FIRST_NAME, C_USER_LAST_NAME);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, userId);
            return new FullName(rs.getString(1), rs.getString(2));
        }
    }

    public long getSignupDate(UserID userId)
            throws SQLException, ExNotFound 
    {
        try (PreparedStatement ps = queryUser(userId, C_USER_SIGNUP_TS);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, userId);
            return rs.getTimestamp(1).getTime();
        }
    }

    public @Nonnull byte[] getShaedSP(UserID userId)
            throws SQLException, ExNotFound 
    {
        try (PreparedStatement ps = queryUser(userId, C_USER_CREDS);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, userId);
            return Base64.decode(rs.getString(1));
        } catch (IOException e) {
            // Base64.decode should not throw any way.
            throw new SQLException(e);
        }
    }

    public @Nonnull AuthorizationLevel getLevel(UserID userId)
            throws SQLException, ExNotFound 
    {
        try (PreparedStatement ps = queryUser(userId, C_USER_AUTHORIZATION_LEVEL);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, userId);
            return AuthorizationLevel.fromOrdinal(rs.getInt(1));
        }
    }

    public boolean isWhitelisted(UserID userID)
            throws SQLException, ExNotFound 
    {
        try (PreparedStatement ps = queryUser(userID, C_USER_WHITELISTED);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, userID);
            return rs.getBoolean(1);
        }
    }

    public boolean getUsageWarningSent(UserID userID)
            throws SQLException, ExNotFound 
    {
        try (PreparedStatement ps = queryUser(userID, C_USER_USAGE_WARNING_SENT);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, userID);
            return rs.getBoolean(1);
        }
    }

    public void setUsageWarningSent(UserID userID, boolean usageWarningSent)
            throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(updateWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0", C_USER_USAGE_WARNING_SENT))) {

            ps.setBoolean(1, usageWarningSent);
            ps.setString(2, userID.getString());
            ps.executeUpdate();
        }
    }

    public Timestamp getPasswordCreatedTS(UserID userId)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = queryUser(userId, C_USER_PASS_TS);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, userId);
            return rs.getTimestamp(1);
        }
    }


    /**
     * @return the usage in bytes, or null if the value has never been set
     */
    public @Nullable Long getBytesUsed(UserID userID)
            throws SQLException, ExNotFound 
    {
        try (PreparedStatement ps = queryUser(userID, C_USER_BYTES_USED);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, userID);
            Long bytesUsed = rs.getLong(1);
            if (rs.wasNull()) return null;
            return bytesUsed;
        }
    }

    public void setBytesUsed(UserID userID, long bytesUsed)
            throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(
                updateWhere(T_USER, C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0",
                        C_USER_BYTES_USED))) {

            ps.setLong(1, bytesUsed);
            ps.setString(2, userID.getString());
            ps.executeUpdate();
        }
    }

    /**
     * List all devices belonging to a the provided user.
     */
    public ImmutableList<DID> getDevices(UserID userId)
            throws SQLException, ExInvalidID 
    {
        try (PreparedStatement ps = prepareStatement(
                selectWhere(T_DEVICE, C_DEVICE_OWNER_ID + "=? and " + C_DEVICE_UNLINKED + "=0",
                        C_DEVICE_ID))) {

            ps.setString(1, userId.getString());

            ImmutableList.Builder<DID> builder = ImmutableList.builder();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String didString = rs.getString(1);
                    builder.add(new DID(didString));
                }
                return builder.build();
            }
        }
    }

    private PreparedStatement queryUser(UserID userId, String... fields)
            throws SQLException 
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0", fields));
        String user_str = userId.getString();
        ps.setString(1, user_str);
        return ps;
    }

    // N.B. will return with cursor on the first element of the result set
    private void throwIfEmptyResultSet(ResultSet rs, UserID userID)
            throws SQLException, ExNotFound 
    {
        if (!rs.next()) {
            throw new ExNotFound("user " + userID + " is not found");
        }
    }

    public void setLevel(UserID userId, AuthorizationLevel authLevel)
            throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(updateWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0", C_USER_AUTHORIZATION_LEVEL))) {

            ps.setInt(1, authLevel.ordinal());
            ps.setString(2, userId.getString());
            Util.verify(ps.executeUpdate() == 1);
        }
    }

    public void setWhitelisted(UserID userID, boolean whitelisted)
            throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(updateWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0", C_USER_WHITELISTED))) {

            ps.setBoolean(1, whitelisted);
            ps.setString(2, userID.getString());
            ps.executeUpdate();
        }
    }

    public void setName(UserID userId, FullName fullName)
            throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(updateWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0", C_USER_FIRST_NAME,
                C_USER_LAST_NAME))) {

            // TODO (WW) instead of doing trim here, normalize the FullName at entry points.
            // See UserID.fromInternal/fromExternal
            ps.setString(1, fullName._first.trim());
            ps.setString(2, fullName._last.trim());
            ps.setString(3, userId.getString());
            Util.verify(ps.executeUpdate() == 1);
        }
    }

    // TODO (WW) move it to a different database class?
    public void insertSignupCode(String code, UserID to)
            throws SQLException 
    {
        insertSignupCode(code, to, System.currentTimeMillis());
    }

    // For testing only
    // TODO (WW) use DI instead
    public void insertSignupCode(String code, UserID to, long currentTime)
            throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_SIGNUP_CODE, C_SIGNUP_CODE_CODE, C_SIGNUP_CODE_TO, C_SIGNUP_CODE_TS))) {

            ps.setString(1, code);
            ps.setString(2, to.getString());
            ps.setTimestamp(3, new Timestamp(currentTime), UTC_CALENDAR);
            ps.executeUpdate();
        }
    }

    /**
     * N.B. We don't currently have an index on C_SIGNUP_CODE_TO since sign-up code deletion is not
     * expected to be a frequent operation. Add the index in the future if needed.
     */
    public void deleteAllSignUpCodes(UserID userID)
            throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.deleteWhereEquals(T_SIGNUP_CODE, C_SIGNUP_CODE_TO))) {

            ps.setString(1, userID.getString());
            ps.executeUpdate();
        }
    }

    public void deleteAllOrganizationInvitations(UserID userID)
            throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.deleteWhereEquals(T_OI, C_OI_INVITEE))) {

            ps.setString(1, userID.getString());
            ps.executeUpdate();
        }
    }

    private Collection<SID> executeGetFoldersQuery(PreparedStatement ps) throws SQLException 
    {
        try (ResultSet rs = ps.executeQuery()) {
            List<SID> sids = Lists.newArrayList();
            while (rs.next()) {
                sids.add(new SID(rs.getBytes(1)));
            }
            return sids;
        }
    }

    public Collection<SID> getJoinedFolders(UserID userId) throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(selectDistinctWhere(T_AC,
                C_AC_USER_ID + "=? and " + C_AC_STATE + "=?", C_AC_STORE_ID))) {

            ps.setString(1, userId.getString());
            ps.setInt(2, SharedFolderState.JOINED.ordinal());

            return executeGetFoldersQuery(ps);
        }

    }

    public int countJoinedSharedFolders(UserID userId)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(
                V_SFV,
                C_SFV_USER_ID +  " =? and " + C_SFV_STATE + " =? ",
                "count(*)"))) {

            ps.setString(1, userId.getString());
            ps.setInt(2, SharedFolderState.JOINED.ordinal());

            try (ResultSet rs = ps.executeQuery()) {
                return DBUtil.count(rs);
            }
        }
    }

    public int countJoinedSharedFoldersWithSearchString(UserID userId, String searchString)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(
                V_SFV,
                C_SFV_USER_ID +  " =? and " + C_SFV_STATE + " =? " +
                        andNameLikeString(searchString),
                "count(*)"))) {

            ps.setString(1, userId.getString());
            ps.setInt(2, SharedFolderState.JOINED.ordinal());

            if (searchString != null) {
                ps.setString(3, "%" + DBUtil.escapeLikeOperators(searchString) + "%");
            }

            try (ResultSet rs = ps.executeQuery()) {
                return DBUtil.count(rs);
            }
        }
    }

    /**
     * @return the shared folders for which the user has joined
     */
    public Collection<SID> getJoinedSharedFolders(UserID userId, Integer maxResults,
            Integer offset, String searchString)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectDistinctWhere(
                V_SFV,
                C_SFV_USER_ID +  " =? and " + C_SFV_STATE + " =? " +
                        andNameLikeString(searchString),
                C_SFV_SID)
                + "order by " + C_SFV_NAME + ", binary(" + C_SFV_NAME + ") asc" + limitAndOffset(maxResults, offset))) {

            int index = 1;
            ps.setString(index++, userId.getString());
            ps.setInt(index++, SharedFolderState.JOINED.ordinal());

            if (searchString != null) {
                ps.setString(index++, "%" + DBUtil.escapeLikeOperators(searchString) + "%");
            }

            if (maxResults != null && offset != null){
                ps.setInt(index++, maxResults);
                ps.setInt(index++, offset);
            }

            return executeGetFoldersQuery(ps);
        }
    }

    /**
     * @return the shared folders for which the user has left
     */
    public Collection<SID> getLeftSharedFolders(UserID userId)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectDistinctWhere(
                V_SFV,
                C_SFV_USER_ID +  " =? and " + C_SFV_STATE + " =? ",
                C_SFV_SID)
                + "order by " + C_SFV_NAME + ", binary(" + C_SFV_NAME + ") asc")) {

            ps.setString(1, userId.getString());
            ps.setInt(2, SharedFolderState.LEFT.ordinal());

            return executeGetFoldersQuery(ps);
        }
    }

    /**
     *
     * @userId the id of the user
     * @return all folders that user is a part of, including root
     */

    public Collection<SID> getAllFolders(UserID userId)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectDistinctWhere(T_AC + " join " + T_SF
                + " on " + C_AC_STORE_ID + " = " + C_SF_ID,
                C_AC_USER_ID + "=?",
                C_AC_STORE_ID)
                + " order by " + C_SF_PUBLIC_NAME + ", binary("
                + C_SF_PUBLIC_NAME + ") ASC")) {

            ps.setString(1, userId.getString());

            return executeGetFoldersQuery(ps);
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

    private String limitAndOffset(Integer maxResults, Integer offset)
    {
        if (maxResults == null || offset == null) {
            return "";
        } else {
            return  " limit ? offset ?";
        }
    }

    /**
     * Deactivate a user
     * <p>
     * NB: the system cannot currently (and maybe ever) gracefully deal with deletion for
     * user/device information. This is mostly because the tick space is append-only: new
     * ticks can be added but old ticks cannot ever be removed and some other features,
     * most notably activity log, extract user-visible info from ticks and need to associate
     * this info to user and device names.
     */
    public void deactivate(UserID userId) throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(updateWhere(T_USER, C_USER_ID + "=?",
                C_USER_DEACTIVATED))) {

            ps.setBoolean(1, true);
            ps.setString(2, userId.getString());

            Util.verify(ps.executeUpdate() == 1);
        }
    }

    public boolean getEnforceSecondFactor(UserID id)
            throws SQLException, ExNotFound 
    {
        try (PreparedStatement ps = queryUser(id, C_USER_TWO_FACTOR_ENFORCED);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, id);
            return rs.getBoolean(1);
        }
    }

    public void setEnforceSecondFactor(UserID id, boolean enabled)
            throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(updateWhere(T_USER,
                C_USER_ID + "=?", C_USER_TWO_FACTOR_ENFORCED))) {
            ps.setBoolean(1, enabled);
            ps.setString(2, id.getString());
            Util.verify(ps.executeUpdate() == 1);
        }
    }

    public static class PendingSharedFolder 
    {
        public final SID _sid;
        public final UserID _sharer;

        PendingSharedFolder(SID sid, UserID sharer) {
            _sid = sid;
            _sharer = sharer;
        }
    }

    // TODO (WW) move this method to SharedFolderDatabase?
    public Collection<PendingSharedFolder> getPendingSharedFolders(UserID userId) throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement(selectDistinctWhere(T_AC,
                C_AC_USER_ID + "=? and " + C_AC_STATE + "=?",
                C_AC_STORE_ID, C_AC_SHARER))) {

            ps.setString(1, userId.getString());
            ps.setInt(2, SharedFolderState.PENDING.ordinal());

            try (ResultSet rs = ps.executeQuery()) {
                List<PendingSharedFolder> sids = Lists.newArrayList();
                while (rs.next()) {
                    String sharer = rs.getString(2);
                    // sharer can be null when user is invited to folders as a result of being added to a group
                    // return a team server here so the inviter name will be displayed as "an organization admin"
                    if (rs.wasNull()) {
                        sharer = UserID.UNKNOWN_TEAM_SERVER.getString();
                    }

                    sids.add(new PendingSharedFolder(new SID(rs.getBytes(1)),
                            UserID.fromInternal(sharer)));
                }
                return sids;
            }
        }
    }

    public Long incrementACLEpoch(UserID user) throws SQLException 
    {
        try (PreparedStatement ps = prepareStatement("update " + T_USER +
                " set " + C_USER_ACL_EPOCH + "=" + C_USER_ACL_EPOCH + "+1" +
                " where " + C_USER_ID + "=?")) {

            ps.setString(1, user.getString());
            int rows = ps.executeUpdate();

            assert rows == 1 : user + " " + rows;

            return getACLEpoch(user);
        }
    }

    public long getACLEpoch(UserID user) throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_USER, C_USER_ID + "=?",
                C_USER_ACL_EPOCH))) {
            return queryGetACLEpoch(ps, user);
        }
    }

    private long queryGetACLEpoch(PreparedStatement ps, UserID user)
            throws SQLException 
    {
        ps.setString(1, user.getString());
        try (ResultSet rs = ps.executeQuery()) {
            Util.verify(rs.next());
            long epoch = rs.getLong(1);
            assert !rs.next();
            return epoch;
        }
    }
}
