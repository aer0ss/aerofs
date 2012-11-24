package com.aerofs.sp.server.lib;

import java.util.Arrays;
import java.util.EnumSet;

import java.util.TimeZone;

import com.aerofs.lib.S;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExDeviceNameAlreadyExist;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.common.Base62CodeGenerator;
import com.aerofs.sp.common.SubscriptionCategory;

import java.util.Calendar;

import com.aerofs.lib.db.DBUtil;

import com.aerofs.lib.C;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.Base64;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.sp.common.SubscriptionParams;
import com.aerofs.servlets.lib.db.AbstractSQLDatabase;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.sp.server.lib.organization.IOrganizationDatabase;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.IUserSearchDatabase;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;

import static com.aerofs.sp.server.lib.SPSchema.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SPDatabase
       extends AbstractSQLDatabase
        implements IOrganizationDatabase, ISharedFolderDatabase, IUserSearchDatabase,
        IEmailSubscriptionDatabase
{
    private final static Logger l = Util.l(SPDatabase.class);

    private static final Calendar _calendar =  Calendar.getInstance(TimeZone.getTimeZone("UTC")); // set time in UTC

    public SPDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    // TODO use DBCW.throwOnConstraintViolation() instead
    private static void throwOnConstraintViolation(SQLException e) throws ExAlreadyExist
    {
        if (e.getMessage().startsWith("Duplicate entry")) {
            if (e.getMessage().contains(CO_DEVICE_NAME_OWNER))
                throw new ExDeviceNameAlreadyExist();
            else
                throw new ExAlreadyExist(e);
        }
    }

    /**
     * This method returns a SQL query for getting a list of store IDs for shared folders in a
     * given organization. Store IDs are repeated as many times as they are listed in the database
     * (no 'distinct' keyword is used) to allow the number of occurrences of each store ID to be
     * counted. It first queries for ACLs referencing members of a given organization, then queries
     * for the store IDs in those ACLs in the ACL table again to get a list of store IDs with
     * the correct number of occurrences (including ACLs for shared folder members outside the given
     * organization).
     */
    private static String sidListQuery() {
        return  "select t1." + C_AC_STORE_ID + " from (" +
                    "select " + C_AC_STORE_ID + " from " + T_AC + " join " + T_USER
                    + " on " + C_AC_USER_ID + "=" + C_USER_ID + " where " +
                    C_USER_ORG_ID + "=?" +
                ") as t1 join " + T_AC + " as t2 on t1." + C_AC_STORE_ID + "=" +
                "t2." + C_AC_STORE_ID;
    }

    public @Nullable User getUserNullable(UserID userId)
            throws SQLException
    {
        PreparedStatement psGU = getConnection().prepareStatement(
                DBUtil.selectWhere(T_USER, C_USER_ID + "=?", C_USER_FIRST_NAME, C_USER_LAST_NAME,
                        C_USER_CREDS, C_USER_VERIFIED, C_USER_ORG_ID, C_USER_AUTHORIZATION_LEVEL));
        psGU.setString(1, userId.toString());
        ResultSet rs = psGU.executeQuery();
        try {
            if (rs.next()) {
                String firstName = rs.getString(1);
                String lastName = rs.getString(2);
                byte[] creds = Base64.decode(rs.getString(3));
                boolean verified = rs.getBoolean(4);
                OrgID orgId = new OrgID(rs.getInt(5));
                AuthorizationLevel level = AuthorizationLevel.fromOrdinal(rs.getInt(6));
                User u = new User(userId, firstName, lastName, creds, verified, orgId, level);
                assert !rs.next();
                return u;
            } else {
                return null;
            }
        } catch (IOException e) {
            // Base64.decode should not throw
            throw new SQLException(e);
        } finally {
            rs.close();
        }
    }

    /**
     * @param rs Result set of tuples of the form (id, first name, last name).
     * @return  List of users in the result set.
     */
    private List<UserInfo> usersResultSet2List(ResultSet rs)
            throws SQLException
    {
        List<UserInfo> users = Lists.newArrayList();
        while (rs.next()) {
            UserID id = UserID.fromInternal(rs.getString(1));
            String firstName = rs.getString(2);
            String lastName = rs.getString(3);
            UserInfo user = new UserInfo(id, firstName, lastName);
            users.add(user);
        }
        return users;
    }

    @Override
    public List<UserInfo> listUsers(OrgID orgId, int offset, int maxResults)
            throws SQLException
    {
        PreparedStatement psLU = getConnection().prepareStatement(
                "select " + C_USER_ID + "," +
                        C_USER_FIRST_NAME + "," + C_USER_LAST_NAME + " from " +
                        T_USER +
                        " where " + C_USER_ORG_ID + "=? " + " order by " +
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

    @Override
    public List<UserInfo> searchUsers(OrgID orgId, int offset, int maxResults, String search)
            throws SQLException
    {
        PreparedStatement psSLU = getConnection().prepareStatement("select " + C_USER_ID + "," +
                C_USER_FIRST_NAME + "," + C_USER_LAST_NAME + " from " + T_USER +
                " where " + C_USER_ORG_ID + "=? and " + C_USER_ID + " like ? " +
                " order by " + C_USER_ID + " limit ? offset ?");

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

    @Override
    public List<UserInfo> listUsersWithAuthorization(OrgID orgId, int offset, int maxResults,
            AuthorizationLevel authLevel)
            throws SQLException
    {
        PreparedStatement psLUA = getConnection().prepareStatement(
                "select " + C_USER_ID + ", " + C_USER_FIRST_NAME + ", " +
                C_USER_LAST_NAME + " from " + T_USER +
                " where " + C_USER_ORG_ID + "=? and " +
                C_USER_AUTHORIZATION_LEVEL + "=? " +
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

    @Override
    public List<UserInfo> searchUsersWithAuthorization(OrgID orgId, int offset,
            int maxResults, AuthorizationLevel authLevel, String search)
        throws SQLException
    {
        PreparedStatement psSUA = getConnection().prepareStatement(
                "select " + C_USER_ID + ", " + C_USER_FIRST_NAME + ", " +
                C_USER_LAST_NAME + " from " + T_USER +
                " where " + C_USER_ORG_ID + "=? and " +
                C_USER_ID + " like ? and " +
                C_USER_AUTHORIZATION_LEVEL + "=? " +
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

    private int countResultSet2Int(ResultSet rs)
        throws SQLException
    {
        Util.verify(rs.next());
        int count = rs.getInt(1);
        assert !rs.next();
        return count;
    }

    @Override
    public int listUsersCount(OrgID orgId)
        throws SQLException
    {
        PreparedStatement psLUC = getConnection().prepareStatement("select count(*) from " +
                T_USER + " where " + C_USER_ORG_ID + "=?");

        psLUC.setInt(1, orgId.getInt());
        ResultSet rs = psLUC.executeQuery();
        try {
            return countResultSet2Int(rs);
        } finally {
            rs.close();
        }
    }

    @Override
    public int searchUsersCount(OrgID orgId, String search)
        throws SQLException
    {
        PreparedStatement psSCU = getConnection().prepareStatement("select count(*) from " +
                T_USER + " where " + C_USER_ORG_ID + "=? and " + C_USER_ID + " like ?");

        psSCU.setInt(1, orgId.getInt());
        psSCU.setString(2, "%" + search + "%");

        ResultSet rs = psSCU.executeQuery();
        try {
            return countResultSet2Int(rs);
        } finally {
            rs.close();
        }
    }

    @Override
    public int listUsersWithAuthorizationCount(AuthorizationLevel authlevel, OrgID orgId)
            throws SQLException
    {
        PreparedStatement psLUAC = getConnection().prepareStatement("select count(*) from " +
                T_USER + " where " + C_USER_ORG_ID + "=? and " +
                C_USER_AUTHORIZATION_LEVEL + "=?");

        psLUAC.setInt(1, orgId.getInt());
        psLUAC.setInt(2, authlevel.ordinal());

        ResultSet rs = psLUAC.executeQuery();
        try {
            return countResultSet2Int(rs);
        } finally {
            rs.close();
        }
    }

    @Override
    public int searchUsersWithAuthorizationCount(AuthorizationLevel authLevel,
            OrgID orgId, String search)
            throws SQLException
    {
        PreparedStatement psSUAC = getConnection().prepareStatement(
                "select count(*) from " + T_USER + " where " + C_USER_ORG_ID + "=? and " +
                C_USER_ID + " like ? and " +
                C_USER_AUTHORIZATION_LEVEL + "=?"
        );

        psSUAC.setInt(1, orgId.getInt());
        psSUAC.setString(2, "%" + search + "%");
        psSUAC.setInt(3, authLevel.ordinal());

        ResultSet rs = psSUAC.executeQuery();
        try {
            return countResultSet2Int(rs);
        } finally {
            rs.close();
        }
    }

    @Override
    public List<SharedFolder> listSharedFolders(OrgID orgId, int maxResults,
            int offset)
            throws SQLException
    {
        // This massive sql statement is the result of our complicated DB schema around
        // shared folders. See sidListQuery for an explanation of the innermost query.
        // Following that query, the surrounding statement counts how many people have
        // permissions for each store id inside and discards any store ids where fewer than
        // 2 people have permissions. This statement also handles the offset into and size
        // limits needed for the entire query to return only a subset of shared folders,
        // and also fetches folder names for the given SIDs from sp_shared_folder_name.
        //
        // After this, now that we have a list of store ids for which there is more than 1
        // user (and at least 1 user from the given organization), the final query joins
        // the list of store ids with sp_acl again to get the user ids and permissions of
        // all the users with permissions for those store ids.
        //
        // Note: The addition of the 'group by' keyword here and in countSharedFolders
        // (used in part to throw out store IDs that have fewer than 2 users) causes a
        // filesort that will likely become a bottleneck in the future. See the query
        // analysis pasted on Gerrit at https://g.arrowfs.org:8443/#/c/1605 for more
        // information.
        //
        // TODO: Increase the performance of this query
        PreparedStatement psLSF = getConnection().prepareStatement(
                "select t1." + C_AC_STORE_ID + ", t1." + C_SF_NAME + ", t2." + C_AC_USER_ID
                + ", t2." + C_AC_ROLE + " from (" +
                    "select " + C_AC_STORE_ID + ", " + C_SF_NAME + ", count(*) from (" +
                        sidListQuery() +
                    ") as t1 left join " + T_SF + " on " + C_AC_STORE_ID + "=" + C_SF_ID +
                    " group by " + C_AC_STORE_ID + " having count(*) > 1 order by "
                    + C_SF_NAME + " asc, " + C_SF_ID + " asc limit ? offset ?" +
                ") as t1 join " + T_AC + " as t2 on t1." + C_AC_STORE_ID + "=t2." +
                C_AC_STORE_ID + " order by t1." + C_SF_NAME + " asc, t1." + C_AC_STORE_ID +
                " asc, t2." + C_AC_USER_ID + " asc"
        );

        psLSF.setInt(1, orgId.getInt());
        psLSF.setInt(2, maxResults);
        psLSF.setInt(3, offset);
        ResultSet rs = psLSF.executeQuery();

        List<SharedFolder> resultList = Lists.newLinkedList();
        try {
            // iterate through rows in db response, squashing rows with the same store id
            // into single PBSharedFolder objects to be returned

            SID curStoreId = null;
            SharedFolder sf = null;

            while (rs.next()) {
                SID storeId = new SID(rs.getBytes(1));
                String storeName = "(name not currently saved)";
                List<SubjectRolePair> acl = Lists.newArrayList();
                if (!storeId.equals(curStoreId)) {
                    if (curStoreId != null) {
                        resultList.add(sf);
                    }
                    curStoreId = storeId;

                    String name = rs.getString(2);
                    if (name != null) storeName = name;
                }

                UserID userId = UserID.fromInternal(rs.getString(3));
                Role role = Role.fromOrdinal(rs.getInt(4));
                acl.add(new SubjectRolePair(userId, role));

                sf = new SharedFolder(storeId, storeName, acl);
            }
            if (curStoreId != null) {
                resultList.add(sf); // add final shared folder
            }
        } finally {
            rs.close();
        }
        return resultList;
    }

    @Override
    public int countSharedFolders(OrgID orgId)
            throws SQLException
    {
        // The statement here is taken from listSharedFolders above, but the outermost
        // statement in it has been modified to return the count of shared folders instead
        // of the users of the shared folders. Please see the explanation of the sql
        // statement in listSharedFolders for more details.
        PreparedStatement psCSF = getConnection().prepareStatement(
                "select count(*) from (" +
                    "select " + C_AC_STORE_ID + ", count(*) from (" +
                        sidListQuery() +
                    ") as t1 group by " + C_AC_STORE_ID + " having count(*) > 1" +
                ") as t1"
        );

        psCSF.setInt(1, orgId.getInt());
        ResultSet rs = psCSF.executeQuery();
        try {
            Util.verify(rs.next());
            int folderCount = rs.getInt(1);
            assert !rs.next();
            return folderCount;
        } finally {
            rs.close();
        }
    }

    // Return 0 if user not found.
    public int getFolderlessInvitesQuota(UserID userId) throws SQLException
    {
        PreparedStatement psGIL = getConnection().prepareStatement("select " +
                C_USER_STORELESS_INVITES_QUOTA + " from " + T_USER + " where " + C_USER_ID +
                "=?");

        psGIL.setString(1, userId.toString());
        ResultSet rs = psGIL.executeQuery();
        try {
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                return 0;
            }
        } finally {
            rs.close();
        }
    }

    public void setFolderlessInvitesQuota(UserID userId, int quota)
            throws SQLException
    {
        PreparedStatement psSSIQ = getConnection().prepareStatement("update " + T_USER + " set "
                + C_USER_STORELESS_INVITES_QUOTA + "=? where " + C_USER_ID + "=?");

        psSSIQ.setInt(1, quota);
        psSSIQ.setString(2, userId.toString());
        psSSIQ.executeUpdate();
    }

    private int getNewUserInitialACLEpoch()
    {
        //noinspection PointlessArithmeticExpression
        return C.INITIAL_ACL_EPOCH + 1;
    }

    /**
     * Add a user row to the sp_user table
     *
     * Note: this method always creates the user as non-verified.
     *
     * @throws ExAlreadyExist if the user exists
     */
    public void addUser(User ur)
            throws SQLException, ExAlreadyExist
    {
        // We are not going to set the verified field, so make sure nobody asks us to do so
        assert !ur._isVerified;

        l.info("addUser " + ur);

        try {
            // we always create a user with initial epoch + 1 to ensure that the first time
            // a device is created it gets any acl updates that were made while the user
            // didn't have an entry in the user table

            PreparedStatement psAU = getConnection().prepareStatement(
                    DBUtil.insert(T_USER, C_USER_ID, C_USER_CREDS, C_USER_FIRST_NAME,
                            C_USER_LAST_NAME, C_USER_ORG_ID, C_USER_AUTHORIZATION_LEVEL,
                            C_USER_ACL_EPOCH));

            psAU.setString(1, ur._id.toString());
            psAU.setString(2, Base64.encodeBytes(ur._shaedSP));
            psAU.setString(3, ur._firstName);
            psAU.setString(4, ur._lastName);
            psAU.setInt(5, ur._orgID.getInt());
            psAU.setInt(6, ur._level.ordinal());
            psAU.setInt(7, getNewUserInitialACLEpoch());
            psAU.executeUpdate();
        } catch (SQLException aue) {
            throwOnConstraintViolation(aue);
        }
    }

    public void markUserVerified(UserID userId)
            throws SQLException
    {
        PreparedStatement psUVerified = getConnection().prepareStatement("update " +
                T_USER + " set " + C_USER_VERIFIED + "=true where " + C_USER_ID +"=?");

        psUVerified.setString(1, userId.toString());
        Util.verify(psUVerified.executeUpdate() == 1);

        l.info("user " + userId + " marked verified");
    }

    public void setUserName(UserID userId, String firstName, String lastName)
            throws SQLException
    {
        PreparedStatement psSUN = getConnection().prepareStatement("update " + T_USER +
                " set " + C_USER_FIRST_NAME + "=?, " + C_USER_LAST_NAME + "=? where " + C_USER_ID +
                "=?");

        psSUN.setString(1, firstName.trim());
        psSUN.setString(2, lastName.trim());
        psSUN.setString(3, userId.toString());
        Util.verify(psSUN.executeUpdate() == 1);
    }

    public void addPasswordResetToken(UserID userId, String token)
        throws SQLException
    {
        PreparedStatement psAPRT = getConnection().prepareStatement("insert into " +
                T_PASSWORD_RESET + "(" + C_PASS_TOKEN + "," + C_PASS_USER + ") values (?,?)");

        psAPRT.setString(1, token);
        psAPRT.setString(2, userId.toString());
        Util.verify(psAPRT.executeUpdate() == 1);
    }

    public UserID resolvePasswordResetToken(String token)
        throws SQLException, IOException, ExNotFound
    {
        PreparedStatement psRPRT = getConnection().prepareStatement("select " + C_PASS_USER +
                " from " + T_PASSWORD_RESET + " where " + C_PASS_TOKEN + "=? and " + C_PASS_TS +
                " > ?");

        psRPRT.setString(1, token);
        java.util.Date today = new java.util.Date();

        psRPRT.setTimestamp(2,
                new Timestamp(today.getTime() - SPParam.PASSWORD_RESET_TOKEN_VALID_DURATION));
        ResultSet rs = psRPRT.executeQuery();
        try {
            if (rs.next()) {
                UserID id = UserID.fromInternal(rs.getString(1));
                assert !rs.next();
                return id;
            } else {
                throw new ExNotFound();
            }
        } finally {
            rs.close();
        }
    }

    public void deletePasswordResetToken(String token)
        throws SQLException
    {
        PreparedStatement psDPRT = getConnection().prepareStatement("delete from " + T_PASSWORD_RESET +
                " where " + C_PASS_TOKEN + " = ?");

        psDPRT.setString(1, token);
        int updates = psDPRT.executeUpdate();
        Util.verify(updates == 1);
    }

    public void updateUserCredentials(UserID userId, byte[] credentials)
        throws SQLException
    {
        PreparedStatement psUUC = getConnection().prepareStatement("update " + T_USER + " set " +
                C_USER_CREDS + "=? where " + C_USER_ID + "=?");

        psUUC.setString(1, Base64.encodeBytes(credentials));
        psUUC.setString(2, userId.toString());
        Util.verify(psUUC.executeUpdate() == 1);
    }

    public void checkAndUpdateUserCredentials(UserID userID, byte[] oldCredentials,
            byte[] newCredentials)
            throws ExNoPerm, SQLException
    {
        PreparedStatement psTASUC = getConnection().prepareCall(
                "update " + T_USER + " set " + C_USER_CREDS +
                        "=? where " + C_USER_ID + "=? AND " + C_USER_CREDS + "=?");

        psTASUC.setString(1, Base64.encodeBytes(newCredentials));
        psTASUC.setString(2, userID.toString());
        psTASUC.setString(3, Base64.encodeBytes(oldCredentials));
        int updated = psTASUC.executeUpdate();
        if (updated == 0) {
            throw new ExNoPerm();
        }
    }

    // TODO (WW) use DeviceRow. query user names using a separate query
    public static class DeviceInfo
    {
        public UserID _ownerID;
        public String _ownerFirstName;
        public String _ownerLastName;
        public String _deviceName;
    }

    /**
     * Get the device info for a given device ID.
     *
     * Note: we're not using the getDevice() method here because it does not include the first and
     * last name. I don't want to add first and last name to that call because it will slow things
     * down and is not required in the other places that user getDevice().
     *
     * @param did the device ID we are going to search for.
     * @return the device info corresponding to the supplied device ID. If no such device exists,
     * then return null.
     */
    public @Nullable DeviceInfo getDeviceInfo(DID did) throws SQLException
    {
        // Need to join the user and the device table.
        PreparedStatement psGDI = getConnection().prepareStatement(
                "select dev." + C_DEVICE_NAME + ", dev." + C_DEVICE_OWNER_ID + ", user." +
                C_USER_FIRST_NAME + ", user." + C_USER_LAST_NAME + " from " + T_DEVICE +
                " dev join " + T_USER + " user on dev." + C_DEVICE_OWNER_ID + " = user." +
                C_USER_ID + " where dev." + C_DEVICE_ID + " = ?"
        );

        psGDI.setString(1, did.toStringFormal());

        ResultSet rs = psGDI.executeQuery();
        try {
            if (rs.next()) {
                DeviceInfo di = new DeviceInfo();
                di._deviceName = rs.getString(1);
                di._ownerID = UserID.fromInternal(rs.getString(2));
                di._ownerFirstName = rs.getString(3);
                di._ownerLastName = rs.getString(4);
                return di;
            } else {
                return null;
            }
        } finally {
            rs.close();
        }
    }

    /**
     * Get the shared users set for a given user (i.e. the set of users that the supplied user
     * shares with).
     *
     * The smallest possible set is of size 1, since we say a user always shared with themselves
     * (even if there is no explicit ACL entry, i.e. even if they do not use shared folders).
     *
     * @param userId the user whose shared user set we are going to compute.
     */
    public Set<UserID> getSharedUsersSet(UserID userId) throws SQLException
    {
        Set<UserID> result = Sets.newHashSet();

        // The user always shares with themselves.
        result.add(userId);

        PreparedStatement psGSUS = getConnection().prepareStatement(
                "select distinct t1." + C_AC_USER_ID + " from " + T_AC +
                        " t1 join " + T_AC +
                        " t2 on t1." + C_AC_STORE_ID + " = t2." + C_AC_STORE_ID +
                        " where t2." +
                        C_AC_USER_ID + " = ?");

        psGSUS.setString(1, userId.toString());

        ResultSet rs = psGSUS.executeQuery();
        try {
            while (rs.next()) {
                UserID sharedUser = UserID.fromInternal(rs.getString(1));
                result.add(sharedUser);
            }
        } finally {
            rs.close();
        }

        return result;
    }

    /**
     * TODO (WW) remove this class and use Device and User classes instead
     * A class to hold both a username and a device ID.
     */
    public static class UserDevice
    {
        public final byte[] _did;
        public final UserID _userId;

        public UserDevice(byte[] did, UserID userId)
        {
            _did = did;
            _userId = userId;
        }

        @Override
        public int hashCode()
        {
            HashCodeBuilder builder = new HashCodeBuilder();
            builder.append(Util.hexEncode(_did));
            builder.append(_userId);
            return builder.toHashCode();
        }

        @Override
        public boolean equals(Object o)
        {
            if (!(o instanceof UserDevice)) return false;
            UserDevice od = (UserDevice)o;
            return Arrays.equals(_did, od._did) && _userId.equals(od._userId);
        }
    }

    /**
     * Get the interested devices set for a given SID belonging to a specific owner (i.e. the set
     * of devices that sync with a particular shared folder).
     *
     * Note that all the devices belonging to the owner are always included in the interested
     * devices set (regardless of exclusion).
     */
    public Set<UserDevice> getInterestedDevicesSet(byte[] sid, UserID ownerId)
            throws SQLException, ExFormatError
    {
        Set<UserDevice> result = Sets.newHashSet();

        PreparedStatement psGIDSAcl = getConnection().prepareStatement(
                "select " + C_DEVICE_ID + ", " + C_DEVICE_OWNER_ID + " from " + T_AC +
                        " acl join " + T_DEVICE + " dev on " + C_AC_USER_ID + " = " +
                        C_DEVICE_OWNER_ID + " where " + C_AC_STORE_ID + " = ?");
        psGIDSAcl.setBytes(1, sid);
        ResultSet rs = psGIDSAcl.executeQuery();
        try {
            while (rs.next()) {
                // TODO (MP) yuck. why do we store did's are CHAR(32) instead of BINARY(16)?
                String did = rs.getString(1);
                UserID userId = UserID.fromInternal(rs.getString(2));
                UserDevice ud = new UserDevice(new DID(did).getBytes(), userId);

                result.add(ud);
            }
        } finally {
            rs.close();
        }

        PreparedStatement psGIDSDevice = getConnection().prepareStatement(
                "select " + C_DEVICE_ID + " from " + T_DEVICE + " where " +
                        C_DEVICE_OWNER_ID + " = ?");
        psGIDSDevice.setString(1, ownerId.toString());
        rs = psGIDSDevice.executeQuery();
        try {
            while (rs.next()) {
                // TODO (MP) ditto here.
                String did = rs.getString(1);
                UserDevice ud = new UserDevice(new DID(did).getBytes(), ownerId);

                result.add(ud);
            }
        } finally {
            rs.close();
        }

        return result;
    }

    public void setDeviceInfo(DID did, String deviceName)
            throws SQLException, ExAlreadyExist
    {
        try {
            PreparedStatement psSDI = getConnection().prepareStatement("update " + T_DEVICE +
                    " set " + C_DEVICE_NAME + "=? where " + C_DEVICE_ID + "=?");

            psSDI.setString(1, deviceName.trim());
            psSDI.setString(2, did.toStringFormal());
            Util.verify(psSDI.executeUpdate() == 1);
        } catch (SQLException e) {
            throwOnConstraintViolation(e);
            throw e;
        }
    }

    public void setAuthorizationLevel(UserID userId, AuthorizationLevel authLevel)
            throws SQLException
    {
        l.info("set auth to " + authLevel + " for " + userId);

        PreparedStatement psSAuthLevel = getConnection().prepareStatement("update " + T_USER +
                " set " + C_USER_AUTHORIZATION_LEVEL + "=? where " + C_USER_ID + "=?");

        psSAuthLevel.setInt(1, authLevel.ordinal());
        psSAuthLevel.setString(2, userId.toString());
        Util.verify(psSAuthLevel.executeUpdate() == 1);
    }

    public void addTargetedSignupCode(String code, UserID from, UserID to, OrgID orgId, long time)
        throws SQLException
    {
       PreparedStatement psAddTI = getConnection().prepareStatement(
                DBUtil.insert(T_TI, C_TI_TIC, C_TI_FROM,
                        C_TI_TO, C_TI_ORG_ID, C_TI_TS));

        psAddTI.setString(1, code);
        psAddTI.setString(2, from.toString());
        psAddTI.setString(3, to.toString());
        psAddTI.setInt(4, orgId.getInt());
        psAddTI.setTimestamp(5, new Timestamp(time), _calendar);
        psAddTI.executeUpdate();
    }

    public synchronized void addTargetedSignupCode(String code, UserID from, UserID to, OrgID orgId)
            throws SQLException
    {
        addTargetedSignupCode(code, from, to, orgId, System.currentTimeMillis());
    }

    /**
     * Check whether a user has already been invited (with a targeted signup code).
     * This is used by us to avoid spamming people when doing mass-invite
     */
    public boolean isAlreadyInvited(UserID userId)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                DBUtil.selectWhere(C_TI_TIC, C_TI_TO + "=?", "count(*)"));

        ps.setString(1, userId.toString());
        ResultSet rs = ps.executeQuery();
        try {
            Util.verify(rs.next());
            int count = rs.getInt(1);
            assert !rs.next();
            return count != 0;
        } finally {
            rs.close();
        }
    }

    @Override
    public void addShareFolderCode(String code, UserID from, UserID to, SID sid,
            String folderName)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement("insert into " + T_FI
                + " (" + C_FI_FIC + "," + C_FI_FROM + "," + C_FI_TO + "," + C_FI_SID + ","
                + C_FI_FOLDER_NAME + ") " + "values (?,?,?,?,?)");

        ps.setString(1, code);
        ps.setString(2, from.toString());
        ps.setString(3, to.toString());
        ps.setBytes(4, sid.getBytes());
        ps.setString(5, folderName);

        ps.executeUpdate();
    }

    public static class ResolveTargetedSignUpCodeResult
    {
        public UserID _userId;
        public OrgID _orgId;
    }

    /**
     * @param tsc the invitation code
     */
    public @Nonnull ResolveTargetedSignUpCodeResult getTargetedSignUp(String tsc)
        throws SQLException, ExNotFound
    {
        PreparedStatement ps = getConnection().prepareStatement(
                DBUtil.selectWhere(T_TI, C_TI_TIC + "=?", C_TI_TO, C_TI_ORG_ID));

        ps.setString(1, tsc);
        ResultSet rs = ps.executeQuery();
        try {
            if (rs.next()) {
                ResolveTargetedSignUpCodeResult result = new ResolveTargetedSignUpCodeResult();
                result._userId = UserID.fromInternal(rs.getString(1));
                result._orgId = new OrgID(rs.getInt(2));
                assert !rs.next();
                return result;
            } else {
                throw new ExNotFound(S.INVITATION_CODE_NOT_FOUND);
            }
        } finally {
            rs.close();
        }
    }

    public static class FolderInvitation
    {
        public final SID _sid;
        public final String _folderName;
        public final UserID _invitee;

        private FolderInvitation(SID sid, String folderName, UserID invitee)
        {
            _sid = sid;
            _folderName = folderName;
            _invitee = invitee;
        }
    }

    /**
     * @param code the invitation code
     * @return null if not found
     */
    public FolderInvitation getFolderInvitation(String code)
            throws SQLException
    {
        PreparedStatement psGetFI = getConnection().prepareStatement("select " + C_FI_SID + ", " +
                C_FI_FOLDER_NAME + ", " + C_FI_TO + " from " + T_FI + " where " + C_FI_FIC + "=?");

        psGetFI.setString(1, code);
        ResultSet rs = psGetFI.executeQuery();
        try {
            if (rs.next()) {
                return new FolderInvitation(new SID(rs.getBytes(1)), rs.getString(2),
                        UserID.fromInternal(rs.getString(3)));
            } else {
                return null;
            }
        } finally {
            rs.close();
        }
    }

    public List<FolderInvitation> listPendingFolderInvitations(UserID to)
            throws SQLException
    {
        PreparedStatement psListPFI = getConnection().prepareStatement("select " + C_FI_FROM + ", "
                + C_FI_FOLDER_NAME + ", " + C_FI_SID + " from " + T_FI + " where " + C_FI_TO +
                " = ? group by " + C_FI_SID);

        psListPFI.setString(1, to.toString());
        ResultSet rs = psListPFI.executeQuery();
        try {
            List<FolderInvitation> invitations = Lists.newArrayList();
            while (rs.next()) {
                invitations.add(new FolderInvitation(
                        new SID(rs.getBytes(3)),
                        rs.getString(2),
                        UserID.fromInternal(rs.getString(1))));
            }
            return invitations;
        } finally {
            rs.close();
        }
    }


    @Override
    public String getOnePendingFolderInvitationCode(UserID to)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                        DBUtil.selectWhere(T_TI, C_TI_TO + "=?",
                                C_TI_TIC));

        ps.setString(1, to.toString());
        ResultSet rs = ps.executeQuery();
        try {
            if (rs.next()) return rs.getString(1);
            else return null;
        } finally {
            rs.close();
        }
    }

    /**
     * Add the given sid to the shared folder table with a null name. Necessary to satisfy foreign
     * key constraints in createACL.
     */
    private void addSharedFolder(SID sid)
           throws SQLException
    {
        PreparedStatement psAddSharedFolder = getConnection().prepareStatement("insert into "
                + T_SF + " (" + C_SF_ID + ") values (?) on duplicate key update " + C_SF_ID  + "="
                + C_SF_ID);

        psAddSharedFolder.setBytes(1, sid.getBytes());

        // Update returns 0 on duplicate key and 1 on successful insert
        Util.verify(psAddSharedFolder.executeUpdate() < 2);
    }

    @Override
    public void setFolderName(SID sid, String folderName)
            throws SQLException
    {
        PreparedStatement psSetFolderName = getConnection().prepareStatement("insert into " + T_SF +
                " (" + C_SF_ID + ", " + C_SF_NAME + ") values (?, ?) on duplicate key update " +
                C_SF_NAME + "=values(" + C_SF_NAME + ")");

        psSetFolderName.setBytes(1, sid.getBytes());
        psSetFolderName.setString(2, folderName);

        // update returns 0 when name hasn't changed, 1 for insert, and 2 for update in place
        Util.verify(psSetFolderName.executeUpdate() <= 2);
    }

    public static class DeviceRow
    {
        final DID _did;
        final String _name;

        // User ID of the device owner.
        final UserID _ownerID;

        public DeviceRow(DID did, String name, UserID ownerID)
        {
            _did = did;
            _ownerID = ownerID;
            _name = name;
        }

        public UserID getOwnerID()
        {
            return _ownerID;
        }

        public DID getDID()
        {
            return _did;
        }

        public String getName()
        {
            return _name;
        }
    }

    /**
     * @return null if the device is not found
     */
    public @Nullable DeviceRow getDevice(DID did)
            throws SQLException
    {
        PreparedStatement psGetDeviceUser = getConnection().prepareStatement("select " +
                C_DEVICE_NAME + "," + C_DEVICE_OWNER_ID + " from " + T_DEVICE + " where " +
                C_DEVICE_ID + " = ?");

        psGetDeviceUser.setString(1, did.toStringFormal());
        ResultSet rs = psGetDeviceUser.executeQuery();
        try {
            if (rs.next()) {
                return new DeviceRow(did, rs.getString(1), UserID.fromInternal(rs.getString(2)));
            } else {
                return null;
            }
        } finally {
            rs.close();
        }
    }

    public void addDevice(DeviceRow dr)
            throws SQLException, ExAlreadyExist
    {
        try {
            PreparedStatement psAddDev = getConnection().prepareStatement("insert into " + T_DEVICE
                    + "(" + C_DEVICE_ID + "," + C_DEVICE_NAME + "," + C_DEVICE_OWNER_ID + ")" +
                    " values (?,?,?)");
            psAddDev.setString(1, dr._did.toStringFormal());
            psAddDev.setString(2, dr._name);
            psAddDev.setString(3, dr._ownerID.toString());
            psAddDev.executeUpdate();
        } catch (SQLException e) {
            throwOnConstraintViolation(e);
            throw e;
        }
    }

    /**
     * Add a certificate row to the certificate table.
     *
     * @param serial the serial number of this new certificate.
     * @param did the device which owns this certificate.
     * @param expireTs the date (in the future) at which this certificate expires.
     */
    public void addCertificate(long serial, DID did, Date expireTs)
            throws SQLException, ExAlreadyExist
    {
        try {
            PreparedStatement psAddCert = getConnection().prepareStatement("insert into " + T_CERT +
                    "(" + C_CERT_SERIAL + "," + C_CERT_DEVICE_ID + "," + C_CERT_EXPIRE_TS +
                    ") values (?,?,?)");

            psAddCert.setString(1, String.valueOf(serial));
            psAddCert.setString(2, did.toStringFormal());
            psAddCert.setTimestamp(3, new Timestamp(expireTs.getTime()));
            psAddCert.executeUpdate();
        } catch (SQLException e) {
            throwOnConstraintViolation(e);
            throw e;
        }
    }

    /**
     * Revoke the certificates belonging to a single device.
     *
     * Important note: this should be called within a transaction!
     *
     * @param did the device whose certificates we are going to revoke.
     */
    public ImmutableList<Long> revokeDeviceCertificate(final DID did)
            throws SQLException
    {
        // Find the affected serial in the certificate table.
        PreparedStatement psRevokeDeviceCertificate = getConnection().prepareStatement("select " +
                C_CERT_SERIAL + " from " + T_CERT + " where " + C_CERT_DEVICE_ID +
                " = ? and " + C_CERT_REVOKE_TS + " = 0");

        psRevokeDeviceCertificate.setString(1, did.toStringFormal());

        ResultSet rs = psRevokeDeviceCertificate.executeQuery();
        try {
            Builder<Long> builder = ImmutableList.builder();

            // Sigh... result set does not have a size member.
            int count = 0;
            while (rs.next()) {
                builder.add(rs.getLong(1));
                count++;
            }

            // Verify that indeed we only have one device cert.
            assert count == 0 || count == 1 : ("too many device certs: " + count);

            ImmutableList<Long> serials = builder.build();
            revokeCertificatesBySerials(serials);
            return serials;
        } finally {
            rs.close();
        }
    }

    /**
     * Revoke all certificates belonging to user.
     *
     * Important note: this should be called within a transaction!
     *
     * @param userId the user whose certificates we are going to revoke.
     */
    public ImmutableList<Long> revokeUserCertificates(UserID userId)
            throws SQLException
    {
        // Find all unrevoked serials for the device.
        PreparedStatement ps = getConnection().prepareStatement("select " +
                C_CERT_SERIAL + " from " + T_CERT + " " + "join " + T_DEVICE + " on " +
                T_CERT + "." + C_CERT_DEVICE_ID + " = " + T_DEVICE + "." + C_DEVICE_ID +
                " where " + T_DEVICE + "." + C_DEVICE_OWNER_ID + " = ? and " +
                C_CERT_REVOKE_TS + " = 0");

        ps.setString(1, userId.toString());

        ResultSet rs = ps.executeQuery();
        try {
            Builder<Long> builder = ImmutableList.builder();

            while (rs.next()) {
                builder.add(rs.getLong(1));
            }

            ImmutableList<Long> serials = builder.build();
            revokeCertificatesBySerials(serials);

            return serials;
        } finally {
            rs.close();
        }
    }

    private void revokeCertificatesBySerials(ImmutableList<Long> serials)
            throws SQLException
    {
        // Update the revoke timestamp in the certificate table.
        PreparedStatement psRevokeCertificatesBySerials = getConnection().prepareStatement("update "
                + T_CERT + " set " + C_CERT_REVOKE_TS + " = current_timestamp, " +
                C_CERT_EXPIRE_TS + " = " + C_CERT_EXPIRE_TS + " where " + C_CERT_REVOKE_TS +
                " = 0 and " + C_CERT_SERIAL + " = ?");

        for (Long serial : serials) {
            psRevokeCertificatesBySerials.setLong(1, serial);
            psRevokeCertificatesBySerials.addBatch();
        }

        executeBatchWarn(psRevokeCertificatesBySerials, serials.size(), 1);
    }

    /**
     * Get a a list of revoked certificate serial numbers. The returned certificates have an
     * expiry date that is in the future.
     *
     * @return list of revoked certificates.
     */
    public ImmutableList<Long> getCRL()
            throws SQLException
    {
        PreparedStatement psGetCRL = getConnection().prepareStatement("select " + C_CERT_SERIAL +
                " from " + T_CERT + " where " + C_CERT_EXPIRE_TS + " > current_timestamp and " +
                C_CERT_REVOKE_TS + " != 0");

        ResultSet rs = psGetCRL.executeQuery();
        try {
            Builder<Long> builder = ImmutableList.builder();
            while (rs.next()) {
                builder.add(rs.getLong(1));
            }
            return builder.build();
        } finally {
            rs.close();
        }
    }

    //
    //
    // AAG FIXME: IMPORTANT!!!!!
    //
    // AAG FIXME: consider refactoring ACL db calls into a separate object!!!
    //
    //

    /**
     * <strong>Call in the context of an overall transaction only!</strong>
     *
     *
     * @param userId person requesting the ACL changes
     * @param sid store to which the acl changes will be made
     * @return true if the ACL changes should be allowed (i.e. the user has permissions)
     * @throws SQLException if there is a db error
     */
    private boolean canUserModifyACL(UserID userId, SID sid)
            throws SQLException, IOException
    {
        PreparedStatement psRoleCount = getConnection().prepareStatement(
                DBUtil.selectWhere(T_AC, C_AC_STORE_ID + "=?", "count(*)"));

        psRoleCount.setBytes(1, sid.getBytes());

        ResultSet rs;
        rs = psRoleCount.executeQuery();
        try {
            Util.verify(rs.next());
            if (rs.getInt(1) == 0) {
                l.info("allow acl modification - no roles exist for s:" + sid);
                return true;
            }
        } finally {
            rs.close();
        }

        PreparedStatement psRoleCheck = getConnection().prepareStatement(
                DBUtil.selectWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?" +
                        " and " + C_AC_ROLE + " = ?", "count(*)"));

        psRoleCheck.setBytes(1, sid.getBytes());
        psRoleCheck.setString(2, userId.toString());
        psRoleCheck.setInt(3, Role.OWNER.ordinal());

        rs = psRoleCheck.executeQuery();
        try {
            Util.verify(rs.next());

            int ownerCount = rs.getInt(1);
            // cannot have multiple owner acl entries
            assert ownerCount >= 0 && ownerCount <= 1;

            if (ownerCount == 1) {
                l.info(userId + " is an owner for s:" + sid);
                return true;
            }
        } finally {
            rs.close();
        }

        // see if user is an admin and one of their organization's members is an owner
        User currentUser = getUserNullable(userId);
        assert currentUser != null;
        if (currentUser._level == AuthorizationLevel.ADMIN) {
            l.info("user is an admin, checking if folder owner(s) are part of organization");

            PreparedStatement psOwnersInOrgCount = getConnection().prepareStatement(
                    "select count(*) from " + T_AC + " join " + T_USER + " on " + C_AC_USER_ID +
                    "=" + C_USER_ID + " where " + C_AC_STORE_ID + "=? and " + C_USER_ORG_ID +
                    "=? and " + C_AC_ROLE + "=?");

            psOwnersInOrgCount.setBytes(1, sid.getBytes());
            psOwnersInOrgCount.setInt(2, currentUser._orgID.getInt());
            psOwnersInOrgCount.setInt(3, Role.OWNER.ordinal());

            rs = psOwnersInOrgCount.executeQuery();
            try {
                Util.verify(rs.next());
                int ownersInUserOrgCount = rs.getInt(1);
                l.info("there is/are " + ownersInUserOrgCount + " folder owner(s) in " + userId +
                        "'s organization");
                assert !rs.next();
                if (ownersInUserOrgCount > 0) {
                    return true;
                }
            } finally {
                rs.close();
            }
        }

        l.info(userId + " cannot modify acl for s:" + sid);

        return false; // user has no permissions
    }

    public ACLReturn getACL(long userEpoch, UserID user)
            throws SQLException
    {
        //
        // first check if the user actually needs to get the acl
        //

        // AAG IMPORTANT: both db calls _do not_ have to be part of the same transaction!

        Set<UserID> users = Sets.newHashSet();
        users.add(user);

        Map<UserID, Long> epochs = getACLEpochs(users);
        assert epochs.size() == 1 : ("too many epochs returned exp:1 act:" + epochs.size());
        assert epochs.containsKey(user) : ("did not get epoch for user:" + user);

        long serverEpoch = epochs.get(user);
        assert serverEpoch >= userEpoch :
                ("bad epoch: user:" + userEpoch + " > server:" + serverEpoch);

        if (serverEpoch == userEpoch) {
            l.info("server epoch:" + serverEpoch + " matches user epoch:" + userEpoch);
            return new ACLReturn(serverEpoch, Collections.<SID, List<SubjectRolePair>>emptyMap());
        }

        //
        // apparently the user is out of date
        //

        PreparedStatement psGetRoles = getConnection().prepareStatement("select acl_master." +
                C_AC_STORE_ID + ", acl_master." + C_AC_USER_ID + ", acl_master." +
                C_AC_ROLE + " from " + T_AC + " as acl_master inner join " + T_AC +
                " as acl_filter using (" + C_AC_STORE_ID + ") where acl_filter." +
                C_AC_USER_ID + "=?");

        psGetRoles.setString(1, user.toString());

        Map<SID, List<SubjectRolePair>> storeToPairs = Maps.newHashMap();

        ResultSet rs = psGetRoles.executeQuery();
        try {
            while (rs.next()) {
                SID sid = new SID(rs.getBytes(1));

                if (!storeToPairs.containsKey(sid)) {
                    storeToPairs.put(sid, new LinkedList<SubjectRolePair>());
                }

                UserID subject = UserID.fromInternal(rs.getString(2));
                Role role = Role.fromOrdinal(rs.getInt(3));

                storeToPairs.get(sid).add(new SubjectRolePair(subject, role));
            }
        } finally {
            rs.close();
        }

        return new ACLReturn(serverEpoch, storeToPairs);
    }

    /**
     * @param users set of users for whom you want the acl epoch number
     * @return a map of user -> epoch number
     */
    private Map<UserID, Long> getACLEpochs(Set<UserID> users)
            throws SQLException
    {
        l.info("get epoch for " + users.size() + " users");

        PreparedStatement psGetEpoch = getConnection().prepareStatement("select " + C_USER_ID + ","
                + C_USER_ACL_EPOCH + " from " + T_USER + " where " + C_USER_ID + "=?");

        Map<UserID, Long> serverEpochs = Maps.newHashMap();

        ResultSet rs;
        for (UserID user : users) {
            psGetEpoch.setString(1, user.toString());

            rs = psGetEpoch.executeQuery();
            try {
                if (rs.next()) {
                    UserID dbUser = UserID.fromInternal(rs.getString(1));
                    long dbEpoch = rs.getLong(2);

                    assert dbUser.equals(user) : ("mismatched user exp:" + user + " act:" + dbUser);

                    l.info("get epoch:" + dbEpoch + " for " + dbUser);
                    serverEpochs.put(dbUser, dbEpoch);
                    Util.verify(!rs.next());
                } else {
                    l.info("no epoch for " + user);
                }
            } finally {
                rs.close();
            }
        }

        l.info("got epoch for " + serverEpochs.size() + " users");

        return serverEpochs;
    }

    /**
     * This method checks whether the user has the right permissions needed to modify the
     * given store, and if not performs checks to detect malicious changes to permissions and
     * attempts to repair the store's permissions if needed. Updates pairs in place during the
     * repair process.
     */
    private void checkUserPermissionsAndClearACLForHijackedRootStore(UserID userId, SID sid,
            List<SubjectRolePair> pairs)
            throws SQLException, IOException, ExNoPerm
    {
        if (canUserModifyACL(userId, sid)) return;

        // apparently the user cannot modify the ACL - check if an attacker maliciously
        // overwrote their permissions and repair the store if necessary

        l.info(userId + " cannot modify acl for s:" + sid);

        if (!SID.rootSID(userId).equals(sid)) {
            throw new ExNoPerm(userId + " not owner"); // nope - just a regular store
        }

        l.info("s:" + sid + " matches " + userId + " root store - delete existing acl");

        PreparedStatement psDeleteAllRoles = getConnection().prepareStatement("delete from "
                + T_AC + " where " + C_AC_STORE_ID + "=?");

        psDeleteAllRoles.setBytes(1, sid.getBytes());

        int updatedRows = psDeleteAllRoles.executeUpdate();
        assert updatedRows > 0 : updatedRows;

        l.info("adding " + userId + " as owner of s:" + sid);

        boolean foundOwner = false;
        for (SubjectRolePair pair : pairs) {
            if (pair._subject.equals(userId) && pair._role.equals(Role.OWNER)) {
                foundOwner = true;
            }
        }

        if (!foundOwner) {
            pairs.add(new SubjectRolePair(userId, Role.OWNER));
        }
    }

    /**
     * Create ACLs for a store
     * @return new ACL epochs for each affected user id, to be published via verkehr
     */
    @Override
    public Map<UserID, Long> createACL(UserID requester, SID sid, List<SubjectRolePair> pairs)
            throws SQLException, ExNoPerm, IOException
    {
        l.info(requester + " create roles for s:" + sid);

        checkUserPermissionsAndClearACLForHijackedRootStore(requester, sid, pairs);

        addSharedFolder(sid); // to satisfy foreign key constraints add the sid before creating ACLs

        l.info(requester + " creating " + pairs.size() + " roles for s:" + sid);

        PreparedStatement psReplaceRole = getConnection().prepareStatement("insert into " + T_AC +
                " (" + C_AC_STORE_ID + "," + C_AC_USER_ID + "," + C_AC_ROLE + ") values (?, ?, ?) "
                + "on duplicate key update " + C_AC_ROLE + "= values (" + C_AC_ROLE + ")");

        for (SubjectRolePair pair : pairs) {
            psReplaceRole.setBytes(1, sid.getBytes());
            psReplaceRole.setString(2, pair._subject.toString());
            psReplaceRole.setInt(3, pair._role.ordinal());
            psReplaceRole.addBatch();
        }

        executeBatchWarn(psReplaceRole, pairs.size(), 1); // update the roles for all users

        return incrementACLEpoch(getStoreMembers(sid));
    }

    /**
     * Update ACLs for a store
     * @throws ExNoPerm if trying to add new users to the store
     * @return new ACL epochs for each affected user id, to be published via verkehr
     */
    @Override
    public Map<UserID, Long> updateACL(UserID requester, SID sid, List<SubjectRolePair> pairs)
            throws SQLException, ExNoPerm, IOException
    {
        l.info(requester + " updating " + pairs.size() + " roles for s:" + sid);

        checkUserPermissionsAndClearACLForHijackedRootStore(requester, sid, pairs);

        PreparedStatement psUpdateRole = getConnection().prepareStatement("update " + T_AC +
                " set " + C_AC_ROLE + "=? where " + C_AC_STORE_ID + "=? and " + C_AC_USER_ID +
                "=?");

        for (SubjectRolePair pair : pairs) {
            psUpdateRole.setInt(1, pair._role.ordinal());
            psUpdateRole.setBytes(2, sid.getBytes());
            psUpdateRole.setString(3, pair._subject.toString());
            psUpdateRole.addBatch();
        }

        try {
            // throw if any query's affected rows != 1, meaning ACL entry doesn't exist
            executeBatch(psUpdateRole, pairs.size(), 1); // update the roles for all users
        } catch (ExSizeMismatch e) {
            throw new ExNoPerm("not permitted to create new ACLs when updating ACLs");
        }

        if (!hasAtLeastOneOwner(sid)) throw new ExNoPerm("Cannot demote all admins");

        return incrementACLEpoch(getStoreMembers(sid));
    }

    private boolean hasAtLeastOneOwner(SID sid) throws SQLException
    {
        PreparedStatement psCheckAtLeastOneOwner = getConnection()
                .prepareStatement("select " + C_AC_USER_ID + " from " + T_AC
                        + " where " + C_AC_STORE_ID + "=? and " + C_AC_ROLE + "=?"
                        + " LIMIT 1");

        psCheckAtLeastOneOwner.setBytes(1, sid.getBytes());
        psCheckAtLeastOneOwner.setInt(2, Role.OWNER.ordinal());

        ResultSet rs = psCheckAtLeastOneOwner.executeQuery();
        try {
            return rs.next();
        } finally {
            rs.close();
        }
    }

    /**
     * Fetch the set of users with access to a given store
     */
    private Set<UserID> getStoreMembers(SID sid)
            throws SQLException
    {
        PreparedStatement psGetSubjectsForStore = getConnection().prepareStatement("select " +
                C_AC_USER_ID + " from " + T_AC + " where " + C_AC_STORE_ID + "=?");

        psGetSubjectsForStore.setBytes(1, sid.getBytes());

        Set<UserID> subjects = Sets.newHashSet();
        ResultSet rs = psGetSubjectsForStore.executeQuery();
        try {
            while (rs.next()) { subjects.add(UserID.fromInternal(rs.getString(1))); }
        } finally {
            rs.close();
        }

        return subjects;
    }

    /**
     * <strong>
     *     IMPORTANT: Must be called in the context of a transaction
     * </strong>
     */
    public Map<UserID, Long> deleteACL(UserID userId, SID sid, Set<UserID> subjects)
            throws SQLException, ExNoPerm, IOException
    {
        assert !getConnection().getAutoCommit() :
                ("auto-commit should be turned off before calling delete ACL");

        l.info(userId + " delete roles for s:" + sid);

        if (!canUserModifyACL(userId, sid)) {
            l.info(userId + " cannot modify acl for s:" + sid);

            throw new ExNoPerm(userId + " is not an owner. If " + userId + " has admin privileges" +
                    " no owner is a member of " + userId + "'s organization.");
        }

        // setup the prepared statement
        PreparedStatement psDeleteRole = getConnection().prepareStatement("delete from " + T_AC +
                " where " + C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?");

        // TODO: check that there is at least one admin left?

        // add all the users to be deleted to a batch update (for now don't worry about
        // splitting batches)

        for (UserID subject : subjects) {
            psDeleteRole.setBytes(1, sid.getBytes());
            psDeleteRole.setString(2, subject.toString());
            psDeleteRole.addBatch();
        }

        l.info(userId + " updating " + subjects.size() + " roles for s:" + sid);

        executeBatchWarn(psDeleteRole, subjects.size(), 1); // update roles for all users

        Set<UserID> affectedUsers = getStoreMembers(sid); // get the current users
        affectedUsers.add(userId); // add the caller as well
        affectedUsers.addAll(subjects); // add all the deleted guys as well

        return incrementACLEpoch(affectedUsers);
    }

    /**
     * <strong>IMPORTANT:</strong> should only be called by setACL, deleteACL
     * when called, auto-commit should be off!
     * @param users set of user_ids for all users for which we will update the epoch
     * @return a map of user -> updated epoch number
     */
    private Map<UserID, Long> incrementACLEpoch(Set<UserID> users)
            throws SQLException
    {
        l.info("incrementing epoch for " + users.size() + " users");

        PreparedStatement psUpdateACLEpoch = getConnection().prepareStatement("update " + T_USER +
                " set " + C_USER_ACL_EPOCH + "=" + C_USER_ACL_EPOCH + "+1 where " + C_USER_ID +
                "=?");

        for (UserID user : users) {
            l.info("attempt increment epoch for " + user);
            psUpdateACLEpoch.setString(1, user.toString());
            psUpdateACLEpoch.addBatch();
        }

        executeBatchWarn(psUpdateACLEpoch, users.size(), 1);

        l.info("incremented epoch");

        return getACLEpochs(users);
    }

    @Override
    public @Nullable Role getUserPermissionForStore(SID sid, UserID userId)
            throws SQLException
    {
        PreparedStatement psGetUserPermForStore = getConnection().prepareStatement("select " +
                C_AC_ROLE + " from " + T_AC + " where " + C_AC_STORE_ID + "=? and " + C_AC_USER_ID +
                "=?");

        psGetUserPermForStore.setBytes(1, sid.getBytes());
        psGetUserPermForStore.setString(2, userId.toString());
        ResultSet rs = psGetUserPermForStore.executeQuery();
        try {
            if (!rs.next()) { // there is no entry in the ACL table for this storeid/userid
                return null;
            } else {
                Role userRole = Role.fromOrdinal(rs.getInt(1));
                assert !rs.next();
                return userRole;
            }
        } finally {
            rs.close();
        }
    }

    @Override
    public void addOrganization(final Organization org)
            throws SQLException, ExAlreadyExist
    {
        try {
            PreparedStatement psAddOrg = getConnection().prepareStatement(
                    DBUtil.insert(T_ORG, C_ORG_ID, C_ORG_NAME));

            psAddOrg.setInt(1, org._id.getInt());
            psAddOrg.setString(2, org._name);
            psAddOrg.executeUpdate();
        } catch (SQLException e) {
            throwOnConstraintViolation(e);
            throw e;
        }
    }

    /**
     * @return the Organization indexed by orgId
     * @throws ExNotFound if there is no row indexed by orgId
     */
    @Override
    public Organization getOrganization(final OrgID orgID)
            throws SQLException, ExNotFound
    {
        PreparedStatement psGetOrganization = getConnection().prepareStatement(
                DBUtil.selectWhere(T_ORG, C_ORG_ID + "=?", C_ORG_NAME));

        psGetOrganization.setInt(1, orgID.getInt());
        ResultSet rs = psGetOrganization.executeQuery();
        try {
            if (rs.next()) {
                String name = rs.getString(1);
                Organization org = new Organization(orgID, name);
                assert !rs.next();
                return org;
            } else {
                throw new ExNotFound("Organization " + orgID + " does not exist.");
            }
        } finally {
            rs.close();
        }
    }

    @Override
    public void setOrganizationPreferences(final Organization org)
            throws SQLException
    {
        PreparedStatement psSetOrgPref = getConnection().prepareStatement(
                DBUtil.updateWhere(T_ORG, C_ORG_ID + "=?", C_ORG_NAME));

        psSetOrgPref.setString(1, org._name);
        psSetOrgPref.setInt(2, org._id.getInt());

        Util.verify(psSetOrgPref.executeUpdate() == 1);
    }

    @Override
    public void moveUserToOrganization(UserID userId, OrgID orgId)
            throws SQLException
    {
        PreparedStatement psMoveToOrg = getConnection().prepareStatement(
                DBUtil.updateWhere(T_USER, C_USER_ID + "=?", C_USER_ORG_ID));

        psMoveToOrg.setInt(1, orgId.getInt());
        psMoveToOrg.setString(2, userId.toString());
        Util.verify(psMoveToOrg.executeUpdate() == 1);
    }

    private class ExSizeMismatch extends Exception
    {
        private static final long serialVersionUID = -661574306785445012L;

        ExSizeMismatch(String s) { super(s); }
    }

    /**
     * Same as executeBatch but simply log a warning on size mismatches instead of throwing an
     * ExSizeMismatch exception
     */
    private void executeBatchWarn(PreparedStatement ps, int batchSize,
            int expectedRowsAffectedPerBatchEntry)
            throws SQLException
    {
        try {
            executeBatch(ps, batchSize, expectedRowsAffectedPerBatchEntry);
        } catch (ExSizeMismatch e) {
            l.warn("Batch size mismatch", e);
        }
    }

    /**
     * Execute a batch DB update and check for size mismatch in the result
     */
    private void executeBatch(PreparedStatement ps, int batchSize,
            int expectedRowsAffectedPerBatchEntry)
            throws SQLException, ExSizeMismatch
    {
        int[] batchUpdates = ps.executeBatch();
        if (batchUpdates.length != batchSize) {
            throw new ExSizeMismatch("mismatch in batch size exp:" + batchSize + " act:"
                    + batchUpdates.length);
        }

        for (int rowsPerBatchEntry : batchUpdates) {
            if (rowsPerBatchEntry != expectedRowsAffectedPerBatchEntry) {
                throw new ExSizeMismatch("unexpected number of affected rows " +
                    "exp:" + expectedRowsAffectedPerBatchEntry + " act:" + rowsPerBatchEntry);
            }
        }
    }

    @Override
    public Set<SubscriptionCategory> getEmailSubscriptions(String email)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                        DBUtil.selectWhere(T_ES, C_ES_EMAIL + "=?",
                                C_ES_SUBSCRIPTION));

        ps.setString(1, email);

        ResultSet rs = ps.executeQuery();
        EnumSet<SubscriptionCategory> subscriptions = EnumSet.noneOf(SubscriptionCategory.class);

        try {
            while (rs.next()) {
                int result = rs.getInt(1);
                SubscriptionCategory sc = SubscriptionCategory.getCategoryByID(result);
                subscriptions.add(sc);
            }
            return subscriptions;
        } finally {
            rs.close();
        }
    }

    @Override
    public String addEmailSubscription(UserID userId, SubscriptionCategory sc, long time)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                       DBUtil.insertOnDuplicateUpdate(T_ES,
                               C_ES_LAST_EMAILED + "=?", C_ES_EMAIL,
                               C_ES_TOKEN_ID, C_ES_SUBSCRIPTION,
                               C_ES_LAST_EMAILED));

        String token = Base62CodeGenerator.newRandomBase62String(SubscriptionParams.TOKEN_ID_LENGTH);
        ps.setString(1, userId.toString());
        ps.setString(2, token);
        ps.setInt(3, sc.getCategoryID());
        ps.setTimestamp(4, new Timestamp(time), _calendar);
        ps.setTimestamp(5,new Timestamp(time), _calendar);

        Util.verify(ps.executeUpdate() == 1);

        return token;
    }

    @Override
    public String addEmailSubscription(UserID userId, SubscriptionCategory sc)
            throws SQLException
    {
        return addEmailSubscription(userId, sc, System.currentTimeMillis());
    }

    @Override
    public void removeEmailSubscription(UserID userId, SubscriptionCategory sc)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                       DBUtil.deleteWhereEquals(T_ES, C_ES_EMAIL,
                               C_ES_SUBSCRIPTION));

        ps.setString(1, userId.toString());
        ps.setInt(2, sc.getCategoryID());

        Util.verify(ps.executeUpdate() == 1);
    }

    @Override
    public void removeEmailSubscription(final String tokenId) throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
               DBUtil.deleteWhereEquals(T_ES, C_ES_TOKEN_ID));

        ps.setString(1, tokenId);
        Util.verify(ps.executeUpdate() == 1);
    }

    @Override
    public String getTokenId(final UserID userId, final SubscriptionCategory sc) throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                DBUtil.selectWhere(T_ES,
                        C_ES_EMAIL + "=? and " + C_ES_SUBSCRIPTION + "=?",
                        C_ES_TOKEN_ID));

        ps.setString(1, userId.toString());
        ps.setInt(2, sc.getCategoryID());

        ResultSet rs = ps.executeQuery();
        try {
            if (rs.next()) return rs.getString(1);
            else return null;
        } finally {
            rs.close();
        }
    }

    @Override
    public String getEmail(final String tokenId)
            throws SQLException, ExNotFound {
        PreparedStatement ps = getConnection().prepareStatement(
                DBUtil.selectWhere(T_ES, C_ES_TOKEN_ID + "=?",
                        C_ES_EMAIL)
        );

        ps.setString(1, tokenId);
        ResultSet rs = ps.executeQuery();
        try {
            if (rs.next()) return rs.getString(1);
            else throw new ExNotFound();
        } finally {
            rs.close();
        }
    }
    @Override
    public boolean isSubscribed(UserID userId, SubscriptionCategory sc)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                        DBUtil.selectWhere(T_ES,
                                C_ES_EMAIL + "=? and " + C_ES_SUBSCRIPTION + "=?",
                                C_ES_EMAIL)
                                );

        ps.setString(1, userId.toString());
        ps.setInt(2, sc.getCategoryID());

        ResultSet rs = ps.executeQuery();
        try {
            return rs.next(); //true if an entry was found, false otherwise
        } finally {
            rs.close();
        }
    }

    @Override
    public synchronized void setLastEmailTime(UserID userId, SubscriptionCategory category,
            long lastEmailTime)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                       DBUtil.updateWhere(T_ES,
                               C_ES_EMAIL + "=? and " + C_ES_SUBSCRIPTION + "=?",
                               C_ES_LAST_EMAILED));

        ps.setTimestamp(1, new Timestamp(lastEmailTime), _calendar);
        ps.setString(2, userId.toString());
        ps.setInt(3, category.getCategoryID());
        Util.verify(ps.executeUpdate() == 1);
    }

    @Override
    public Set<UserID> getUsersNotSignedUpAfterXDays(final int days, final int maxUsers,
                                                     final int offset)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                        "select " + C_TI_TO +
                        " from " + T_TI +
                        " left join " + T_USER + " on " + C_USER_ID + "=" +
                                C_TI_TO +
                        " where " + C_USER_ID + " is null " +
                        " and DATEDIFF(CURRENT_DATE(),DATE(" + C_TI_TS +")) =?" +
                        " limit ? offset ?");

        ps.setInt(1, days);
        ps.setInt(2, maxUsers);
        ps.setInt(3, offset);

        ResultSet rs = ps.executeQuery();
        try {
            Set<UserID> users = Sets.newHashSetWithExpectedSize(maxUsers);
            while (rs.next()) { users.add(UserID.fromInternal(rs.getString(1))); }
            return users;
        } finally {
            rs.close();
        }
    }

    @Override
    public synchronized int getHoursSinceLastEmail(final UserID userId,
            final SubscriptionCategory category)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                       DBUtil.selectWhere(T_ES, C_ES_EMAIL + "=? and " +
                               C_ES_SUBSCRIPTION + "=?",
                               "HOUR(TIMEDIFF(CURRENT_TIMESTAMP()," + C_ES_LAST_EMAILED +
                                       "))"));

        ps.setString(1, userId.toString());
        ps.setInt(2, category.getCategoryID());

        ResultSet rs = ps.executeQuery();
        try {
            if (rs.next()) return rs.getInt(1);
            else return -1;
        } finally {
            rs.close();
        }
    }
}
