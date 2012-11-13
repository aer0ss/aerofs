package com.aerofs.sp.server.lib;

import java.util.Arrays;
import java.util.EnumSet;

import java.util.TimeZone;

import com.aerofs.lib.spsv.Base62CodeGenerator;
import com.aerofs.lib.ex.ExDeviceNameAlreadyExist;
import com.aerofs.lib.spsv.sendgrid.SubscriptionCategory;

import java.util.Calendar;

import com.aerofs.lib.db.DBUtil;

import com.aerofs.lib.C;
import com.aerofs.lib.Role;
import com.aerofs.lib.SubjectRolePair;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.Base64;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.spsv.sendgrid.SubscriptionParams;
import com.aerofs.proto.Common.PBRole;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.GetDeviceInfoReply.PBDeviceInfo;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply.PBFolderInvitation;
import com.aerofs.proto.Sp.ListSharedFoldersResponse.PBSharedFolder;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.proto.Sp.ResolveTargetedSignUpCodeReply;
import com.aerofs.servlets.lib.db.AbstractSQLDatabase;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.sp.server.lib.organization.IOrganizationDatabase;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.IUserSearchDatabase;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;

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
    private static void checkDuplicateKey(SQLException e) throws ExAlreadyExist
    {
        if (e.getMessage().startsWith("Duplicate entry")) {
            if (e.getMessage().contains(SPSchema.CO_DEVICE_NAME_OWNER))
                throw new ExDeviceNameAlreadyExist();
            else
                throw new ExAlreadyExist(e);
        }
    }

    private static void checkOrganizationKeyConstraint(SQLException e, String orgId)
            throws ExNotFound
    {
        if (e.getMessage().startsWith("Cannot add or update a child row: a foreign key" +
                " constraint fails")) {
            throw new ExNotFound("Organization " + orgId + " does not exist.");
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
        return  "select t1." + SPSchema.C_AC_STORE_ID + " from (" +
                    "select " + SPSchema.C_AC_STORE_ID + " from " + SPSchema.T_AC + " join " + SPSchema.T_USER
                    + " on " + SPSchema.C_AC_USER_ID + "=" + SPSchema.C_USER_ID + " where " +
                    SPSchema.C_USER_ORG_ID + "=?" +
                ") as t1 join " + SPSchema.T_AC + " as t2 on t1." + SPSchema.C_AC_STORE_ID + "=" +
                "t2." + SPSchema.C_AC_STORE_ID;
    }

    public @Nullable User getUser(String id)
            throws SQLException, IOException
    {
        PreparedStatement psGU = getConnection().prepareStatement("select " + SPSchema.C_USER_FIRST_NAME
                + "," + SPSchema.C_USER_LAST_NAME + "," + SPSchema.C_USER_CREDS + "," + SPSchema.C_FINALIZED + ","
                + SPSchema.C_USER_VERIFIED + "," + SPSchema.C_USER_ORG_ID + "," + SPSchema.C_USER_AUTHORIZATION_LEVEL
                + " from " + SPSchema.T_USER + " where " + SPSchema.C_USER_ID + "=?");
        psGU.setString(1, id);
        ResultSet rs = psGU.executeQuery();
        try {
            if (rs.next()) {
                String firstName = rs.getString(1);
                String lastName = rs.getString(2);
                byte[] creds = Base64.decode(rs.getString(3));
                boolean finalized = rs.getBoolean(4);
                boolean verified = rs.getBoolean(5);
                String orgId = rs.getString(6);
                AuthorizationLevel level = AuthorizationLevel.fromOrdinal(rs.getInt(7));
                User u = new User(id, firstName, lastName, creds, finalized, verified, orgId,
                        level);
                assert !rs.next();
                return u;
            } else {
                return null;
            }
        } finally {
            rs.close();
        }
    }

    /**
     * @param rs Result set of tuples of the form (id, first name, last name).
     * @return  List of users in the result set.
     */
    private List<PBUser> usersResultSet2List(ResultSet rs)
            throws SQLException
    {
        List<PBUser> users = Lists.newArrayList();
        while (rs.next()) {
            String id = rs.getString(1);
            String firstName = rs.getString(2);
            String lastName = rs.getString(3);
            PBUser user = PBUser.newBuilder()
                                 .setUserEmail(id)
                                 .setFirstName(firstName)
                                 .setLastName(lastName)
                                 .build();
            users.add(user);
        }
        return users;
    }

    @Override
    public List<PBUser> listUsers(String orgId, int offset, int maxResults)
            throws SQLException
    {
        PreparedStatement psLU = getConnection().prepareStatement("select " + SPSchema.C_USER_ID + "," +
                SPSchema.C_USER_FIRST_NAME + "," + SPSchema.C_USER_LAST_NAME + " from " + SPSchema.T_USER +
                " where " + SPSchema.C_USER_ORG_ID + "=? " + " order by " +
                SPSchema.C_USER_ID + " limit ? offset ?");

        psLU.setString(1, orgId);
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
    public List<PBUser> searchUsers(String orgId, int offset,
            int maxResults, String search)
        throws SQLException
    {
        PreparedStatement psSLU = getConnection().prepareStatement("select " + SPSchema.C_USER_ID + "," +
                SPSchema.C_USER_FIRST_NAME + "," + SPSchema.C_USER_LAST_NAME + " from " + SPSchema.T_USER +
                " where " + SPSchema.C_USER_ORG_ID + "=? and " + SPSchema.C_USER_ID + " like ? " +
                " order by " + SPSchema.C_USER_ID + " limit ? offset ?");

        psSLU.setString(1, orgId);
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
    public List<PBUser> listUsersWithAuthorization(String orgId, int offset,
            int maxResults, AuthorizationLevel authLevel)
        throws SQLException
    {
        PreparedStatement psLUA = getConnection().prepareStatement(
                "select " + SPSchema.C_USER_ID + ", " + SPSchema.C_USER_FIRST_NAME + ", " +
                SPSchema.C_USER_LAST_NAME + " from " + SPSchema.T_USER +
                " where " + SPSchema.C_USER_ORG_ID + "=? and " +
                SPSchema.C_USER_AUTHORIZATION_LEVEL + "=? " +
                "order by " + SPSchema.C_USER_ID + " limit ? offset ?"
        );

        psLUA.setString(1, orgId);
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
    public List<PBUser> searchUsersWithAuthorization(String orgId, int offset,
            int maxResults, AuthorizationLevel authLevel, String search)
        throws SQLException
    {
        PreparedStatement psSUA = getConnection().prepareStatement(
                "select " + SPSchema.C_USER_ID + ", " + SPSchema.C_USER_FIRST_NAME + ", " +
                SPSchema.C_USER_LAST_NAME + " from " + SPSchema.T_USER +
                " where " + SPSchema.C_USER_ORG_ID + "=? and " +
                SPSchema.C_USER_ID + " like ? and " +
                SPSchema.C_USER_AUTHORIZATION_LEVEL + "=? " +
                "order by " + SPSchema.C_USER_ID + " limit ? offset ?"
        );

        psSUA.setString(1, orgId);
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
    public int listUsersCount(String orgId)
        throws SQLException
    {
        PreparedStatement psLUC = getConnection().prepareStatement("select count(*) from " +
                SPSchema.T_USER + " where " + SPSchema.C_USER_ORG_ID + "=?");

        psLUC.setString(1, orgId);
        ResultSet rs = psLUC.executeQuery();
        try {
            return countResultSet2Int(rs);
        } finally {
            rs.close();
        }
    }

    @Override
    public int searchUsersCount(String orgId, String search)
        throws SQLException
    {
        PreparedStatement psSCU = getConnection().prepareStatement("select count(*) from " +
                SPSchema.T_USER + " where " + SPSchema.C_USER_ORG_ID + "=? and " + SPSchema.C_USER_ID + " like ?");

        psSCU.setString(1, orgId);
        psSCU.setString(2, "%" + search + "%");

        ResultSet rs = psSCU.executeQuery();
        try {
            return countResultSet2Int(rs);
        } finally {
            rs.close();
        }
    }

    @Override
    public int listUsersWithAuthorizationCount(AuthorizationLevel authlevel,
            String orgId)
            throws SQLException
    {
        PreparedStatement psLUAC = getConnection().prepareStatement("select count(*) from " +
                SPSchema.T_USER + " where " + SPSchema.C_USER_ORG_ID + "=? and " +
                SPSchema.C_USER_AUTHORIZATION_LEVEL + "=?");

        psLUAC.setString(1, orgId);
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
            String orgId, String search)
            throws SQLException
    {
        PreparedStatement psSUAC = getConnection().prepareStatement(
                "select count(*) from " + SPSchema.T_USER + " where " + SPSchema.C_USER_ORG_ID + "=? and " +
                SPSchema.C_USER_ID + " like ? and " +
                SPSchema.C_USER_AUTHORIZATION_LEVEL + "=?"
        );

        psSUAC.setString(1, orgId);
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
    public List<PBSharedFolder> listSharedFolders(String orgId, int maxResults,
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
                "select t1." + SPSchema.C_AC_STORE_ID + ", t1." + SPSchema.C_SF_NAME + ", t2." + SPSchema.C_AC_USER_ID
                + ", t2." + SPSchema.C_AC_ROLE + " from (" +
                    "select " + SPSchema.C_AC_STORE_ID + ", " + SPSchema.C_SF_NAME + ", count(*) from (" +
                        sidListQuery() +
                    ") as t1 left join " + SPSchema.T_SF + " on " + SPSchema.C_AC_STORE_ID + "=" + SPSchema.C_SF_ID +
                    " group by " + SPSchema.C_AC_STORE_ID + " having count(*) > 1 order by "
                    + SPSchema.C_SF_NAME + " asc, " + SPSchema.C_SF_ID + " asc limit ? offset ?" +
                ") as t1 join " + SPSchema.T_AC + " as t2 on t1." + SPSchema.C_AC_STORE_ID + "=t2." +
                SPSchema.C_AC_STORE_ID + " order by t1." + SPSchema.C_SF_NAME + " asc, t1." + SPSchema.C_AC_STORE_ID +
                " asc, t2." + SPSchema.C_AC_USER_ID + " asc"
        );

        psLSF.setString(1, orgId);
        psLSF.setInt(2, maxResults);
        psLSF.setInt(3, offset);
        ResultSet rs = psLSF.executeQuery();

        List<PBSharedFolder> resultList = Lists.newLinkedList();
        try {
            // iterate through rows in db response, squashing rows with the same store id
            // into single PBSharedFolder objects to be returned

            SID curStoreId = null;
            PBSharedFolder.Builder sfBuilder = PBSharedFolder.newBuilder();

            while (rs.next()) {
                SID storeId = new SID(rs.getBytes(1));
                if (!storeId.equals(curStoreId)) {
                    if (curStoreId != null) {
                        resultList.add(sfBuilder.build());
                        sfBuilder = PBSharedFolder.newBuilder();
                    }
                    curStoreId = storeId;
                    sfBuilder.setStoreId(ByteString.copyFrom(storeId.getBytes()));

                    String name = rs.getString(2);
                    if (name != null) {
                        sfBuilder.setName(name);
                    } else {
                        sfBuilder.setName("(name not currently saved)");
                    }
                }

                String userId = rs.getString(3);
                PBRole role = Role.fromOrdinal(rs.getInt(4)).toPB();
                PBSubjectRolePair rolePair = PBSubjectRolePair.newBuilder()
                        .setSubject(userId)
                        .setRole(role)
                        .build();

                sfBuilder.addSubjectRole(rolePair);
            }
            if (curStoreId != null) {
                resultList.add(sfBuilder.build()); // add final shared folder
            }
        } finally {
            rs.close();
        }
        return resultList;
    }

    @Override
    public int countSharedFolders(String orgId)
            throws SQLException
    {
        // The statement here is taken from listSharedFolders above, but the outermost
        // statement in it has been modified to return the count of shared folders instead
        // of the users of the shared folders. Please see the explanation of the sql
        // statement in listSharedFolders for more details.
        PreparedStatement psCSF = getConnection().prepareStatement(
                "select count(*) from (" +
                    "select " + SPSchema.C_AC_STORE_ID + ", count(*) from (" +
                        sidListQuery() +
                    ") as t1 group by " + SPSchema.C_AC_STORE_ID + " having count(*) > 1" +
                ") as t1"
        );

        psCSF.setString(1, orgId);
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
    public int getFolderlessInvitesQuota(String user) throws SQLException
    {
        PreparedStatement psGIL = getConnection().prepareStatement("select " +
                SPSchema.C_USER_STORELESS_INVITES_QUOTA + " from " + SPSchema.T_USER + " where " + SPSchema.C_USER_ID +
                "=?");

        psGIL.setString(1, user);
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

    public void setFolderlessInvitesQuota(String user, int quota)
            throws SQLException
    {
        PreparedStatement psSSIQ = getConnection().prepareStatement("update " + SPSchema.T_USER + " set "
                + SPSchema.C_USER_STORELESS_INVITES_QUOTA + "=? where " + SPSchema.C_USER_ID + "=?");

        psSSIQ.setInt(1, quota);
        psSSIQ.setString(2, user);
        psSSIQ.executeUpdate();
    }

    private int getNewUserInitialACLEpoch()
    {
        return C.INITIAL_ACL_EPOCH + 1;
    }

    /**
     * Add a user row to the sp_user table
     *
     * Note: this method always creates the user as non-verified.
     * @return true if a new user row was added, false if the user exists, but was
     * not finalized
     * @throws SQLException if there was an error executing the SQL query
     * @throws ExAlreadyExist if the user exists (i.e. a row exists in the user table with
     * the same user id) and is finalized
     */
    public boolean addUser(User ur)
            throws SQLException, ExAlreadyExist
    {
        // We are not going to set the verified field, so make sure nobody asks us to do so
        assert !ur._isVerified;

        boolean createduser = false;

        l.info("Add " + ur);

        try {
            // we always create a user with initial epoch + 1 to ensure that the first time
            // a device is created it gets any acl updates that were made while the user
            // didn't have an entry in the user table

            PreparedStatement psAU = getConnection().prepareStatement("insert into " + SPSchema.T_USER +
                    "(" + SPSchema.C_USER_ID + "," + SPSchema.C_USER_CREDS + "," + SPSchema.C_USER_FIRST_NAME + "," +
                    SPSchema.C_USER_LAST_NAME + "," + SPSchema.C_USER_ORG_ID + "," + SPSchema.C_USER_AUTHORIZATION_LEVEL +
                    "," + SPSchema.C_USER_ACL_EPOCH + ") values (?,?,?,?,?,?," +
                    getNewUserInitialACLEpoch() + ")");

            psAU.setString(1, ur._id);
            psAU.setString(2, Base64.encodeBytes(ur._shaedSP));
            psAU.setString(3, ur._firstName);
            psAU.setString(4, ur._lastName);
            psAU.setString(5, ur._orgId);
            psAU.setInt(6, ur._level.ordinal());
            psAU.executeUpdate();

            createduser = true;
        } catch (SQLException aue) {
            l.warn("addUser err:" + aue);

            try {
                l.info("addUser (1) fail - check if duplicate");

                // check if this error was called by a duplicate key
                // if it was this will throw early into the block below
                // I've opted to do this so that I can reuse this function
                // (small as it is)
                checkDuplicateKey(aue);

                l.info("addUser (1) fail - user not duplicate");

                // if it _wasn't_ a duplicate, then let's go ahead and
                // throw this exception
                throw aue;
            } catch (ExAlreadyExist existex) {
                l.info("addUser (2) fail b/c duplicate - check if finalized");

                ResultSet rscue = null;

                try {
                    PreparedStatement psCUE = getConnection().prepareStatement("select " +
                            SPSchema.C_FINALIZED + " from " + SPSchema.T_USER + " where " +
                            SPSchema.C_USER_ID + " = ?");

                    psCUE.setString(1, ur._id);

                    rscue = psCUE.executeQuery();
                    boolean hasrows = rscue.next();
                    assert hasrows : ("ExExist but select fails");

                    boolean finalized = rscue.getBoolean(1);
                    if (finalized) throw new ExAlreadyExist(ur._id + " exists and is finalized");

                    l.info("addUser (2) user not finalized - update creds");

                    setUser(new User(ur, false));
                    createduser = false;
                } finally {
                    if (rscue != null) rscue.close();
                }
            }
        }

        l.info("addUser (4) completed successfully");

        return createduser;
    }

    /**
     * Update a user row if it exists, or create one if it doesn't
     * Note: this method never updates the verified field.
     */
    public void setUser(User ur) throws SQLException
    {
        l.info("set user row for " + ur);

        // [sigh] because of the craziness in the user setup I have to do this
        // basically, I see if an epoch exists for the user. If so, then I use it when
        // replacing the row. otherwise, I set it to the default value
        //
        // FWIW, AFAICT we should only get here on setup, so we should always use the default,
        // but, JIC, I'm doing this

        Map<String, Long> oldEpoch = getACLEpochs_(Sets.newHashSet(ur._id));
        assert oldEpoch.isEmpty() || oldEpoch.size() == 1 :
                ("too many epochs (" + oldEpoch.size() + ") for " + ur._id);

        long epoch = getNewUserInitialACLEpoch();
        if (!oldEpoch.isEmpty()) {
            assert oldEpoch.containsKey(ur._id) : ("no epoch for " + ur._id);

            epoch = oldEpoch.get(ur._id);
            l.warn("replacing user row for " + ur._id + " with existing epoch:" + epoch);
        }

        l.info("using epoch " + epoch + " for " + ur._id);

        // actually replace the row

        PreparedStatement psSU = getConnection().prepareStatement("replace into " + SPSchema.T_USER +
                "(" + SPSchema.C_USER_ID + "," + SPSchema.C_USER_FIRST_NAME + "," + SPSchema.C_USER_LAST_NAME + ","
                + SPSchema.C_USER_ORG_ID + "," + SPSchema.C_USER_CREDS + ","
                + SPSchema.C_FINALIZED + "," + SPSchema.C_USER_ACL_EPOCH  + "," + SPSchema.C_USER_AUTHORIZATION_LEVEL
                + ") values (?,?,?,?,?,?,?,?)");

        psSU.setString(1, ur._id);
        psSU.setString(2, ur._firstName);
        psSU.setString(3, ur._lastName);
        psSU.setString(4, ur._orgId);
        psSU.setString(5, Base64.encodeBytes(ur._shaedSP));
        psSU.setBoolean(6, ur._isFinalized);
        psSU.setLong(7, epoch);
        psSU.setInt(8, ur._level.ordinal());
        Util.verify(psSU.executeUpdate() == 1);

        l.info("setUser completed");
    }

    public void markUserVerified(String userID)
            throws SQLException
    {
        PreparedStatement psUVerified = getConnection().prepareStatement("update " +
                SPSchema.T_USER + " set " + SPSchema.C_USER_VERIFIED + "=true where " + SPSchema.C_USER_ID +"=?");

        psUVerified.setString(1, userID);
        // TODO should this check whether the row was not found, instead of asserting?
        Util.verify(psUVerified.executeUpdate() == 1);

        l.info("user " + userID + " marked verified");
    }

    public void setUserName(String userID, String firstName, String lastName)
            throws SQLException
    {
        PreparedStatement psSUN = getConnection().prepareStatement("update " + SPSchema.T_USER +
                " set " + SPSchema.C_USER_FIRST_NAME + "=?, " + SPSchema.C_USER_LAST_NAME + "=? where " + SPSchema.C_USER_ID +
                "=?");

        psSUN.setString(1, firstName.trim());
        psSUN.setString(2, lastName.trim());
        psSUN.setString(3, userID);
        Util.verify(psSUN.executeUpdate() == 1);
    }

    public void addPasswordResetToken(String userID, String token)
        throws SQLException
    {
        PreparedStatement psAPRT = getConnection().prepareStatement("insert into " +
                SPSchema.T_PASSWORD_RESET + "(" + SPSchema.C_PASS_TOKEN + "," + SPSchema.C_PASS_USER + ") values (?,?)");

        psAPRT.setString(1, token);
        psAPRT.setString(2, userID);
        Util.verify(psAPRT.executeUpdate() == 1);
    }

    public String resolvePasswordResetToken(String token)
        throws SQLException, IOException, ExNotFound
    {
        PreparedStatement psRPRT = getConnection().prepareStatement("select " + SPSchema.C_PASS_USER +
                " from " + SPSchema.T_PASSWORD_RESET + " where " + SPSchema.C_PASS_TOKEN + "=? and " + SPSchema.C_PASS_TS +
                " > ?");

        psRPRT.setString(1, token);
        java.util.Date today = new java.util.Date();

        psRPRT.setTimestamp(2,
                new Timestamp(today.getTime() - SPParam.PASSWORD_RESET_TOKEN_VALID_DURATION));
        ResultSet rs = psRPRT.executeQuery();
        try {
            if (rs.next()) {
                String id = rs.getString(1);
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
        PreparedStatement psDPRT = getConnection().prepareStatement("delete from " + SPSchema.T_PASSWORD_RESET +
                " where " + SPSchema.C_PASS_TOKEN + " = ?");

        psDPRT.setString(1, token);
        int updates = psDPRT.executeUpdate();
        Util.verify(updates == 1);
    }

    public void updateUserCredentials(String userID, byte[] credentials)
        throws SQLException
    {
        PreparedStatement psUUC = getConnection().prepareStatement("update " + SPSchema.T_USER + " set " +
                SPSchema.C_USER_CREDS + "=? where " + SPSchema.C_USER_ID + "=?");

        psUUC.setString(1, Base64.encodeBytes(credentials));
        psUUC.setString(2, userID);
        Util.verify(psUUC.executeUpdate() == 1);
    }

    public void checkAndUpdateUserCredentials(String userID, byte[] old_credentials,
            byte[] new_credentials)
            throws ExNoPerm, SQLException
    {
        PreparedStatement psTASUC = getConnection().prepareCall("update " + SPSchema.T_USER + " set " + SPSchema.C_USER_CREDS +
                "=? where " + SPSchema.C_USER_ID + "=? AND " + SPSchema.C_USER_CREDS + "=?");

        psTASUC.setString(1, Base64.encodeBytes(new_credentials));
        psTASUC.setString(2, userID);
        psTASUC.setString(3, Base64.encodeBytes(old_credentials));
        int updated = psTASUC.executeUpdate();
        if (updated == 0) {
            throw new ExNoPerm();
        }
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
    public @Nullable PBDeviceInfo getDeviceInfo(DID did) throws SQLException
    {
        // Need to join the user and the device table.
        PreparedStatement psGDI = getConnection().prepareStatement(
                "select dev." + SPSchema.C_DEVICE_NAME + ", dev." + SPSchema.C_DEVICE_OWNER_ID + ", user." +
                SPSchema.C_USER_FIRST_NAME + ", user." + SPSchema.C_USER_LAST_NAME + " from " + SPSchema.T_DEVICE +
                " dev join " + SPSchema.T_USER + " user on dev." + SPSchema.C_DEVICE_OWNER_ID + " = user." +
                SPSchema.C_USER_ID + " where dev." + SPSchema.C_DEVICE_ID + " = ?"
        );

        psGDI.setString(1, did.toStringFormal());

        ResultSet rs = psGDI.executeQuery();
        try {
            if (rs.next()) {
                PBUser u = PBUser.newBuilder()
                        .setUserEmail(rs.getString(2))
                        .setFirstName(rs.getString(3))
                        .setLastName(rs.getString(4))
                        .build();

                return PBDeviceInfo.newBuilder()
                        .setOwner(u)
                        .setDeviceName(rs.getString(1))
                        .build();
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
     * @param user the user whose shared user set we are going to compute.
     */
    public Set<String> getSharedUsersSet(String user) throws SQLException
    {
        Set<String> result = Sets.newHashSet();

        // The user always shares with themselves.
        result.add(user);

        PreparedStatement psGSUS = getConnection().prepareStatement(
                "select distinct t1." + SPSchema.C_AC_USER_ID +  " from " + SPSchema.T_AC + " t1 join " + SPSchema.T_AC +
                " t2 on t1." + SPSchema.C_AC_STORE_ID +  " = t2." + SPSchema.C_AC_STORE_ID + " where t2." +
                SPSchema.C_AC_USER_ID + " = ?"
        );

        psGSUS.setString(1, user);

        ResultSet rs = psGSUS.executeQuery();
        try {
            while (rs.next()) {
                String sharedUser = rs.getString(1);
                result.add(sharedUser);
            }
        } finally {
            rs.close();
        }

        return result;
    }

    /**
     * A class to hold both a username and a device ID.
     */
    public static class UserDevice
    {
        private final byte[] _did;
        private final String _userId;

        public UserDevice(byte[] did, String userId)
        {
            this._did = did;
            this._userId = userId;
        }

        public byte[] getDID()
        {
            return _did;
        }

        public String getUserId()
        {
            return _userId;
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
    public Set<UserDevice> getInterestedDevicesSet(byte[] sid, String ownerId)
            throws SQLException, ExFormatError
    {
        Set<UserDevice> result = Sets.newHashSet();

        PreparedStatement psGIDSAcl = getConnection().prepareStatement(
                "select " + SPSchema.C_DEVICE_ID + ", " + SPSchema.C_DEVICE_OWNER_ID + " from " + SPSchema.T_AC +
                        " acl join " + SPSchema.T_DEVICE + " dev on " + SPSchema.C_AC_USER_ID + " = " +
                        SPSchema.C_DEVICE_OWNER_ID + " where " + SPSchema.C_AC_STORE_ID + " = ?");
        psGIDSAcl.setBytes(1, sid);
        ResultSet rs = psGIDSAcl.executeQuery();
        try {
            while (rs.next()) {
                // TODO (MP) yuck. why do we store did's are CHAR(32) instead of BINARY(16)?
                String did = rs.getString(1);
                String userId = rs.getString(2);
                UserDevice ud = new UserDevice(new DID(did).getBytes(), userId);

                result.add(ud);
            }
        } finally {
            rs.close();
        }

        PreparedStatement psGIDSDevice = getConnection().prepareStatement(
                "select " + SPSchema.C_DEVICE_ID + " from " + SPSchema.T_DEVICE + " where " +
                        SPSchema.C_DEVICE_OWNER_ID + " = ?");
        psGIDSDevice.setString(1, ownerId);
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
            PreparedStatement psSDI = getConnection().prepareStatement("update " + SPSchema.T_DEVICE +
                    " set " + SPSchema.C_DEVICE_NAME + "=? where " + SPSchema.C_DEVICE_ID + "=?");

            psSDI.setString(1, deviceName.trim());
            psSDI.setString(2, did.toStringFormal());
            Util.verify(psSDI.executeUpdate() == 1);
        } catch (SQLException e) {
            checkDuplicateKey(e);
            throw e;
        }
    }

    public void setAuthorizationLevel(String userID, AuthorizationLevel authLevel)
            throws SQLException
    {
        l.info("set auth to " + authLevel + " for " + userID);

        PreparedStatement psSAuthLevel = getConnection().prepareStatement("update " + SPSchema.T_USER +
                " set " + SPSchema.C_USER_AUTHORIZATION_LEVEL + "=? where " + SPSchema.C_USER_ID + "=?");

        psSAuthLevel.setInt(1, authLevel.ordinal());
        psSAuthLevel.setString(2, userID);
        Util.verify(psSAuthLevel.executeUpdate() == 1);
    }

    // TODO Allen: don't call other database methods here after transaction is done
    void useBatchSignUp(User user, String bsc)
        throws SQLException, ExNotFound
    {
        l.info("use bsc:" + bsc + " user:" + user);

        // NOTE: I wanted to do update & select in the same statement, but
        // this is not possible in mysql. As a result, I split it into two
        // sql calls

        PreparedStatement psMinBatchIdx = getConnection().prepareStatement(
                "select min(" + SPSchema.C_BI_IDX + ") from " +
                        SPSchema.T_BI + " where " + SPSchema.C_BI_BIC + "=? " + "and " +
                        SPSchema.C_BI_USER + " is null");

        psMinBatchIdx.setString(1, bsc);

        // find the min null-user batch idx for the given bsc

        int minIdx;
        ResultSet minIdxRs = psMinBatchIdx.executeQuery();
        try {
            Util.verify(minIdxRs.next());
            minIdx = minIdxRs.getInt(1);
            if (minIdxRs.wasNull()) {
                throw new ExNotFound("batch signup " + bsc);
            }
        } finally {
            minIdxRs.close();
        }

        // update the user name for the min batch idx we found

        PreparedStatement psUpMinBatch = getConnection().prepareStatement(
                "update " + SPSchema.T_BI + " set " + SPSchema.C_BI_USER + "=? where " +
                        SPSchema.C_BI_IDX + "=?");

        psUpMinBatch.setString(1, user._id);
        psUpMinBatch.setInt(2, minIdx);
        Util.verify(psUpMinBatch.executeUpdate() == 1);
    }

    public void addTargetedSignupCode(String code, String from, String to,
            String orgId, long time)
        throws SQLException
    {
       PreparedStatement psAddTI = getConnection().prepareStatement(
                DBUtil.insert(SPSchema.T_TI, SPSchema.C_TI_TIC, SPSchema.C_TI_FROM,
                        SPSchema.C_TI_TO, SPSchema.C_TI_ORG_ID, SPSchema.C_TI_TS));

        psAddTI.setString(1, code);
        psAddTI.setString(2, from);
        psAddTI.setString(3, to);
        psAddTI.setString(4, orgId);
        psAddTI.setTimestamp(5, new Timestamp(time), _calendar);
        psAddTI.executeUpdate();
    }

    public synchronized void addTargetedSignupCode(String code, String from, String to, String orgId)
            throws SQLException
    {
        addTargetedSignupCode(code, from, to, orgId, System.currentTimeMillis());
    }

    /**
     * Check whether a user has already been invited (with a targeted signup code).
     * This is used by us to avoid spamming people when doing mass-invite
     */
    public boolean isAlreadyInvited(String user, String orgId)
            throws SQLException
    {
        PreparedStatement psIsAlreadyInvited = getConnection().prepareStatement("select " + SPSchema.C_TI_TIC
                + " from " + SPSchema.T_TI + " where " + SPSchema.C_TI_TO + "=? and " + SPSchema.C_TI_ORG_ID + "=? " +
                "limit 1");

        psIsAlreadyInvited.setString(1, user);
        psIsAlreadyInvited.setString(2, orgId);
        ResultSet rs = psIsAlreadyInvited.executeQuery();
        try {
            return rs.next();
        } finally {
            rs.close();
        }
    }

    @Override
    public void addShareFolderCode(String code, String from, String to, SID sid,
            String folderName)
            throws SQLException
    {
        PreparedStatement psAddFI = getConnection().prepareStatement("insert into " + SPSchema.T_FI
                + " (" + SPSchema.C_FI_FIC + "," + SPSchema.C_FI_FROM + "," + SPSchema.C_FI_TO + "," + SPSchema.C_FI_SID + ","
                + SPSchema.C_FI_FOLDER_NAME + ") " + "values (?,?,?,?,?)");

        psAddFI.setString(1, code);
        psAddFI.setString(2, from);
        psAddFI.setString(3, to);
        psAddFI.setBytes(4, sid.getBytes());
        psAddFI.setString(5, folderName);

        psAddFI.executeUpdate();
    }

    /**
     * Initialize a new batch signup code
     * NOTE: DO NOT use this in the context of a greater transaction, because it uses a transaction
     * internally and transactions cannot be nested.
     *
     * @param bsc batch signup code to initialize
     * @param signUpCount number of uses the batch signup code will be valid for
     */
    public void initBatchSignUpCode(String bsc, int signUpCount)
        throws SQLException
    {
        PreparedStatement psInitBIC = getConnection().prepareStatement("insert into " + SPSchema.T_BI +
                " (" + SPSchema.C_BI_BIC + ", " + SPSchema.C_BI_USER + ") values " + "(?, ?)");

        int sqlBatchCount = 0;
        for (int i = 0; i < signUpCount; ++i) {
            if (sqlBatchCount == SPParam.SP_SQL_BATCH_IIC_BATCH_MAX) {
                psInitBIC.executeBatch();
                sqlBatchCount = 0;
            }

            psInitBIC.setString(1, bsc);
            psInitBIC.setString(2, null);
            psInitBIC.addBatch();
            sqlBatchCount++;
        }

        psInitBIC.executeBatch();
    }

    public int countUnusedBatchSignUps(String bsc)
        throws SQLException
    {
        PreparedStatement psCountUnused = getConnection().prepareStatement("select count(*) from " +
                SPSchema.T_BI + " where " + SPSchema.C_BI_BIC + "=? and " + SPSchema.C_BI_USER + " is null");

        psCountUnused.setString(1, bsc);
        ResultSet rs = psCountUnused.executeQuery();
        try {
            return rs.next() ? rs.getInt(1) : 0;
        } finally {
            rs.close();
        }
    }

    public boolean isValidBatchSignUp(String bsc)
        throws SQLException
    {
        int count = countUnusedBatchSignUps(bsc);
        return (count > 0);
    }

    /**
     * Returns the organization id associated with a batch signup, or null if the signup
     * code is invalid.
     */
    public String checkBatchSignUpAndGetOrg(String bsc)
            throws SQLException
    {
        if (isValidBatchSignUp(bsc)) {
            // TODO (GS): Currently, all batch signups codes are for the default organization
            // In the future, the org_id should be part of the batch
            return C.DEFAULT_ORGANIZATION;
        } else {
            return null;
        }
    }

    /**
     * @param tsc the invitation code
     * @return null if not found
     */
    public @Nullable ResolveTargetedSignUpCodeReply getTargetedSignUp(String tsc)
        throws SQLException
    {
        PreparedStatement psGetTI = getConnection().prepareStatement("select " + SPSchema.C_TI_TO + ", " +
                SPSchema.C_TI_ORG_ID + " from " + SPSchema.T_TI + " where " + SPSchema.C_TI_TIC + "=?");

        psGetTI.setString(1, tsc);
        ResultSet rs = psGetTI.executeQuery();
        try {
            if (rs.next()) {
                return ResolveTargetedSignUpCodeReply.newBuilder()
                        .setEmailAddress(rs.getString(1))
                        .setOrganizationId(rs.getString(2))
                        .build();

            } else {
                return null;
            }
        } finally {
            rs.close();
        }
    }

    // TODO (GS) merge this class and PBFolderInvitation
    public static class FolderInvitation
    {
        final SID _sid;
        final String _folderName;
        final String _invitee;

        private FolderInvitation(SID sid, String folderName, String invitee)
        {
            _sid = sid;
            _folderName = folderName;
            _invitee = invitee;
        }

        public SID getSid()
        {
            return _sid;
        }

        public String getFolderName()
        {
            return _folderName;
        }

        public String getInvitee()
        {
            return _invitee;
        }
    }

    /**
     * @param code the invitation code
     * @return null if not found
     */
    public FolderInvitation getFolderInvitation(String code)
            throws SQLException
    {
        PreparedStatement psGetFI = getConnection().prepareStatement("select " + SPSchema.C_FI_SID + ", " +
                SPSchema.C_FI_FOLDER_NAME + ", " + SPSchema.C_FI_TO + " from " + SPSchema.T_FI + " where " + SPSchema.C_FI_FIC + "=?");

        psGetFI.setString(1, code);
        ResultSet rs = psGetFI.executeQuery();
        try {
            if (rs.next()) {
                return new FolderInvitation(new SID(rs.getBytes(1)), rs.getString(2),
                        rs.getString(3));
            } else {
                return null;
            }
        } finally {
            rs.close();
        }
    }

    public List<PBFolderInvitation> listPendingFolderInvitations(String to)
            throws SQLException
    {
        assert !to.isEmpty();

        PreparedStatement psListPFI = getConnection().prepareStatement("select " + SPSchema.C_FI_FROM + ", "
                + SPSchema.C_FI_FOLDER_NAME + ", " + SPSchema.C_FI_SID + " from " + SPSchema.T_FI + " where " + SPSchema.C_FI_TO +
                " = ? group by " + SPSchema.C_FI_SID);

        psListPFI.setString(1, to);
        ResultSet rs = psListPFI.executeQuery();
        try {
            ArrayList<PBFolderInvitation> invitations = new ArrayList<PBFolderInvitation>();
            while (rs.next()) {
                invitations.add(PBFolderInvitation.newBuilder()
                        .setSharer(rs.getString(1))
                        .setFolderName(rs.getString(2))
                        .setShareId(ByteString.copyFrom(rs.getBytes(3)))
                        .build());
            }
            return invitations;
        } finally {
            rs.close();
        }
    }


    @Override
    public String getOnePendingFolderInvitationCode(String to)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                        DBUtil.selectFromWhere(SPSchema.T_TI, SPSchema.C_TI_TO + "=?",
                                SPSchema.C_TI_TIC));

        ps.setString(1, to);
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
                + SPSchema.T_SF + " (" + SPSchema.C_SF_ID + ") values (?) on duplicate key update " + SPSchema.C_SF_ID  + "="
                + SPSchema.C_SF_ID);

        psAddSharedFolder.setBytes(1, sid.getBytes());

        // Update returns 0 on duplicate key and 1 on successful insert
        Util.verify(psAddSharedFolder.executeUpdate() < 2);
    }

    @Override
    public @Nullable String getFolderName(SID sid)
            throws SQLException
    {
        PreparedStatement psGetFolderName = getConnection().prepareStatement("select " + SPSchema.C_SF_NAME +
                " from " + SPSchema.T_SF + " where " + SPSchema.C_SF_ID + "=?");

        psGetFolderName.setBytes(1, sid.getBytes());
        ResultSet rs = psGetFolderName.executeQuery();

        try {
            if (rs.next()) {
                return rs.getString(1);
            } else {
                return null;
            }
        } finally {
            rs.close();
        }
    }

    @Override
    public void setFolderName(SID sid, String folderName)
            throws SQLException
    {
        PreparedStatement psSetFolderName = getConnection().prepareStatement("insert into " + SPSchema.T_SF +
                " (" + SPSchema.C_SF_ID + ", " + SPSchema.C_SF_NAME + ") values (?, ?) on duplicate key update " +
                SPSchema.C_SF_NAME + "=values(" + SPSchema.C_SF_NAME + ")");

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
        final String _ownerID;

        public DeviceRow(DID did, String name, String ownerID)
        {
            _did = did;
            _ownerID = ownerID;
            _name = name;
        }

        public String getOwnerID()
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
                SPSchema.C_DEVICE_NAME + "," + SPSchema.C_DEVICE_OWNER_ID + " from " + SPSchema.T_DEVICE + " where " +
                SPSchema.C_DEVICE_ID + " = ?");

        psGetDeviceUser.setString(1, did.toStringFormal());
        ResultSet rs = psGetDeviceUser.executeQuery();
        try {
            if (rs.next()) {
                return new DeviceRow(did, rs.getString(1), rs.getString(2));
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
            PreparedStatement psAddDev = getConnection().prepareStatement("insert into " + SPSchema.T_DEVICE
                    + "(" + SPSchema.C_DEVICE_ID + "," + SPSchema.C_DEVICE_NAME + "," + SPSchema.C_DEVICE_OWNER_ID + ")" +
                    " values (?,?,?)");
            psAddDev.setString(1, dr._did.toStringFormal());
            psAddDev.setString(2, dr._name);
            psAddDev.setString(3, dr._ownerID);
            psAddDev.executeUpdate();
        } catch (SQLException e) {
            checkDuplicateKey(e);
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
            PreparedStatement psAddCert = getConnection().prepareStatement("insert into " + SPSchema.T_CERT +
                    "(" + SPSchema.C_CERT_SERIAL + "," + SPSchema.C_CERT_DEVICE_ID + "," + SPSchema.C_CERT_EXPIRE_TS +
                    ") values (?,?,?)");

            psAddCert.setString(1, String.valueOf(serial));
            psAddCert.setString(2, did.toStringFormal());
            psAddCert.setTimestamp(3, new Timestamp(expireTs.getTime()));
            psAddCert.executeUpdate();
        } catch (SQLException e) {
            checkDuplicateKey(e);
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
                SPSchema.C_CERT_SERIAL + " from " + SPSchema.T_CERT + " where " + SPSchema.C_CERT_DEVICE_ID +
                " = ? and " + SPSchema.C_CERT_REVOKE_TS + " = 0");

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
            revokeCertificatesBySerials_(serials);
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
     * @param user the user whose certificates we are going to revoke.
     */
    public ImmutableList<Long> revokeUserCertificates(String user)
            throws SQLException
    {
        // Find all unrevoked serials for the device.
        PreparedStatement psRevokeUserCertificates = getConnection().prepareStatement("select " +
                SPSchema.C_CERT_SERIAL + " from " + SPSchema.T_CERT + " " + "join " + SPSchema.T_DEVICE + " on " +
                SPSchema.T_CERT + "." + SPSchema.C_CERT_DEVICE_ID + " = " + SPSchema.T_DEVICE + "." + SPSchema.C_DEVICE_ID +
                " where " + SPSchema.T_DEVICE + "." + SPSchema.C_DEVICE_OWNER_ID + " = ? and " +
                SPSchema.C_CERT_REVOKE_TS + " = 0");

        psRevokeUserCertificates.setString(1, user);

        ResultSet rs = psRevokeUserCertificates.executeQuery();
        try {
            Builder<Long> builder = ImmutableList.builder();

            while (rs.next()) {
                builder.add(rs.getLong(1));
            }

            ImmutableList<Long> serials = builder.build();
            revokeCertificatesBySerials_(serials);

            return serials;
        } finally {
            rs.close();
        }
    }

    private void revokeCertificatesBySerials_(ImmutableList<Long> serials)
            throws SQLException
    {
        // Update the revoke timestamp in the certificate table.
        PreparedStatement psRevokeCertificatesBySerials = getConnection().prepareStatement("update "
                + SPSchema.T_CERT + " set " + SPSchema.C_CERT_REVOKE_TS + " = current_timestamp, " +
                SPSchema.C_CERT_EXPIRE_TS + " = " + SPSchema.C_CERT_EXPIRE_TS + " where " + SPSchema.C_CERT_REVOKE_TS +
                " = 0 and " + SPSchema.C_CERT_SERIAL + " = ?");

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
        PreparedStatement psGetCRL = getConnection().prepareStatement("select " + SPSchema.C_CERT_SERIAL +
                " from " + SPSchema.T_CERT + " where " + SPSchema.C_CERT_EXPIRE_TS + " > current_timestamp and " +
                SPSchema.C_CERT_REVOKE_TS + " != 0");

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
     * @param user person requesting the ACL changes
     * @param sid store to which the acl changes will be made
     * @return true if the ACL changes should be allowed (i.e. the user has permissions)
     * @throws SQLException if there is a db error
     */
    private boolean canUserModifyACL_(String user, SID sid)
            throws SQLException, IOException
    {
        PreparedStatement psRoleCount = getConnection().prepareStatement(
                "select count(*) from " + SPSchema.T_AC + " where " + SPSchema.C_AC_STORE_ID + "=?");

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
                "select count(*) from " + SPSchema.T_AC + " where " + SPSchema.C_AC_STORE_ID + "=? and " +
                SPSchema.C_AC_USER_ID + "=?" + " and " + SPSchema.C_AC_ROLE + " = ?");

        psRoleCheck.setBytes(1, sid.getBytes());
        psRoleCheck.setString(2, user);
        psRoleCheck.setInt(3, Role.OWNER.ordinal());

        rs = psRoleCheck.executeQuery();
        try {
            Util.verify(rs.next());

            int ownerCount = rs.getInt(1);
            assert ownerCount >= 0 && ownerCount <= 1 : ("cannot have multiple owner acl entries");

            if (ownerCount == 1) {
                l.info(user + " is an owner for s:" + sid);
                return true;
            }
        } finally {
            rs.close();
        }

        // see if user is an admin and one of their organization's members is an owner
        User currentUser = getUser(user);
        if (currentUser._level == AuthorizationLevel.ADMIN) {
            l.info("user is an admin, checking if folder owner(s) are part of organization");

            PreparedStatement psOwnersInOrgCount = getConnection().prepareStatement(
                    "select count(*) from " + SPSchema.T_AC + " join " + SPSchema.T_USER + " on " + SPSchema.C_AC_USER_ID +
                    "=" + SPSchema.C_USER_ID + " where " + SPSchema.C_AC_STORE_ID + "=? and " + SPSchema.C_USER_ORG_ID +
                    "=? and " + SPSchema.C_AC_ROLE + "=?");

            psOwnersInOrgCount.setBytes(1, sid.getBytes());
            psOwnersInOrgCount.setString(2, currentUser._orgId);
            psOwnersInOrgCount.setInt(3, Role.OWNER.ordinal());

            rs = psOwnersInOrgCount.executeQuery();
            try {
                Util.verify(rs.next());
                int ownersInUserOrgCount = rs.getInt(1);
                l.info("there is/are " + ownersInUserOrgCount + " folder owner(s) in " + user +
                        "'s organization");
                assert !rs.next();
                if (ownersInUserOrgCount > 0) {
                    return true;
                }
            } finally {
                rs.close();
            }
        }

        l.info(user + " cannot modify acl for s:" + sid);

        return false; // user has no permissions
    }

    public ACLReturn getACL(long userEpoch, String user)
            throws SQLException
    {
        //
        // first check if the user actually needs to get the acl
        //

        // AAG IMPORTANT: both db calls _do not_ have to be part of the same transaction!

        Set<String> users = Sets.newHashSet();
        users.add(user);

        Map<String, Long> epochs = getACLEpochs_(users);
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
                SPSchema.C_AC_STORE_ID + ", acl_master." + SPSchema.C_AC_USER_ID + ", acl_master." +
                SPSchema.C_AC_ROLE + " from " + SPSchema.T_AC + " as acl_master inner join " + SPSchema.T_AC +
                " as acl_filter using (" + SPSchema.C_AC_STORE_ID + ") where acl_filter." +
                SPSchema.C_AC_USER_ID + "=?");

        psGetRoles.setString(1, user);

        Map<SID, List<SubjectRolePair>> storeToPairs = Maps.newHashMap();

        ResultSet rs = psGetRoles.executeQuery();
        try {
            while (rs.next()) {
                SID sid = new SID(rs.getBytes(1));

                if (!storeToPairs.containsKey(sid)) {
                    storeToPairs.put(sid, new LinkedList<SubjectRolePair>());
                }

                String subject = rs.getString(2);
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
     * @throws SQLException if the db calls failed
     */
    private Map<String, Long> getACLEpochs_(Set<String> users)
            throws SQLException
    {
        l.info("get epoch for " + users.size() + " users");

        PreparedStatement psGetEpoch = getConnection().prepareStatement("select " + SPSchema.C_USER_ID + ","
                + SPSchema.C_USER_ACL_EPOCH + " from " + SPSchema.T_USER + " where " + SPSchema.C_USER_ID + "=?");

        Map<String, Long> serverEpochs = Maps.newHashMap();

        ResultSet rs;
        for (String user : users) {
            psGetEpoch.setString(1, user);

            rs = psGetEpoch.executeQuery();
            try {
                if (rs.next()) {
                    String dbUser = rs.getString(1);
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
     * This method checks whether the requester has the right permissions needed to modify the
     * given store, and if not performs checks to detect malicious changes to permissions and
     * attempts to repair the store's permissions if needed. Updates pairs in place during the
     * repair process.
     */
    private void checkUserPermissionsAndClearACLForHijackedRootStore(String requester, SID sid,
            List<SubjectRolePair> pairs)
            throws SQLException, IOException, ExNoPerm
    {
        if (canUserModifyACL_(requester, sid)) return;

        // apparently the user cannot modify the ACL - check if an attacker maliciously
        // overwrote their permissions and repair the store if necessary

        l.info(requester + " cannot modify acl for s:" + sid);

        if (!Util.getRootSID(requester).equals(sid)) {
            throw new ExNoPerm(requester + " not owner"); // nope - just a regular store
        }

        l.info("s:" + sid + " matches " + requester + " root store - delete existing acl");

        PreparedStatement psDeleteAllRoles = getConnection().prepareStatement("delete from "
                + SPSchema.T_AC + " where " + SPSchema.C_AC_STORE_ID + "=?");

        psDeleteAllRoles.setBytes(1, sid.getBytes());

        int updatedRows = psDeleteAllRoles.executeUpdate();
        assert updatedRows > 0 : updatedRows;

        l.info("adding " + requester + " as owner of s:" + sid);

        boolean foundOwner = false;
        for (SubjectRolePair pair : pairs) {
            if (pair._subject.equals(requester) && pair._role.equals(Role.OWNER)) {
                foundOwner = true;
            }
        }

        if (!foundOwner) {
            pairs.add(new SubjectRolePair(requester, Role.OWNER));
        }
    }

    /**
     * Create ACLs for a store
     * @return new ACL epochs for each affected user id, to be published via verkehr
     */
    @Override
    public Map<String, Long> createACL(String requester, SID sid,
            List<SubjectRolePair> pairs)
            throws SQLException, ExNoPerm, IOException
    {
        l.info(requester + " create roles for s:" + sid);

        checkUserPermissionsAndClearACLForHijackedRootStore(requester, sid, pairs);

        addSharedFolder(sid); // to satisfy foreign key constraints add the sid before creating ACLs

        l.info(requester + " creating " + pairs.size() + " roles for s:" + sid);

        PreparedStatement psReplaceRole = getConnection().prepareStatement("insert into " + SPSchema.T_AC +
                " (" + SPSchema.C_AC_STORE_ID + "," + SPSchema.C_AC_USER_ID + "," + SPSchema.C_AC_ROLE + ") values (?, ?, ?) "
                + "on duplicate key update " + SPSchema.C_AC_ROLE + "= values (" + SPSchema.C_AC_ROLE + ")");

        for (SubjectRolePair pair : pairs) {
            psReplaceRole.setBytes(1, sid.getBytes());
            psReplaceRole.setString(2, pair._subject);
            psReplaceRole.setInt(3, pair._role.ordinal());
            psReplaceRole.addBatch();
        }

        executeBatchWarn(psReplaceRole, pairs.size(), 1); // update the roles for all users

        return incrementACLEpoch_(getStoreMembers_(sid));
    }

    /**
     * Update ACLs for a store
     * @throws ExNoPerm if trying to add new users to the store
     * @return new ACL epochs for each affected user id, to be published via verkehr
     */
    @Override
    public Map<String, Long> updateACL(String requester, SID sid, List<SubjectRolePair> pairs)
            throws SQLException, ExNoPerm, IOException
    {
        l.info(requester + " update roles for s:" + sid);

        checkUserPermissionsAndClearACLForHijackedRootStore(requester, sid, pairs);

        l.info(requester + " updating " + pairs.size() + " roles for s:" + sid);

        PreparedStatement psUpdateRole = getConnection().prepareStatement("update " + SPSchema.T_AC +
                " set " + SPSchema.C_AC_ROLE + "=? where " + SPSchema.C_AC_STORE_ID + "=? and " + SPSchema.C_AC_USER_ID +
                "=?");

        for (SubjectRolePair pair : pairs) {
            psUpdateRole.setInt(1, pair._role.ordinal());
            psUpdateRole.setBytes(2, sid.getBytes());
            psUpdateRole.setString(3, pair._subject);
            psUpdateRole.addBatch();
        }

        try {
            // throw if any query's affected rows != 1, meaning ACL entry doesn't exist
            executeBatch(psUpdateRole, pairs.size(), 1); // update the roles for all users
        } catch (ExSizeMismatch e) {
            throw new ExNoPerm("not permitted to create new ACLs when updating ACLs");
        }

        if (!hasAtLeastOneOwner(sid)) throw new ExNoPerm("Cannot demote all admins");

        return incrementACLEpoch_(getStoreMembers_(sid));
    }

    private boolean hasAtLeastOneOwner(SID sid) throws SQLException
    {
        PreparedStatement psCheckAtLeastOneOwner = getConnection()
                .prepareStatement("select " + SPSchema.C_AC_USER_ID + " from " + SPSchema.T_AC
                        + " where " + SPSchema.C_AC_STORE_ID + "=? and " + SPSchema.C_AC_ROLE + "=?"
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
    private Set<String> getStoreMembers_(SID sid)
            throws SQLException
    {
        PreparedStatement psGetSubjectsForStore = getConnection().prepareStatement("select " +
                SPSchema.C_AC_USER_ID + " from " + SPSchema.T_AC + " where " + SPSchema.C_AC_STORE_ID + "=?");

        psGetSubjectsForStore.setBytes(1, sid.getBytes());

        Set<String> subjects = Sets.newHashSet();
        ResultSet rs = psGetSubjectsForStore.executeQuery();
        try {
            while (rs.next()) {
                subjects.add(rs.getString(1));
            }
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
    public Map<String, Long> deleteACL(String user, SID sid, Set<String> subjects)
            throws SQLException, ExNoPerm, IOException
    {
        assert !getConnection().getAutoCommit() :
                ("auto-commit should be turned off before calling delete ACL");

        l.info(user + " delete roles for s:" + sid);

        if (!canUserModifyACL_(user, sid)) {
            l.info(user + " cannot modify acl for s:" + sid);

            throw new ExNoPerm(user + " is not an owner. If " + user + " has admin privileges " +
                    "no owner is a member of " + user + "'s organization.");
        }

        // setup the prepared statement
        PreparedStatement psDeleteRole = getConnection().prepareStatement("delete from " + SPSchema.T_AC +
                " where " + SPSchema.C_AC_STORE_ID + "=? and " + SPSchema.C_AC_USER_ID + "=?");

        // TODO: check that there is at least one admin left?

        // add all the users to be deleted to a batch update (for now don't worry about
        // splitting batches)

        for (String subject : subjects) {
            psDeleteRole.setBytes(1, sid.getBytes());
            psDeleteRole.setString(2, subject);
            psDeleteRole.addBatch();
        }

        l.info(user + " updating " + subjects.size() + " roles for s:" + sid);

        executeBatchWarn(psDeleteRole, subjects.size(), 1); // update roles for all users

        Set<String> affectedUsers = getStoreMembers_(sid); // get the current users
        affectedUsers.add(user); // add the caller as well
        affectedUsers.addAll(subjects); // add all the deleted guys as well

        return incrementACLEpoch_(affectedUsers);
    }

    /**
     * <strong>IMPORTANT:</strong> should only be called by setACL, deleteACL
     * when called, auto-commit should be off!
     * @param users set of user_ids for all users for which we will update the epoch
     * @return a map of user -> updated epoch number
     */
    private Map<String, Long> incrementACLEpoch_(Set<String> users)
            throws SQLException
    {
        l.info("incrementing epoch for " + users.size() + " users");

        PreparedStatement psUpdateACLEpoch = getConnection().prepareStatement("update " + SPSchema.T_USER +
                " set " + SPSchema.C_USER_ACL_EPOCH + "=" + SPSchema.C_USER_ACL_EPOCH + "+1 where " + SPSchema.C_USER_ID +
                "=?");

        for (String user : users) {
            l.info("attempt increment epoch for " + user);
            psUpdateACLEpoch.setString(1, user);
            psUpdateACLEpoch.addBatch();
        }

        executeBatchWarn(psUpdateACLEpoch, users.size(), 1);

        l.info("incremented epoch");

        return getACLEpochs_(users);
    }

    @Override
    public @Nullable Role getUserPermissionForStore(SID sid, String userID)
            throws SQLException
    {
        PreparedStatement psGetUserPermForStore = getConnection().prepareStatement("select " +
                SPSchema.C_AC_ROLE + " from " + SPSchema.T_AC + " where " + SPSchema.C_AC_STORE_ID + "=? and " + SPSchema.C_AC_USER_ID +
                "=?");

        psGetUserPermForStore.setBytes(1, sid.getBytes());
        psGetUserPermForStore.setString(2, userID);
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
            PreparedStatement psAddOrganization = getConnection().prepareStatement("insert into " +
                    SPSchema.T_ORGANIZATION + "(" + SPSchema.C_ORG_ID + "," + SPSchema.C_ORG_NAME + "," +
                    SPSchema.C_ORG_ALLOWED_DOMAIN + "," + SPSchema.C_ORG_OPEN_SHARING + ") values (?,?,?,?)");

            psAddOrganization.setString(1, org._id);
            psAddOrganization.setString(2, org._name);
            psAddOrganization.setString(3, org._allowedDomain);
            psAddOrganization.setBoolean(4, org._shareExternally);
            psAddOrganization.executeUpdate();
        } catch (SQLException e) {
            // The following will throw ExAlreadyExist if the orgId already exists
            checkDuplicateKey(e);
            throw e;
        }
    }

    /**
     * @return the Organization indexed by orgId
     * @throws ExNotFound if there is no row indexed by orgId
     */
    @Override
    public Organization getOrganization(final String orgId)
            throws SQLException, ExNotFound
    {
        PreparedStatement psGetOrganization = getConnection().prepareStatement("select " +
                SPSchema.C_ORG_NAME + "," + SPSchema.C_ORG_ALLOWED_DOMAIN + "," + SPSchema.C_ORG_OPEN_SHARING +
                " from " + SPSchema.T_ORGANIZATION + " where " + SPSchema.C_ORG_ID + "=?");

        psGetOrganization.setString(1, orgId);
        ResultSet rs = psGetOrganization.executeQuery();
        try {
            if (rs.next()) {
                String name = rs.getString(1);
                String domain = rs.getString(2);
                boolean shareExternally = rs.getBoolean(3);
                Organization org = new Organization(orgId, name, domain, shareExternally);
                assert !rs.next();
                return org;
            } else {
                throw new ExNotFound("Organization " + orgId + " does not exist.");
            }
        } finally {
            rs.close();
        }
    }

    @Override
    public void setOrganizationPreferences(final Organization newOrg)
            throws SQLException, ExNotFound
    {
        PreparedStatement psSetOrganizationPreferences = getConnection().prepareStatement("update "
                + SPSchema.T_ORGANIZATION + " set " + SPSchema.C_ORG_NAME + "=?, " + SPSchema.C_ORG_ALLOWED_DOMAIN + "=?, " +
                SPSchema.C_ORG_OPEN_SHARING + "=? where " + SPSchema.C_ORG_ID + "=?");

        psSetOrganizationPreferences.setString(1, newOrg._name);
        psSetOrganizationPreferences.setString(2, newOrg._allowedDomain);
        psSetOrganizationPreferences.setBoolean(3, newOrg._shareExternally);
        psSetOrganizationPreferences.setString(4, newOrg._id);

        if (psSetOrganizationPreferences.executeUpdate() == 0) {
            throw new ExNotFound("Organization " + newOrg._id + " does not exist.");
        }
    }

    @Override
    public void moveUserToOrganization(String userId, String orgId)
            throws SQLException, ExNotFound
    {
        try {
            PreparedStatement psMoveToOrg = getConnection().prepareStatement("update " + SPSchema.T_USER +
                    " set " + SPSchema.C_USER_ORG_ID + " = ? where " + SPSchema.C_USER_ID + "=?");

            psMoveToOrg.setString(1, orgId);
            psMoveToOrg.setString(2, userId);
            if (psMoveToOrg.executeUpdate() == 0) { // 0 rows updated if user id doesn't exist
                throw new ExNotFound("User " + userId + " does not exist.");
            }
        } catch (SQLException e) {
            checkOrganizationKeyConstraint(e, orgId); // throws ExNotFound if orgId doesn't exist
            throw e;
        }
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
                        DBUtil.selectFromWhere(
                                SPSchema.T_ES, SPSchema.C_ES_EMAIL + "=?", SPSchema.C_ES_SUBSCRIPTION));

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
    public String addEmailSubscription(String email, SubscriptionCategory sc, long time)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                       DBUtil.insertOnDuplicateUpdate(SPSchema.T_ES,
                               SPSchema.C_ES_LAST_EMAILED + "=?", SPSchema.C_ES_EMAIL,
                               SPSchema.C_ES_TOKEN_ID, SPSchema.C_ES_SUBSCRIPTION,
                               SPSchema.C_ES_LAST_EMAILED));

        String token = Base62CodeGenerator.newRandomBase62String(SubscriptionParams.TOKEN_ID_LENGTH);
        ps.setString(1, email);
        ps.setString(2, token);
        ps.setInt(3, sc.getCategoryID());
        ps.setTimestamp(4, new Timestamp(time), _calendar);
        ps.setTimestamp(5,new Timestamp(time), _calendar);

        Util.verify(ps.executeUpdate() == 1);

        return token;
    }

    @Override
    public String addEmailSubscription(String email, SubscriptionCategory sc)
            throws SQLException
    {
        return addEmailSubscription(email, sc, System.currentTimeMillis());
    }

    @Override
    public void removeEmailSubscription(String email, SubscriptionCategory sc)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                       DBUtil.deleteWhere(SPSchema.T_ES, SPSchema.C_ES_EMAIL,
                               SPSchema.C_ES_SUBSCRIPTION));

        ps.setString(1, email);
        ps.setInt(2, sc.getCategoryID());

        Util.verify(ps.executeUpdate() == 1);
    }

    @Override
    public void removeEmailSubscription(final String tokenId) throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
               DBUtil.deleteWhere(SPSchema.T_ES, SPSchema.C_ES_TOKEN_ID));

        ps.setString(1, tokenId);
        Util.verify(ps.executeUpdate() == 1);
    }

    @Override
    public String getTokenId(final String email, final SubscriptionCategory sc) throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                DBUtil.selectFromWhere(SPSchema.T_ES,
                        SPSchema.C_ES_EMAIL +"=? and " + SPSchema.C_ES_SUBSCRIPTION + "=?",
                        SPSchema.C_ES_TOKEN_ID));

        ps.setString(1, email);
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
                DBUtil.selectFromWhere(SPSchema.T_ES, SPSchema.C_ES_TOKEN_ID +"=?",
                        SPSchema.C_ES_EMAIL)
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
    public boolean isSubscribed(String email, SubscriptionCategory sc)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                        DBUtil.selectFromWhere(SPSchema.T_ES,
                                SPSchema.C_ES_EMAIL + "=? and " + SPSchema.C_ES_SUBSCRIPTION + "=?",
                                SPSchema.C_ES_EMAIL)
                                );

        ps.setString(1, email);
        ps.setInt(2, sc.getCategoryID());

        ResultSet rs = ps.executeQuery();
        try {
            return rs.next(); //true if an entry was found, false otherwise
        } finally {
            rs.close();
        }
    }

    @Override
    public synchronized void setLastEmailTime(String email, SubscriptionCategory category,
            long lastEmailTime)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                       DBUtil.updateWhere(SPSchema.T_ES,
                               SPSchema.C_ES_EMAIL + "=? and " + SPSchema.C_ES_SUBSCRIPTION + "=?",
                               SPSchema.C_ES_LAST_EMAILED));

        ps.setTimestamp(1, new Timestamp(lastEmailTime), _calendar);
        ps.setString(2, email);
        ps.setInt(3, category.getCategoryID());
        Util.verify(ps.executeUpdate() == 1);
    }

    @Override
    public Set<String> getUsersNotSignedUpAfterXDays(final int days, final int maxUsers,
                                                     final int offset)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                        "select " + SPSchema.C_TI_TO +
                        " from " + SPSchema.T_TI +
                        " left join " + SPSchema.T_USER + " on " + SPSchema.C_USER_ID + "=" +
                                SPSchema.C_TI_TO +
                        " where " + SPSchema.C_USER_ID + " is null " +
                        " and DATEDIFF(CURRENT_DATE(),DATE(" + SPSchema.C_TI_TS +")) =?" +
                        " limit ? offset ?");

        ps.setInt(1, days);
        ps.setInt(2, maxUsers);
        ps.setInt(3, offset);

        ResultSet rs = ps.executeQuery();
        try {
            Set<String> users = Sets.newHashSetWithExpectedSize(maxUsers);
            while (rs.next()) users.add(rs.getString(1));
            return users;
        } finally {
            rs.close();
        }
    }

    @Override
    public synchronized int getHoursSinceLastEmail(final String email,
            final SubscriptionCategory category)
            throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                       DBUtil.selectFromWhere(SPSchema.T_ES, SPSchema.C_ES_EMAIL + "=? and " +
                         SPSchema.C_ES_SUBSCRIPTION + "=?",
                         "HOUR(TIMEDIFF(CURRENT_TIMESTAMP()," + SPSchema.C_ES_LAST_EMAILED + "))"));

        ps.setString(1, email);
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
