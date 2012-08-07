package com.aerofs.srvlib.sp;

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
import com.aerofs.proto.Common.PBRole;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.GetDeviceInfoReply.PBDeviceInfo;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply.PBFolderInvitation;
import com.aerofs.proto.Sp.ListSharedFoldersResponse.PBSharedFolder;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.proto.Sp.ResolveTargetedSignUpCodeReply;
import com.aerofs.srvlib.db.AbstractDatabase;
import com.aerofs.srvlib.sp.organization.IOrganizationDatabase;
import com.aerofs.srvlib.sp.organization.Organization;
import com.aerofs.srvlib.sp.user.AuthorizationLevel;
import com.aerofs.srvlib.sp.user.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;

import javax.annotation.Nullable;

import static com.aerofs.srvlib.sp.SPSchema.*;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;

public class SPDatabase
        extends AbstractDatabase
        implements IOrganizationDatabase
{
    private final static Logger l = Util.l(SPDatabase.class);

    public void init_(String dbEndpoint, String schema, String dbUser, String dbPass)
            throws SQLException, ClassNotFoundException
    {
        Class.forName("com.mysql.jdbc.Driver");

        Connection c = DriverManager.getConnection(
                "jdbc:mysql://" + dbEndpoint + "/" + schema + "?user=" + dbUser + "&password=" +
                dbPass + "&autoReconnect=true&useUnicode=true&characterEncoding=utf8");

        super.init_(c);
    }

    // TODO use DBCW.checkDuplicateKey() instead
    private static void checkDuplicateKey(SQLException e) throws ExAlreadyExist
    {
        if (e.getMessage().startsWith("Duplicate entry")) throw new ExAlreadyExist(e);
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
        return  "select t1." + C_AC_STORE_ID + " from (" +
                    "select " + C_AC_STORE_ID + " from " + T_AC + " join " + T_USER
                    + " on " + C_AC_USER_ID + "=" + C_USER_ID + " where " +
                    C_USER_ORG_ID + "=?" +
                ") as t1 join " + T_AC + " as t2 on t1." + C_AC_STORE_ID + "=" +
                "t2." + C_AC_STORE_ID;
    }

    private PreparedStatement _psGU;

    // return null if not found
    public synchronized @Nullable User getUser(String id) throws SQLException, IOException
    {
        try {
            if (_psGU == null) {
                _psGU = _c.prepareStatement("select " + C_USER_FIRST_NAME + ","
                        + C_USER_LAST_NAME + "," + C_USER_CREDS + "," + C_FINALIZED + ","
                        + C_USER_VERIFIED + "," + C_USER_ORG_ID + "," + C_USER_AUTHORIZATION_LEVEL
                        + " from " + T_USER + " where " + C_USER_ID + "=?");
            }
            _psGU.setString(1, id);
            ResultSet rs = _psGU.executeQuery();
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

        } catch (SQLException e) {
            close(_psGU);
            _psGU = null;
            throw e;
        }
    }

    private PreparedStatement _psLU;
    private PreparedStatement _psLUMQ;

    public synchronized List<PBUser> listUsers(String orgId, int offset,
            int maxResults, @Nullable String search) throws
            SQLException
            // search is a string to search for within user ids; when search is non-null
            // listUsers will return all users that have search as a substring of their user ids.
    {
        try {
            ResultSet rs;

            if (_psLU == null || _psLUMQ == null) {
                _psLU = _c.prepareStatement("select " + C_USER_ID + "," +
                        C_USER_FIRST_NAME + "," + C_USER_LAST_NAME + " from " + T_USER +
                        " where " + C_USER_ORG_ID + "=? " + " order by " +
                        C_USER_ID + " limit ? offset ?");
                _psLUMQ = _c.prepareStatement("select " + C_USER_ID + "," +
                        C_USER_FIRST_NAME + "," + C_USER_LAST_NAME + " from " + T_USER +
                        " where " + C_USER_ORG_ID + "=? and " + C_USER_ID + " like ? " +
                        " order by " + C_USER_ID + " limit ? offset ?");
            }

            if (search == null || search.isEmpty()) {
                _psLU.setString(1, orgId);
                _psLU.setInt(2, maxResults);
                _psLU.setInt(3, offset);
                rs = _psLU.executeQuery();
            } else {
                _psLUMQ.setString(1, orgId);
                _psLUMQ.setString(2, "%" + search + "%");
                _psLUMQ.setInt(3, maxResults);
                _psLUMQ.setInt(4, offset);
                rs = _psLUMQ.executeQuery();
            }

            List<PBUser> resultSet = new ArrayList<PBUser>();
            try {
                while (rs.next()) {
                    String id = rs.getString(1);
                    String firstName = rs.getString(2);
                    String lastName = rs.getString(3);
                    PBUser u = PBUser.newBuilder()
                            .setUserEmail(id)
                            .setFirstName(firstName)
                            .setLastName(lastName)
                            .build();
                    resultSet.add(u);
                }
            } finally {
                rs.close();
            }
            return resultSet;
        } catch (SQLException e) {
            close(_psLU);
            close(_psLUMQ);
            _psLU = _psLUMQ = null;
            throw e;
        }
    }

    private PreparedStatement _psCU;
    private PreparedStatement _psCUMQ;

    public synchronized int countUsers(String orgId, @Nullable String search) throws
            SQLException
            // refer to listUsers above for an explanation of the search parameter here
    {
        try {
            ResultSet rs;

            if (_psCU == null || _psCUMQ == null) {
                _psCU = _c.prepareStatement("select count(" + C_USER_ID + ") from " + T_USER +
                        " where " + C_USER_ORG_ID + "=?");
                _psCUMQ = _c.prepareStatement("select count(" + C_USER_ID + ") from " + T_USER +
                        " where " + C_USER_ORG_ID + "=? and " + C_USER_ID + " like ?");
            }

            if (search == null || search.isEmpty()) {
                _psCU.setString(1, orgId);
                rs = _psCU.executeQuery();
            } else {
                _psCUMQ.setString(1, orgId);
                _psCUMQ.setString(2, "%" + search + "%");
                rs = _psCUMQ.executeQuery();
            }
            try {
                Util.verify(rs.next());
                int userCount = rs.getInt(1);
                assert !rs.next();
                return userCount;
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            close(_psCU);
            close(_psCUMQ);
            _psCU = _psCUMQ = null;
            throw e;
        }
    }

    private PreparedStatement _psLSF;

    @Override
    public synchronized List<PBSharedFolder> listSharedFolders(String orgId, int maxResults,
            int offset)
            throws SQLException
    {
        try {
            if (_psLSF == null) {
                // This massive sql statement is the result of our complicated DB schema around
                // shared folders. See sidListQuery for an explanation of the innermost query.
                // Following that query, the surrounding statement counts how many people have
                // permissions for each store id inside and discards any store ids where fewer than
                // 2 people have permissions. This statement also handles the offset into and size
                // limits needed for the entire query to return only a subset of shared folders.
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
                // TODO: Revisit this query once we have a more complete DB schema for shared folders
                _psLSF = _c.prepareStatement(
                        "select t1." + C_AC_STORE_ID + ", t2." + C_AC_USER_ID + ", t2."
                        + C_AC_ROLE + " from (" +
                            "select " + C_AC_STORE_ID + ", count(*) from (" +
                                sidListQuery() +
                            ") as t1 group by " + C_AC_STORE_ID + " having count(*) > 1 order by "
                            + C_AC_STORE_ID + " desc limit ? offset ?" +
                        ") as t1 join " + T_AC + " as t2 on t1." + C_AC_STORE_ID + "=t2." +
                        C_AC_STORE_ID + " order by t1.a_sid desc, t2.a_id asc"
                );
            }

            _psLSF.setString(1, orgId);
            _psLSF.setInt(2, maxResults);
            _psLSF.setInt(3, offset);
            ResultSet rs = _psLSF.executeQuery();

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
                        sfBuilder.setStoreId(storeId.toStringFormal());
                    }

                    String userId = rs.getString(2);
                    PBRole role = PBRole.valueOf(rs.getInt(3));
                    PBSubjectRolePair rolePair = PBSubjectRolePair.newBuilder()
                            .setSubject(userId)
                            .setRole(role)
                            .build();

                    sfBuilder.addSubjectRole(rolePair);
                }
                resultList.add(sfBuilder.build()); // add final shared folder
            } finally {
                rs.close();
            }
            return resultList;
        } catch (SQLException e) {
            close(_psLSF);
            _psLSF = null;
            throw e;
        }
    }

    private PreparedStatement _psCSF;

    @Override
    public synchronized int countSharedFolders(String orgId)
            throws SQLException
    {
        try {
            if (_psCSF == null) {
                // The statement here is taken from listSharedFolders above, but the outermost
                // statement in it has been modified to return the count of shared folders instead
                // of the users of the shared folders. Please see the explanation of the sql
                // statement in listSharedFolders for more details.
                _psCSF = _c.prepareStatement(
                        "select count(*) from (" +
                            "select " + C_AC_STORE_ID + ", count(*) from (" +
                                 sidListQuery() +
                            ") as t1 group by " + C_AC_STORE_ID + " having count(*) > 1" +
                        ") as t1"
                );
            }

            _psCSF.setString(1, orgId);
            ResultSet rs = _psCSF.executeQuery();
            try {
                Util.verify(rs.next());
                int folderCount = rs.getInt(1);
                assert !rs.next();
                return folderCount;
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            close(_psCSF);
            _psCSF = null;
            throw e;
        }
    }

    private PreparedStatement _psGIL;

    // return 0 if user not found
    public synchronized int getFolderlessInvitesQuota(String user) throws SQLException
    {
        try {
            if (_psGIL == null) {
                _psGIL = _c.prepareStatement("select " + C_USER_STORELESS_INVITES_QUOTA + " from "
                        + T_USER + " where " + C_USER_ID + "=?");
            }

            _psGIL.setString(1, user);
            ResultSet rs = _psGIL.executeQuery();
            try {
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    return 0;
                }
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            close(_psGIL);
            _psGIL = null;
            throw e;
        }
    }

    private PreparedStatement _psSSIQ;

    public synchronized void setFolderlessInvitesQuota(String user, int quota)
            throws SQLException
    {
        try {
            if (_psSSIQ == null) {
                _psSSIQ = _c.prepareStatement("update " + T_USER + " set "
                        + C_USER_STORELESS_INVITES_QUOTA + "=? where " + C_USER_ID + "=?");
            }

            _psSSIQ.setInt(1, quota);
            _psSSIQ.setString(2, user);
            _psSSIQ.executeUpdate();

        } catch (SQLException e) {
            close(_psSSIQ);
            _psSSIQ = null;
            throw e;
        }
    }

    private int getNewUserInitialACLEpoch()
    {
        return C.INITIAL_ACL_EPOCH + 1;
    }

    private PreparedStatement _psCUE;
    private PreparedStatement _psAU;

    /**
     * Add a user row to the sp_user table
     *
     * @param standalone whether we are calling this function in the context of
     * a larger transaction (false) or not (true)
     * Note: this method always creates the user as non-verified.
     * @return true if a new user row was added, false if the user exists, but was
     * not finalized
     * @throws SQLException if there was an error executing the SQL query
     * @throws ExAlreadyExist if the user exists (i.e. a row exists in the user table with
     * the same user id) and is finalized
     */
    public synchronized boolean addUser(User ur, boolean standalone)
            throws SQLException, ExAlreadyExist
    {
        // We are not going to set the verified field, so make sure nobody asks us to do so
        assert !ur._isVerified;

        boolean createduser = false;
        boolean commit = false;

        l.info("Add " + ur);

        try {
            if (standalone) _c.setAutoCommit(false);

            if (_psAU == null) {

                // we always create a user with initial epoch + 1 to ensure that the first time
                // a device is created it gets any acl updates that were made while the user
                // didn't have an entry in the user table

                _psAU = _c.prepareStatement("insert into " + T_USER +
                        "(" + C_USER_ID + "," + C_USER_CREDS + "," + C_USER_FIRST_NAME + "," +
                        C_USER_LAST_NAME + "," + C_USER_ORG_ID + "," + C_USER_AUTHORIZATION_LEVEL
                        + "," + C_USER_ACL_EPOCH + ") values (?,?,?,?,?,?," +
                        getNewUserInitialACLEpoch() + ")");
            }

            _psAU.setString(1, ur._id);
            _psAU.setString(2, Base64.encodeBytes(ur._shaedSP));
            _psAU.setString(3, ur._firstName);
            _psAU.setString(4, ur._lastName);
            _psAU.setString(5, ur._orgId);
            _psAU.setInt(6, ur._level.ordinal());
            _psAU.executeUpdate();

            createduser = true;
            commit = true;
        } catch (SQLException aue) {
            l.warn("addUser err:" + aue);

            try {
                // these steps should run no matter what
                close(_psAU);
                _psAU = null;

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
                    if (_psCUE == null) {
                        _psCUE = _c.prepareStatement("select " +
                       C_FINALIZED + " from " + T_USER + " where " +
                       C_USER_ID + " = ?");
                    }

                    _psCUE.setString(1, ur._id);

                    rscue = _psCUE.executeQuery();
                    boolean hasrows = rscue.next();
                    assert hasrows : ("ExExist but select fails");

                    boolean finalized = rscue.getBoolean(1);
                    if (finalized) throw new ExAlreadyExist(ur._id + " exists and is finalized");

                    l.info("addUser (2) user not finalized - update creds");

                    setUser(new User(ur, false));
                    createduser = false;
                } catch (SQLException cueex) {
                    close(_psCUE);
                    _psCUE = null;
                    throw cueex;
                } finally {
                    if (rscue != null) rscue.close();
                }
            }
        } finally {
            if (standalone) {
                if (commit) _c.commit(); else _c.rollback();
                _c.setAutoCommit(true);
            }
        }

        l.info("addUser (4) completed successfully");

        return createduser;
    }

    private PreparedStatement _psSU;

    /**
     * Update a user row if it exists, or create one if it doesn't
     * Note: this method never updates the verified field.
     */
    public synchronized void setUser(User ur) throws SQLException
    {
        try {
            l.info("set user row for " + ur);

            // [sigh] because of the craziness in the user setup I have to do this
            // basically, I see if an epoch exists for the user. If so, then I use it when
            // replacing the row. otherwise, I set it to the default value
            //
            // FWIW, AFAICT we should only get here on setup, so we should always use the default,
            // but, JIC, I'm doing this

            Map<String, Long> oldEpoch = getACLEpochs_(newHashSet(ur._id));
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

            _psSU = _c.prepareStatement("replace into " + T_USER +
                    "(" + C_USER_ID + "," + C_USER_FIRST_NAME + "," + C_USER_LAST_NAME + ","
                    + C_USER_ORG_ID + "," + C_USER_CREDS + ","
                    + C_FINALIZED + "," + C_USER_ACL_EPOCH  + "," + C_USER_AUTHORIZATION_LEVEL
                    + ") values (?,?,?,?,?,?,?,?)");

            _psSU.setString(1, ur._id);
            _psSU.setString(2, ur._firstName);
            _psSU.setString(3, ur._lastName);
            _psSU.setString(4, ur._orgId);
            _psSU.setString(5, Base64.encodeBytes(ur._shaedSP));
            _psSU.setBoolean(6, ur._isFinalized);
            _psSU.setLong(7, epoch);
            _psSU.setInt(8, ur._level.ordinal());
            Util.verify(_psSU.executeUpdate() == 1);

            l.info("setUser completed");
        } catch (SQLException e) {
            close(_psSU);
            _psSU = null;
            throw e;
        }
    }

    private PreparedStatement _psUVerified;
    public synchronized void markUserVerified(String userID)
            throws SQLException
    {
        try {
            if (_psUVerified == null) _psUVerified = _c.prepareStatement("update " + T_USER + " "
                    + "set " + C_USER_VERIFIED + "=true where " + C_USER_ID +"=?");

            _psUVerified.setString(1, userID);
            // TODO should this check whether the row was not found, instead of asserting?
            Util.verify(_psUVerified.executeUpdate() == 1);

            l.info("user " + userID + " marked verified");
        } catch (SQLException e) {
            close(_psUVerified);
            _psUVerified = null;
            throw e;
        }
    }

    private PreparedStatement _psSUN;

    public synchronized void setUserName(String userID, String firstName, String lastName)
            throws SQLException
    {
        try {
            if (_psSUN == null) _psSUN = _c.prepareStatement("update " + T_USER + " set "
                    + C_USER_FIRST_NAME + "=?, " + C_USER_LAST_NAME + "=? where "
                    + C_USER_ID + "=?");

            _psSUN.setString(1, firstName.trim());
            _psSUN.setString(2, lastName.trim());
            _psSUN.setString(3, userID);
            Util.verify(_psSUN.executeUpdate() == 1);
        } catch (SQLException e) {
            close(_psSUN);
            _psSUN = null;
            throw e;
        }
    }

    private PreparedStatement _psAPRT;

    public synchronized void addPasswordResetToken(String userID, String token)
        throws SQLException
    {
        try {
            if (_psAPRT == null) {
                _psAPRT = _c.prepareStatement("insert into " + T_PASSWORD_RESET + "(" +
                        C_PASS_TOKEN + "," + C_PASS_USER + ") values (?,?)");
            }
            _psAPRT.setString(1, token);
            _psAPRT.setString(2, userID);
            Util.verify(_psAPRT.executeUpdate() == 1);
        } catch (SQLException e) {
            close(_psAPRT);
            _psAPRT = null;
            throw e;
        }
    }

    private PreparedStatement _psRPRT;

    public synchronized String resolvePasswordResetToken(String token)
        throws SQLException, IOException, ExNotFound
    {
        try {

            if (_psRPRT == null) {

                _psRPRT = _c.prepareStatement("select " + C_PASS_USER + " from " + T_PASSWORD_RESET + " where " +
                        C_PASS_TOKEN + "=? and " + C_PASS_TS + " > ?");
            }
            _psRPRT.setString(1, token);
            java.util.Date today = new java.util.Date();

            _psRPRT.setTimestamp(2, new Timestamp(today.getTime() -
                    SPParam.PASSWORD_RESET_TOKEN_VALID_DURATION));
            ResultSet rs = _psRPRT.executeQuery();
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

        } catch (SQLException e) {
            close(_psRPRT);
            _psRPRT = null;
            throw e;
        }
    }

    private PreparedStatement _psDPRT;

    public synchronized void deletePasswordResetToken(String token)
        throws SQLException
    {
        try{
            if (_psDPRT == null) {
                _psDPRT = _c.prepareStatement("delete from " + T_PASSWORD_RESET + " where " +
                        C_PASS_TOKEN + " = ?");
            }
            _psDPRT.setString(1, token);
            int updates = _psDPRT.executeUpdate();
            Util.verify(updates == 1);
        } catch (SQLException e) {
            close(_psDPRT);
            _psDPRT = null;
            throw e;
        }
    }
    private PreparedStatement _psUUC;

    public synchronized void updateUserCredentials(String userID, byte[] credentials)
        throws SQLException
    {
        try {
            if (_psUUC == null) {
                _psUUC = _c.prepareStatement("update " + T_USER + " set " +
                        C_USER_CREDS + "=? where " + C_USER_ID + "=?");
            }
            _psUUC.setString(1, Base64.encodeBytes(credentials));
            _psUUC.setString(2, userID);
            Util.verify(_psUUC.executeUpdate() == 1);
        } catch (SQLException e) {
            close(_psUUC);
            _psUUC = null;
            throw e;
        }
    }

    private PreparedStatement _psTASUC;

    public synchronized void checkAndUpdateUserCredentials(String userID, byte[] old_credentials,
            byte[] new_credentials)
            throws ExNoPerm, SQLException
    {
        try {
            if (_psTASUC == null) {
                _psTASUC = _c.prepareCall("update " + T_USER + " set " + C_USER_CREDS + "=? where" +
                        " " + C_USER_ID + "=? AND " + C_USER_CREDS + "=?");
            }
            _psTASUC.setString(1,Base64.encodeBytes(new_credentials));
            _psTASUC.setString(2,userID);
            _psTASUC.setString(3,Base64.encodeBytes(old_credentials));
            int updated = _psTASUC.executeUpdate();
            if (updated == 0) {
                throw new ExNoPerm();
            }

        } catch (SQLException e) {
            close(_psTASUC);
            _psTASUC = null;
            throw e;
        }
    }

    private PreparedStatement _psGDI;

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
    public synchronized @Nullable PBDeviceInfo getDeviceInfo(DID did) throws SQLException
    {
        try {
            if (_psGDI == null) {
                // Need to join the user and the device table.
                _psGDI = _c.prepareStatement(
                    "select dev." + C_DEVICE_NAME + ", dev." + C_DEVICE_OWNER_ID + ", user." +
                    C_USER_FIRST_NAME + ", user." + C_USER_LAST_NAME + " from " + T_DEVICE +
                    " dev join " + T_USER + " user on dev." + C_DEVICE_OWNER_ID + " = user." +
                    C_USER_ID + " where dev." + C_DEVICE_ID + " = ?");
            }

            _psGDI.setString(1, did.toStringFormal());

            ResultSet rs = _psGDI.executeQuery();
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

        } catch (SQLException e) {
            close(_psGDI);
            _psGDI = null;
            throw e;
        }
    }

    private PreparedStatement _psGSUS;

    /**
     * Get the shared users set for a given user (i.e. the set of users that the supplied user
     * shares with).
     *
     * The smallest possible set is of size 1, since we say a user always shared with themselves
     * (even if there is no explicit ACL entry, i.e. even if they do not use shared folders).
     *
     * @param user the user whose shared user set we are going to compute.
     */
    public synchronized HashSet<String> getSharedUsersSet(String user) throws SQLException
    {
        HashSet<String> result = new HashSet<String>();

        // The user always shares with themselves.
        result.add(user);

        try {
            if (_psGSUS == null) {
                _psGSUS = _c.prepareStatement(
                        "select distinct t1." + C_AC_USER_ID +  " from " + T_AC + " t1 join " + T_AC +
                                " t2 on t1." + C_AC_STORE_ID +  " = t2." + C_AC_STORE_ID + " where t2." +
                                C_AC_USER_ID + " = ?");
            }

            _psGSUS.setString(1, user);

            ResultSet rs = _psGSUS.executeQuery();
            try {
                while (rs.next()) {
                    String sharedUser = rs.getString(1);
                    result.add(sharedUser);
                }
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            close(_psGSUS);
            _psGSUS = null;
            throw e;
        }

        return result;
    }

    /**
     * A class to hold both a username and a device ID.
     */
    public static class UserDevice
    {
        private final DID _did;
        private final String _userId;

        UserDevice(DID did, String userId)
        {
            this._did = did;
            this._userId = userId;
        }

        public DID getDID()
        {
            return _did;
        }

        public String getUserId()
        {
            return _userId;
        }
    }

    private PreparedStatement _psGIDS;

    /**
     * Get the interested devices set for a given SID belonging to a specific owner (i.e. the set
     * of devices that sync with a particular shared folder).
     *
     * Note that all the devices belonging to the owner are always included in the interested
     * devices set (regardless of exclusion).
     */
    public synchronized HashSet<UserDevice> getInterestedDevicesSet(SID sid, String ownderId)
            throws SQLException, ExFormatError
    {
        HashSet<UserDevice> result = new HashSet<UserDevice>();

        try {
            if (_psGIDS == null) {
                _psGIDS = _c.prepareStatement(
                        "select distinct dev." + C_DEVICE_ID + ", dev." + C_DEVICE_OWNER_ID +
                                " from " + T_AC + " acl right join " + T_DEVICE + " dev on acl." +
                                C_AC_USER_ID + " = dev." + C_DEVICE_OWNER_ID + " where acl." +
                                C_AC_STORE_ID + " = ? or " + C_DEVICE_OWNER_ID + " = ?");
            }

            _psGIDS.setBytes(1, sid.getBytes());
            _psGIDS.setString(2, ownderId);

            ResultSet rs = _psGIDS.executeQuery();
            try {
                while (rs.next()) {
                    String did = rs.getString(1);
                    String userId = rs.getString(2);
                    result.add(new UserDevice(new DID(did), userId));
                }
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            close(_psGIDS);
            _psGIDS = null;
            throw e;
        }

        return result;
    }

    private PreparedStatement _psSDN;

    public synchronized void setDeviceName(String user, DID did, String deviceName) throws SQLException
    {
        try {
            _psSDN = _c.prepareStatement("replace into " + T_DEVICE + "(" + C_DEVICE_NAME + "," +
                    C_DEVICE_ID + "," + C_DEVICE_OWNER_ID + ") values (?,?,?)");

            _psSDN.setString(1, deviceName.trim());
            _psSDN.setString(2, did.toStringFormal());
            _psSDN.setString(3, user);
            Util.verify(_psSDN.executeUpdate() == 1);
        } catch (SQLException e) {
            close(_psSDN);
            _psSDN = null;
            throw e;
        }
    }

    private PreparedStatement _psSAuthLevel;
    public synchronized void setAuthorizationLevel(String userID, AuthorizationLevel authLevel)
            throws SQLException
    {
        l.info("set auth to " + authLevel + " for " + userID);
        try {
            if (_psSAuthLevel == null)
                _psSAuthLevel = _c.prepareStatement("update " + T_USER + " set "
                        + C_USER_AUTHORIZATION_LEVEL + "=? where " + C_USER_ID + "=?");

            _psSAuthLevel.setInt(1, authLevel.ordinal());
            _psSAuthLevel.setString(2, userID);
            Util.verify(_psSAuthLevel.executeUpdate() == 1);
        } catch (SQLException e) {
            close(_psSAuthLevel);
            _psSAuthLevel = null;
            throw e;
        }
    }

    private PreparedStatement _psMinBatchIdx;
    private PreparedStatement _psUpMinBatch;

    // TODO Allen: don't call other database methods here after transaction is done
    //
    // the combination of synchronized and transactions mean that I can simply
    // check and update a single table
    synchronized void useBatchSignUp(User user, String bsc)
        throws SQLException, ExNotFound
    {
        boolean commit = false;
        try {
            l.info("use bsc:" + bsc + " user:" + user);

            _c.setAutoCommit(false);

            // NOTE: I wanted to do update & select in the same statement, but
            // this is not possible in mysql. As a result, I split it into two
            // sql calls

            if (_psMinBatchIdx == null) {
                _psMinBatchIdx = _c.prepareStatement(
                    "select min(" + C_BI_IDX + ") from " +
                    T_BI + " where " + C_BI_BIC + "=? " + "and " +
                    C_BI_USER + " is null");
            }

            _psMinBatchIdx.setString(1, bsc);

            // find the min null-user batch idx for the given bsc

            int minIdx;
            ResultSet minIdxRs = _psMinBatchIdx.executeQuery();
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

            if (_psUpMinBatch == null) {
                _psUpMinBatch = _c.prepareStatement(
                    "update " + T_BI + " set " + C_BI_USER + "=? where " +
                    C_BI_IDX + "=?");
            }

            _psUpMinBatch.setString(1, user._id);
            _psUpMinBatch.setInt(2, minIdx);
            Util.verify(_psUpMinBatch.executeUpdate() == 1);

            commit = true;

        } catch (SQLException e) {
            close(_psMinBatchIdx);
            _psMinBatchIdx = null;
            close(_psUpMinBatch);
            _psUpMinBatch = null;
            throw e;
        } finally {
            if (commit) _c.commit(); else _c.rollback();
            _c.setAutoCommit(true);
        }
    }

    private PreparedStatement _psAddTI;

    public synchronized void addTargetedSignupCode(String code, String from, String to, String orgId)
        throws SQLException, ExAlreadyExist
    {
        try {
            if (_psAddTI == null)
                _psAddTI = _c.prepareStatement("insert into " + T_TI +
                        " (" + C_TI_TIC + "," + C_TI_FROM + "," + C_TI_TO + "," + C_TI_ORG_ID + ") "
                        + "values (?,?,?,?)");

            _psAddTI.setString(1, code);
            _psAddTI.setString(2, from);
            _psAddTI.setString(3, to);
            _psAddTI.setString(4, orgId);

            _psAddTI.executeUpdate();
        } catch (SQLException e) {
            // do not use checkDuplicateKey() here. Duplicate keys aren't an expected error,
            // and should be treated as sql exceptions
            close(_psAddTI);
            _psAddTI = null;
            throw e;
        }
    }

    private PreparedStatement _psAddFI;

    public synchronized void addShareFolderCode(String code, String from, String to, byte[] sid,
            String folderName)
            throws SQLException
    {
        try {
            if (_psAddFI == null)
                _psAddFI = _c.prepareStatement("insert into " + T_FI
                        + " (" + C_FI_FIC + "," + C_FI_FROM + "," + C_FI_TO + "," + C_FI_SID + ","
                        + C_FI_FOLDER_NAME + ") " + "values (?,?,?,?,?)");

            _psAddFI.setString(1, code);
            _psAddFI.setString(2, from);
            _psAddFI.setString(3, to);
            _psAddFI.setBytes(4, sid);
            _psAddFI.setString(5, folderName);

            _psAddFI.executeUpdate();
        } catch (SQLException e) {
            // do not use checkDuplicateKey() here. Duplicate keys aren't an expected error,
            // and should be treated as sql exceptions
            close(_psAddFI);
            _psAddFI = null;
            throw e;
        }
    }

    private PreparedStatement _psInitBIC;

    public synchronized void initBatchSignUpCode(String bsc, int signUpCount)
        throws SQLException
    {
        try {
            _c.setAutoCommit(false);

            if (_psInitBIC == null) {
                _psInitBIC = _c.prepareStatement("insert into " + T_BI +
                    " (" + C_BI_BIC + ", " + C_BI_USER + ") values " + "(?, ?)");
            }

            int sqlBatchCount = 0;
            for (int i = 0; i < signUpCount; ++i) {
                if (sqlBatchCount == SPParam.SP_SQL_BATCH_IIC_BATCH_MAX) {
                    _psInitBIC.executeBatch();
                    sqlBatchCount = 0;
                }

                _psInitBIC.setString(1, bsc);
                _psInitBIC.setString(2, null);
                _psInitBIC.addBatch();
                sqlBatchCount++;
            }
            _psInitBIC.executeBatch();

            _c.commit();
        } catch (SQLException e) {
            _c.rollback();
            _psInitBIC = null;
            throw e;
        } finally {
            _c.setAutoCommit(true);
        }
    }

    private PreparedStatement _psCountUnused;

    public synchronized int countUnusedBatchSignUps(String bsc)
        throws SQLException
    {
        try {
            if (_psCountUnused == null) {
                _psCountUnused = _c.prepareStatement("select count(*) from " +
                    T_BI + " where " + C_BI_BIC + "=? and " +
                    C_BI_USER + " is null");
            }

            _psCountUnused.setString(1, bsc);
            ResultSet rs = _psCountUnused.executeQuery();
            try {
                return rs.next() ? rs.getInt(1) : 0;
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _psCountUnused = null;
            throw e;
        }
    }

    public synchronized boolean isValidBatchSignUp(String bsc)
        throws SQLException
    {
        int count = countUnusedBatchSignUps(bsc);
        return (count > 0);
    }

    /**
     * Returns the organization id associated with a batch signup, or null if the signup
     * code is invalid.
     */
    public synchronized String checkBatchSignUpAndGetOrg(String bsc)
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

    private PreparedStatement _psGetTI;

    /**
     * @param tsc the invitation code
     * @return null if not found
     */
    public synchronized ResolveTargetedSignUpCodeReply getTargetedSignUp(String tsc)
        throws SQLException
    {
        try {
            if (_psGetTI == null) {
                _psGetTI = _c.prepareStatement("select " + C_TI_TO + ", " + C_TI_ORG_ID
                        + " from " + T_TI + " where " + C_TI_TIC + "=?");
            }
            _psGetTI.setString(1, tsc);
            ResultSet rs = _psGetTI.executeQuery();
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

        } catch (SQLException e) {
            _psGetTI = null;
            throw e;
        }
    }

    private PreparedStatement _psGetFI;

    // TODO (GS) merge this class and PBFolderInvitation
    public static class FolderInvitation
    {
        final byte[] _sid;
        final String _folderName;
        final String _invitee;

        private FolderInvitation(byte[] sid, String folderName, String invitee)
        {
            _sid = sid;
            _folderName = folderName;
            _invitee = invitee;
        }

        public byte[] getSid()
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
    public synchronized FolderInvitation getFolderInvitation(String code)
            throws SQLException
    {
        try {
            if (_psGetFI == null) {
                _psGetFI = _c.prepareStatement("select " + C_FI_SID + ", " + C_FI_FOLDER_NAME
                        + ", " + C_FI_TO + " from " + T_FI + " where " + C_FI_FIC + "=?");
            }
            _psGetFI.setString(1, code);
            ResultSet rs = _psGetFI.executeQuery();
            try {
                if (rs.next()) {
                    return new FolderInvitation(rs.getBytes(1), rs.getString(2), rs.getString(3));
                } else {
                    return null;
                }
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            _psGetFI = null;
            throw e;
        }
    }

    private PreparedStatement _psListPFI;

    public synchronized List<PBFolderInvitation> listPendingFolderInvitations(String to)
            throws SQLException
    {
        assert !to.isEmpty();

        try {
            if (_psListPFI == null) {
                _psListPFI = _c.prepareStatement("select " + C_FI_FROM + ", " + C_FI_FOLDER_NAME
                        + ", " + C_FI_SID + " from " + T_FI + " where " + C_FI_TO + "=?"
                        + " group by " + C_FI_SID);
            }
            _psListPFI.setString(1, to);
            ResultSet rs = _psListPFI.executeQuery();
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

        } catch (SQLException e) {
            _psListPFI = null;
            throw e;
        }
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

    private PreparedStatement _psGetDeviceUser;

    /**
     * @return null if the device is not found
     */
    public synchronized @Nullable DeviceRow getDevice(DID did) throws SQLException
    {
        try {
            if (_psGetDeviceUser == null) {
                _psGetDeviceUser = _c.prepareStatement("select " + C_DEVICE_NAME + "," +
                        C_DEVICE_OWNER_ID + " from " + T_DEVICE + " where " + C_DEVICE_ID + "=?");
            }
            _psGetDeviceUser.setString(1, did.toStringFormal());
            ResultSet rs = _psGetDeviceUser.executeQuery();
            try {
                if (rs.next()) {
                    return new DeviceRow(did, rs.getString(1), rs.getString(2));
                } else {
                    return null;
                }
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            _psGetDeviceUser = null;
            throw e;
        }
    }

    private PreparedStatement _psAddDev;

    public synchronized void addDevice(DeviceRow dr) throws SQLException, ExAlreadyExist
    {
        try {
            if (_psAddDev == null) {
                _psAddDev = _c.prepareStatement("insert into " + T_DEVICE +
                        "(" + C_DEVICE_ID + "," + C_DEVICE_NAME + "," + C_DEVICE_OWNER_ID + ")" +
                                " values (?,?,?)");
            }

            _psAddDev.setString(1, dr._did.toStringFormal());
            _psAddDev.setString(2, dr._name);
            _psAddDev.setString(3, dr._ownerID);
            _psAddDev.executeUpdate();
        } catch (SQLException e) {
            checkDuplicateKey(e);
            close(_psAddDev);
            _psAddDev = null;
            throw e;
        }
    }

    private PreparedStatement _psAddCert;

    /**
     * Add a certificate row to the certificate table.
     *
     * @param serial the serial number of this new certificate.
     * @param did the device which owns this certificate.
     * @param expireTs the date (in the future) at which this certificate expires.
     */
    public synchronized void addCertificate(long serial, DID did, Date expireTs)
            throws SQLException, ExAlreadyExist
    {
        try {
            if (_psAddCert == null) {
                _psAddCert = _c.prepareStatement("insert into " + T_CERT + "(" + C_CERT_SERIAL +
                        "," + C_CERT_DEVICE_ID + "," + C_CERT_EXPIRE_TS + ")" + " values (?,?,?)");
            }

            _psAddCert.setString(1, String.valueOf(serial));
            _psAddCert.setString(2, did.toStringFormal());
            _psAddCert.setTimestamp(3, new Timestamp(expireTs.getTime()));
            _psAddCert.executeUpdate();
        } catch (SQLException e) {
            checkDuplicateKey(e);
            close(_psAddCert);
            _psAddCert = null;
            throw e;
        }
    }

    private PreparedStatement _psRevokeDeviceCertificate;

    /**
     * Revoke the certificates belonging to a single device.
     *
     * Important note: this should be called within a transaction!
     *
     * @param did the device whose certificates we are going to revoke.
     */
    public synchronized ImmutableList<Long> revokeDeviceCertificate(final DID did)
            throws SQLException
    {
        try {
            // Find the affected serial in the certificate table.
            if (_psRevokeDeviceCertificate == null) {
                _psRevokeDeviceCertificate = _c.prepareStatement("select " + C_CERT_SERIAL +
                        " from " + T_CERT + " where " + C_CERT_DEVICE_ID + " = ? and " +
                        C_CERT_REVOKE_TS + " = 0");
            }

            _psRevokeDeviceCertificate.setString(1, did.toStringFormal());

            ResultSet rs = _psRevokeDeviceCertificate.executeQuery();
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
            }
            finally {
                rs.close();
            }

        } catch (SQLException e) {
            close(_psRevokeDeviceCertificate);
            _psRevokeDeviceCertificate = null;
            throw e;
        }
    }

    private PreparedStatement _psRevokeUserCertificates;

    /**
     * Revoke all certificates belonging to user.
     *
     * Important note: this should be called within a transaction!
     *
     * @param user the user whose certificates we are going to revoke.
     */
    public synchronized ImmutableList<Long> revokeUserCertificates(String user)
            throws SQLException
    {
        try {
            // Find all unrevoked serials for the device.
            if (_psRevokeUserCertificates == null) {
                _psRevokeUserCertificates = _c.prepareStatement("select " + C_CERT_SERIAL +
                        " from " + T_CERT + " " + "join " + T_DEVICE + " on " + T_CERT + "." +
                        C_CERT_DEVICE_ID + " = " + T_DEVICE + "." + C_DEVICE_ID +
                        " where " + T_DEVICE + "." + C_DEVICE_OWNER_ID + " = ? and " +
                        C_CERT_REVOKE_TS + " = 0");
            }

            _psRevokeUserCertificates.setString(1, user);

            ResultSet rs = _psRevokeUserCertificates.executeQuery();
            try {
                Builder<Long> builder = ImmutableList.builder();

                while (rs.next()) {
                    builder.add(rs.getLong(1));
                }

                ImmutableList<Long> serials = builder.build();
                revokeCertificatesBySerials_(serials);

                return serials;
            }
            finally {
                rs.close();
            }
        } catch (SQLException e) {
            close(_psRevokeUserCertificates);
            _psRevokeUserCertificates = null;
            throw e;
        }
    }

    private PreparedStatement _psRevokeCertificatesBySerials;

    private void revokeCertificatesBySerials_(ImmutableList<Long> serials)
            throws SQLException
    {
        try {
            // Update the revoke timestamp in the certificate table.
            if (_psRevokeCertificatesBySerials == null) {
                _psRevokeCertificatesBySerials = _c.prepareStatement("update " + T_CERT + " set " +
                        C_CERT_REVOKE_TS + " = current_timestamp, " + C_CERT_EXPIRE_TS + " = " +
                        C_CERT_EXPIRE_TS + " where " + C_CERT_REVOKE_TS + " = 0 and " +
                        C_CERT_SERIAL + " = ?");
            }

            for (Long serial : serials) {
                _psRevokeCertificatesBySerials.setLong(1, serial);
                _psRevokeCertificatesBySerials.addBatch();
            }

            executeBatch(_psRevokeCertificatesBySerials, serials.size(), 1, false);
        } catch (SQLException e) {
            close(_psRevokeCertificatesBySerials);
            _psRevokeCertificatesBySerials = null;
            throw e;
        }
    }

    private PreparedStatement _psGetCRL;

    /**
     * Get a a list of revoked certificate serial numbers. The returned certificates have an
     * expiry date that is in the future.
     *
     * @return list of revoked certificates.
     */
    public synchronized ImmutableList<Long> getCRL()
            throws SQLException
    {
        try {
            if (_psGetCRL == null) {
                _psGetCRL = _c.prepareStatement("select " + C_CERT_SERIAL + " from " + T_CERT +
                        " where " + C_CERT_EXPIRE_TS + " > current_timestamp and " +
                        C_CERT_REVOKE_TS + " != 0");
            }

            ResultSet rs = _psGetCRL.executeQuery();
            try {
                Builder<Long> builder = ImmutableList.builder();
                while (rs.next()) {
                    builder.add(rs.getLong(1));
                }
                return builder.build();
            }
            finally {
                rs.close();
            }
        } catch (SQLException e) {
            close(_psGetCRL);
            _psGetCRL = null;
            throw e;
        }
    }

    //
    //
    // AAG FIXME: IMPORTANT!!!!!
    //
    // AAG FIXME: consider refactoring ACL db calls into a separate object!!!
    //
    //

    private PreparedStatement _psRoleCount;
    private PreparedStatement _psRoleCheck;

    /**
     * <strong>Call in the context of an overall transaction only!</strong>
     *
     *
     * @param user person requesting the ACL changes
     * @param sid store to which the acl changes will be made
     * @return true if the ACL changes should be allowed (i.e. the user has permissions)
     * @throws SQLException if there is a db error
     */
    private synchronized boolean checkIfUserCanModifyACL_(String user, SID sid)
            throws SQLException
    {
        try {
            if (_psRoleCount == null) {
                _psRoleCount = _c.prepareStatement("select count(*) from " + T_AC + " where " +
                        C_AC_STORE_ID + "=?");
            }

            _psRoleCount.setBytes(1, sid.getBytes());

            ResultSet rs;

            rs = _psRoleCount.executeQuery();
            try {
                Util.verify(rs.next());
                if (rs.getInt(1) == 0) {
                    l.info("allow acl modification - no roles exist for s:" + sid);
                    return true;
                }
            } finally {
                rs.close();
            }

            if (_psRoleCheck == null) {
                _psRoleCheck = _c.prepareStatement("select count(*) from " + T_AC + " where " +
                        C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?" + " and " + C_AC_ROLE +
                        "=?");
            }

            _psRoleCheck.setBytes(1, sid.getBytes());
            _psRoleCheck.setString(2, user);
            _psRoleCheck.setInt(3, Role.OWNER.ordinal());

            rs = _psRoleCheck.executeQuery();
            try {
                Util.verify(rs.next());

                int ownerCount = rs.getInt(1);
                assert ownerCount >= 0 && ownerCount <=1 :
                        ("cannot have multiple owner acl entries");

                if (ownerCount == 1) {
                    l.info(user + " is an owner for s:" + sid);
                    return true;
                }
            } finally {
                rs.close();
            }

            l.info(user + " cannot modify acl for s:" + sid);

            return false; // user has no permissions
        } catch (SQLException e) {
            close(_psRoleCount);
            _psRoleCount = null;

            close(_psRoleCheck);
            _psRoleCheck = null;

            throw e;
        }
    }

    private PreparedStatement _psGetRoles;

    public synchronized ACLReturn getACL(long userEpoch, String user)
            throws SQLException
    {
        //
        // first check if the user actually needs to get the acl
        //

        // AAG IMPORTANT: both db calls _do not_ have to be part of the same transaction!

        Set<String> users = new HashSet<String>(1);
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

        try {
            if (_psGetRoles == null) {
                _psGetRoles = _c.prepareStatement("select acl_master." + C_AC_STORE_ID +
                        ", acl_master." + C_AC_USER_ID + ", acl_master." + C_AC_ROLE + " from " +
                        T_AC + " as acl_master inner join " + T_AC + " as acl_filter using (" +
                        C_AC_STORE_ID + ") where acl_filter." + C_AC_USER_ID + "=?");
            }

            _psGetRoles.setString(1, user);

            Map<SID, List<SubjectRolePair>> storeToPairs = new HashMap<SID, List<SubjectRolePair>>();

            ResultSet rs = _psGetRoles.executeQuery();
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
        } catch (SQLException e) {
            close(_psGetRoles);
            _psGetRoles = null;

            throw e;
        }
    }

    private PreparedStatement _psGetEpoch;

    /**
     * @param users set of users for whom you want the acl epoch number
     * @return a map of user -> epoch number
     * @throws SQLException if the db calls failed
     */
    private Map<String, Long> getACLEpochs_(Set<String> users)
            throws SQLException
    {
        try {
            l.info("get epoch for " + users.size() + " users");

            if (_psGetEpoch == null) {
                _psGetEpoch = _c.prepareStatement("select " + C_USER_ID + "," + C_USER_ACL_EPOCH
                        + " from " + T_USER + " where " + C_USER_ID + "=?");
            }

            Map<String, Long> serverEpochs = newHashMap();

            ResultSet rs;
            for (String user : users) {
                _psGetEpoch.setString(1, user);

                rs = _psGetEpoch.executeQuery();
                try {
                    if (rs.next()) {
                        String dbUser = rs.getString(1);
                        long dbEpoch = rs.getLong(2);

                        assert dbUser.equals(user) :
                                ("mismatched user exp:" + user + " act:" + dbUser);

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
        } catch (SQLException e) {
            close(_psGetEpoch);
            _psGetEpoch = null;

            throw e;
        }
    }

    private PreparedStatement _psDeleteAllRoles;
    private PreparedStatement _psReplaceRole;

    /**
     * <strong>IMPORTANT: Caller must 1) synchronize on {@link SPDatabase}  AND 2) call {@link
     * SPDatabase#setAutoCommit} (with false) before calling this method. Caller also
     * has to call {@link SPDatabase#setAutoCommit} (with true) after method is done.
     * </strong>
     */
    public synchronized Map<String, Long> setACL(String requester, SID sid,
            List<SubjectRolePair> pairs)
            throws SQLException, ExNoPerm
    {
        //assert !_c.getAutoCommit() : ("auto-commit should be turned off before calling setACL");

        try {
            l.info(requester + " set roles for s:" + sid);

            if (!checkIfUserCanModifyACL_(requester, sid)) {
                // apparently the user cannot modify the ACL - check if an attacker maliciously
                // overwrote their permissions

                l.info(requester + " cannot modify acl for s:" + sid);

                if (!Util.getRootSID(requester).equals(sid)) {
                    throw new ExNoPerm(requester + " not owner"); // nope - just a regular store
                }

                l.info("s:" + sid + " matches " + requester + " root store - delete existing acl");

                if (_psDeleteAllRoles == null) {
                    _psDeleteAllRoles = _c.prepareStatement("delete from " + T_AC + " where " +
                            C_AC_STORE_ID + "=?");
                }

                _psDeleteAllRoles.setBytes(1, sid.getBytes());

                int updatedRows = _psDeleteAllRoles.executeUpdate();
                assert updatedRows > 0;

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

            l.info(requester + " updating " + pairs.size() + " roles for s:" + sid);

            if (_psReplaceRole == null) {
                _psReplaceRole = _c.prepareStatement("insert into " + T_AC + " (" +
                    C_AC_STORE_ID + "," + C_AC_USER_ID + "," + C_AC_ROLE + ") values (?, ?, ?) " +
                    "on duplicate key update " + C_AC_ROLE + "= values (" + C_AC_ROLE + ")");
            }

            for (SubjectRolePair pair : pairs) {
                _psReplaceRole.setBytes(1, sid.getBytes());
                _psReplaceRole.setString(2, pair._subject);
                _psReplaceRole.setInt(3, pair._role.ordinal());
                _psReplaceRole.addBatch();
            }

            executeBatch(_psReplaceRole, pairs.size(), 1, false); // update the roles for all users

            Set<String> affectedUsers = getSubjectsForStore_(sid);
            Map<String, Long> updatedEpochs = incrementACLEpoch_(affectedUsers);

            return updatedEpochs;
        } catch (SQLException e) {
            close(_psReplaceRole);
            _psReplaceRole = null;

            throw e;
        }
    }

    private PreparedStatement _psGetSubjectsForStore;

    private Set<String> getSubjectsForStore_(SID sid)
            throws SQLException
    {
        try {
            if (_psGetSubjectsForStore == null) {
                _psGetSubjectsForStore = _c.prepareStatement("select " + C_AC_USER_ID + " from " +
                        T_AC + " where " + C_AC_STORE_ID + "=?");
            }

            _psGetSubjectsForStore.setBytes(1, sid.getBytes());

            Set<String> subjects = new HashSet<String>();
            ResultSet rs = _psGetSubjectsForStore.executeQuery();
            try {
                while (rs.next()) {
                    subjects.add(rs.getString(1));
                }
            } finally {
                rs.close();
            }

            return subjects;
        } catch (SQLException e) {
            close(_psGetSubjectsForStore);
            _psGetSubjectsForStore = null;

            throw e;
        }
    }

    private PreparedStatement _psDeleteRole;

    /**
     * <strong>IMPORTANT: Caller must 1) synchronize on {@link SPDatabase}  AND 2) call {@link
     * SPDatabase#setAutoCommit} (with false) before calling this method. Caller also
     * has to call {@link SPDatabase#setAutoCommit} (with true) after method is done.
     * </strong>
     */
    public Map<String, Long> deleteACL(String user, SID sid, Set<String> subjects)
            throws SQLException, ExNoPerm
    {
        assert !_c.getAutoCommit() : ("auto-commit should be turned off before calling deleteACL");

        try {
            l.info(user + " delete roles for s:" + sid);

            if (!checkIfUserCanModifyACL_(user, sid)) {
                l.info(user + " cannot modify acl for s:" + sid);

                throw new ExNoPerm(user + " not owner");
            }

            // setup the prepared statement

            if (_psDeleteRole == null) {
                _psDeleteRole = _c.prepareStatement("delete from " + T_AC + " where " +
                        C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?");
            }

            // add all the users to be deleted to a batch update (for now don't worry about
            // splitting batches)

            for (String subject: subjects) {
                _psDeleteRole.setBytes(1, sid.getBytes());
                _psDeleteRole.setString(2, subject);
                _psDeleteRole.addBatch();
            }

            l.info(user + " updating " + subjects.size() + " roles for s:" + sid);

            executeBatch(_psDeleteRole, subjects.size(), 1, false); // update roles for all users

            Set<String> affectedUsers = getSubjectsForStore_(sid); // get the current users
            affectedUsers.add(user); // add the caller as well
            affectedUsers.addAll(subjects); // add all the deleted guys as well
            Map<String, Long> updatedEpochs = incrementACLEpoch_(affectedUsers);

            return updatedEpochs;
        } catch (SQLException e) {
            close(_psDeleteRole);
            _psDeleteRole = null;

            throw e;
        }
    }

    private PreparedStatement _psUpdateACLEpoch;

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

        try {
            if (_psUpdateACLEpoch == null) {
                _psUpdateACLEpoch = _c.prepareStatement("update " + T_USER + " set " +
                        C_USER_ACL_EPOCH + "=" + C_USER_ACL_EPOCH + "+1 where " + C_USER_ID +
                        "=?");
            }

            for (String user : users) {
                l.info("attempt increment epoch for " + user);
                _psUpdateACLEpoch.setString(1, user);
                _psUpdateACLEpoch.addBatch();
            }

            executeBatch(_psUpdateACLEpoch, users.size(), 1, false);

            l.info("incremented epoch");

            return getACLEpochs_(users);
        } catch (SQLException e) {
            close(_psUpdateACLEpoch);
            _psUpdateACLEpoch = null;

            throw e;
        }
    }

    private PreparedStatement _psAddOrganization;

    /**
     * Add an organization name to the sp_organization table
     *
     * @param org new organization to add (assume here that error checking has been done on org's
     *          values when instantiating org in the caller)
     * @throws ExAlreadyExist if the organization exists (i.e. a row exists in the organization
     * table with the same organization id)
     */
    @Override
    public synchronized void addOrganization(final Organization org)
            throws SQLException, ExAlreadyExist
    {
        try {
            if (_psAddOrganization == null) {
                _psAddOrganization = _c.prepareStatement("insert into " + T_ORGANIZATION + "(" +
                        C_ORG_ID + "," + C_ORG_NAME + "," + C_ORG_ALLOWED_DOMAIN + "," +
                        C_ORG_OPEN_SHARING + ") values (?,?,?,?)");
            }
            _psAddOrganization.setString(1, org._id);
            _psAddOrganization.setString(2, org._name);
            _psAddOrganization.setString(3, org._allowedDomain);
            _psAddOrganization.setBoolean(4, org._shareExternally);
            _psAddOrganization.executeUpdate();
        } catch (SQLException e) {
            // The following will throw ExAlreadyExist if the orgId already exists
            checkDuplicateKey(e);
            close(_psAddOrganization);
            _psAddOrganization = null;
            throw e;
        }
    }

    private PreparedStatement _psGetOrganization;

    /**
     * @return the Organization indexed by orgId
     * @throws ExNotFound if there is no row indexed by orgId
     */
    @Override
    public synchronized Organization getOrganization(final String orgId)
            throws SQLException, ExNotFound
    {
        try {
            if (_psGetOrganization == null) {
                _psGetOrganization = _c.prepareStatement("select " + C_ORG_NAME + ","
                        + C_ORG_ALLOWED_DOMAIN + "," + C_ORG_OPEN_SHARING + " from "
                        + T_ORGANIZATION + " where " + C_ORG_ID + "=?");
            }
            _psGetOrganization.setString(1, orgId);
            ResultSet rs = _psGetOrganization.executeQuery();
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
        } catch (SQLException e) {
            close(_psGetOrganization);
            _psGetOrganization = null;
            throw e;
        }
    }

    private PreparedStatement _psMoveToOrg;

    @Override
    public synchronized void moveUserToOrganization(String userId, String orgId)
            throws SQLException, ExNotFound
    {
        try {
            if (_psMoveToOrg == null) {
                _psMoveToOrg = _c.prepareStatement("update " + T_USER + " set " + C_USER_ORG_ID +
                    "=? where " + C_USER_ID + "=?");
            }
            _psMoveToOrg.setString(1, orgId);
            _psMoveToOrg.setString(2, userId);
            if (_psMoveToOrg.executeUpdate() == 0) { // 0 rows updated if user id doesn't exist
                throw new ExNotFound("User " + userId + " does not exist.");
            }
        } catch (SQLException e) {
            checkOrganizationKeyConstraint(e, orgId); // throws ExNotFound if orgId doesn't exist
            close(_psMoveToOrg);
            _psMoveToOrg = null;
            throw e;
        }
    }

    private void executeBatch(PreparedStatement ps, int batchSize,
            int expectedRowsAffectedPerBatchEntry, boolean assertOnBatchSizeMismatch)
            throws SQLException
    {
        int[] batchUpdates = ps.executeBatch();
        if (batchUpdates.length != batchSize) {
            String mismatch = "mismatch in batch size exp:" + batchSize + " act:" + batchUpdates;

            if (assertOnBatchSizeMismatch) {
                assert batchUpdates.length == batchSize : mismatch;
            } else {
                l.warn(mismatch);
            }
        }

        for (int rowsPerBatchEntry : batchUpdates) {
            if (rowsPerBatchEntry != expectedRowsAffectedPerBatchEntry) {
                String mismatch =  "unexpected number of affected rows " +
                    "exp:" + expectedRowsAffectedPerBatchEntry + " act:" + rowsPerBatchEntry;

                if (assertOnBatchSizeMismatch) {
                    assert rowsPerBatchEntry == expectedRowsAffectedPerBatchEntry : mismatch;
                } else {
                    l.warn(mismatch);
                }
            }
        }
    }

    private static void close(PreparedStatement ps)
    {
        try {
            if (ps != null /* && !ps.isClosed()*/) ps.close();
        } catch (SQLException e) {
            Util.l().warn("cannot close ps: " + e);
        }
    }
}
