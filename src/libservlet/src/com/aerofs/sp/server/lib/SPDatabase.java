package com.aerofs.sp.server.lib;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

import java.util.TimeZone;

import com.aerofs.lib.S;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.common.Base62CodeGenerator;
import com.aerofs.sp.common.SubscriptionCategory;

import java.util.Calendar;

import com.aerofs.lib.db.DBUtil;

import com.aerofs.lib.acl.SubjectRolePair;
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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.log4j.Logger;

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

import static com.aerofs.lib.db.DBUtil.deleteWhere;
import static com.aerofs.lib.db.DBUtil.insertOnDuplicateUpdate;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SPDatabase
        extends AbstractSQLDatabase
        implements IOrganizationDatabase, ISharedFolderDatabase, IEmailSubscriptionDatabase
{
    private final static Logger l = Util.l(SPDatabase.class);

    private static final Calendar _calendar =  Calendar.getInstance(TimeZone.getTimeZone("UTC")); // set time in UTC

    public SPDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
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
        PreparedStatement psLSF = prepareStatement(
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
        PreparedStatement psCSF = prepareStatement(
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
        PreparedStatement psGIL = prepareStatement("select " +
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
        PreparedStatement ps = prepareStatement("update " + T_USER + " set "
                + C_USER_STORELESS_INVITES_QUOTA + "=? where " + C_USER_ID + "=?");

        ps.setInt(1, quota);
        ps.setString(2, userId.toString());
        ps.executeUpdate();
    }

    public void addPasswordResetToken(UserID userId, String token)
        throws SQLException
    {
        PreparedStatement ps = prepareStatement("insert into " +
                T_PASSWORD_RESET + "(" + C_PASS_TOKEN + "," + C_PASS_USER + ") values (?,?)");

        ps.setString(1, token);
        ps.setString(2, userId.toString());
        Util.verify(ps.executeUpdate() == 1);
    }

    public UserID resolvePasswordResetToken(String token)
        throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement("select " + C_PASS_USER +
                " from " + T_PASSWORD_RESET + " where " + C_PASS_TOKEN + "=? and " + C_PASS_TS +
                " > ?");

        ps.setString(1, token);
        java.util.Date today = new java.util.Date();

        ps.setTimestamp(2,
                new Timestamp(today.getTime() - SPParam.PASSWORD_RESET_TOKEN_VALID_DURATION));
        ResultSet rs = ps.executeQuery();
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
        PreparedStatement psDPRT = prepareStatement("delete from " + T_PASSWORD_RESET +
                " where " + C_PASS_TOKEN + " = ?");

        psDPRT.setString(1, token);
        int updates = psDPRT.executeUpdate();
        Util.verify(updates == 1);
    }

    public void updateUserCredentials(UserID userId, byte[] credentials)
        throws SQLException
    {
        PreparedStatement psUUC = prepareStatement("update " + T_USER + " set " +
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
     * TODO (WW) refactor this method. move it to DeviceDatabase
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
        PreparedStatement psGDI = prepareStatement(
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

        PreparedStatement psGSUS = prepareStatement(
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

        PreparedStatement ps = prepareStatement(
                "select " + C_DEVICE_ID + ", " + C_DEVICE_OWNER_ID + " from " + T_AC +
                        " acl join " + T_DEVICE + " dev on " + C_AC_USER_ID + " = " +
                        C_DEVICE_OWNER_ID + " where " + C_AC_STORE_ID + " = ?");
        ps.setBytes(1, sid);
        ResultSet rs = ps.executeQuery();
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

        PreparedStatement psGIDSDevice = prepareStatement(
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

    public void addTargetedSignupCode(String code, UserID from, UserID to, OrgID orgId, long time)
        throws SQLException
    {
       PreparedStatement psAddTI = prepareStatement(
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
        PreparedStatement ps = prepareStatement(
                DBUtil.selectWhere(T_TI, C_TI_TO + "=?", "count(*)"));

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
        PreparedStatement ps = prepareStatement("insert into " + T_FI
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
        PreparedStatement ps = prepareStatement(
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
        PreparedStatement psGetFI = prepareStatement("select " + C_FI_SID + ", " +
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
        PreparedStatement psListPFI = prepareStatement("select " + C_FI_FROM + ", "
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
        PreparedStatement ps = prepareStatement(
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
     * Add the given sid to the shared folder table with a null name. No-op if the entry already
     * exists.
     */
    @Override
    public void addSharedFolder(SID sid)
           throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                insertOnDuplicateUpdate(T_SF, C_SF_ID + "=" + C_SF_ID, C_SF_ID));

        ps.setBytes(1, sid.getBytes());

        // Update returns 0 on duplicate key and 1 on successful insert
        Util.verify(ps.executeUpdate() < 2);
    }

    @Override
    public void setFolderName(SID sid, String folderName)
            throws SQLException
    {
        PreparedStatement psSetFolderName = prepareStatement("insert into " + T_SF +
                " (" + C_SF_ID + ", " + C_SF_NAME + ") values (?, ?) on duplicate key update " +
                C_SF_NAME + "=values(" + C_SF_NAME + ")");

        psSetFolderName.setBytes(1, sid.getBytes());
        psSetFolderName.setString(2, folderName);

        // update returns 0 when name hasn't changed, 1 for insert, and 2 for update in place
        Util.verify(psSetFolderName.executeUpdate() <= 2);
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

        PreparedStatement psGetRoles = prepareStatement("select acl_master." +
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

        PreparedStatement psGetEpoch = prepareStatement("select " + C_USER_ID + ","
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

    @Override public void createACL(UserID requester, SID sid, List<SubjectRolePair> pairs)
            throws SQLException, ExNoPerm
    {
        PreparedStatement psReplaceRole = prepareStatement("insert into " + T_AC +
                " (" + C_AC_STORE_ID + "," + C_AC_USER_ID + "," + C_AC_ROLE + ") values (?, ?, ?) "
                + "on duplicate key update " + C_AC_ROLE + "= values (" + C_AC_ROLE + ")");

        for (SubjectRolePair pair : pairs) {
            psReplaceRole.setBytes(1, sid.getBytes());
            psReplaceRole.setString(2, pair._subject.toString());
            psReplaceRole.setInt(3, pair._role.ordinal());
            psReplaceRole.addBatch();
        }

        executeBatchWarn(psReplaceRole, pairs.size(), 1); // update the roles for all users
    }

    @Override
    public void updateACL(UserID requester, SID sid, List<SubjectRolePair> pairs)
            throws SQLException, ExNoPerm
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_ROLE));

        for (SubjectRolePair pair : pairs) {
            ps.setInt(1, pair._role.ordinal());
            ps.setBytes(2, sid.getBytes());
            ps.setString(3, pair._subject.toString());
            ps.addBatch();
        }

        try {
            // throw if any query's affected rows != 1, meaning ACL entry doesn't exist
            executeBatch(ps, pairs.size(), 1); // update the roles for all users
        } catch (ExSizeMismatch e) {
            // TODO (WW) What??
            throw new ExNoPerm("not permitted to create new ACLs when updating ACLs");
        }
    }

    @Override
    public boolean hasOwner(SID sid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_ROLE + "=?", "count(*)"));

        ps.setBytes(1, sid.getBytes());
        ps.setInt(2, Role.OWNER.ordinal());

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

    /**
     * Fetch the set of users with access to a given store
     */
    @Override
    public Set<UserID> getACLUsers(SID sid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                selectWhere(T_AC, C_AC_STORE_ID + "=?", C_AC_USER_ID));

        ps.setBytes(1, sid.getBytes());

        ResultSet rs = ps.executeQuery();
        try {
            Set<UserID> subjects = Sets.newHashSet();
            while (rs.next()) subjects.add(UserID.fromInternal(rs.getString(1)));
            return subjects;
        } finally {
            rs.close();
        }
    }

    @Override
    public boolean hasACL(SID sid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC, C_AC_STORE_ID + "=?", "count(*)"));

        ps.setBytes(1, sid.getBytes());

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
    public boolean isOwner(SID sid, UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?" + " and " + C_AC_ROLE + " = ?",
                "count(*)"));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, userId.toString());
        ps.setInt(3, Role.OWNER.ordinal());

        ResultSet rs = ps.executeQuery();
        try {
            Util.verify(rs.next());
            int ownerCount = rs.getInt(1);
            assert ownerCount >= 0 && ownerCount <= 1;
            assert !rs.next();
            return ownerCount == 1;
        } finally {
            rs.close();
        }
    }

    @Override
    public void deleteACL(UserID userId, SID sid, Collection<UserID> subjects)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                deleteWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?"));

        for (UserID subject : subjects) {
            ps.setBytes(1, sid.getBytes());
            ps.setString(2, subject.toString());
            ps.addBatch();
        }

        executeBatchWarn(ps, subjects.size(), 1);
    }

    @Override
    public void deleteACL(SID sid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(DBUtil.deleteWhere(T_AC, C_AC_STORE_ID + "=?"));

        ps.setBytes(1, sid.getBytes());

        Util.verify(ps.executeUpdate() > 0);
    }

    @Override
    public Map<UserID, Long> incrementACLEpoch(Set<UserID> users)
            throws SQLException
    {
        l.info("incrementing epoch for " + users.size() + " users");

        PreparedStatement ps = prepareStatement("update " + T_USER +
                " set " + C_USER_ACL_EPOCH + "=" + C_USER_ACL_EPOCH + "+1 where " + C_USER_ID +
                "=?");

        for (UserID user : users) {
            l.info("attempt increment epoch for " + user);
            ps.setString(1, user.toString());
            ps.addBatch();
        }

        executeBatchWarn(ps, users.size(), 1);

        return getACLEpochs(users);
    }

    @Override
    public @Nullable Role getUserPermissionForStore(SID sid, UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement("select " +
                C_AC_ROLE + " from " + T_AC + " where " + C_AC_STORE_ID + "=? and " + C_AC_USER_ID +
                "=?");

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, userId.toString());
        ResultSet rs = ps.executeQuery();
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
    public Set<SubscriptionCategory> getEmailSubscriptions(String email)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
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
        PreparedStatement ps = prepareStatement(
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
        PreparedStatement ps = prepareStatement(
                       DBUtil.deleteWhereEquals(T_ES, C_ES_EMAIL,
                               C_ES_SUBSCRIPTION));

        ps.setString(1, userId.toString());
        ps.setInt(2, sc.getCategoryID());

        Util.verify(ps.executeUpdate() == 1);
    }

    @Override
    public void removeEmailSubscription(final String tokenId) throws SQLException
    {
        PreparedStatement ps = prepareStatement(
               DBUtil.deleteWhereEquals(T_ES, C_ES_TOKEN_ID));

        ps.setString(1, tokenId);
        Util.verify(ps.executeUpdate() == 1);
    }

    @Override
    public String getTokenId(final UserID userId, final SubscriptionCategory sc) throws SQLException
    {
        PreparedStatement ps = prepareStatement(
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
        PreparedStatement ps = prepareStatement(
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
        PreparedStatement ps = prepareStatement(
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
        PreparedStatement ps = prepareStatement(
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
        PreparedStatement ps = prepareStatement(
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
        PreparedStatement ps = prepareStatement(
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
