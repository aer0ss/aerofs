/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.Util;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.AbstractSQLDatabase;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.sp.server.lib.organization.OrganizationID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import static com.aerofs.lib.db.DBUtil.count;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_ROLE;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STORE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_ORG_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_ORG_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_AUTHORIZATION_LEVEL;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ORG_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_AC;
import static com.aerofs.sp.server.lib.SPSchema.T_ORG;
import static com.aerofs.sp.server.lib.SPSchema.T_SF;
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
    public void add(OrganizationID orgID, String name)
            throws SQLException, ExAlreadyExist
    {
        try {
            PreparedStatement ps = prepareStatement(DBUtil.insert(T_ORG, C_ORG_ID, C_ORG_NAME));

            ps.setInt(1, orgID.getInt());
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throwOnConstraintViolation(e);
            throw e;
        }
    }

    public @Nonnull String getName(OrganizationID orgID)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryOrg(orgID, C_ORG_NAME);
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
        PreparedStatement ps = prepareStatement(updateWhere(T_ORG, C_ORG_ID + "=?", C_ORG_NAME));

        ps.setString(1, name);
        ps.setInt(2, orgID.getInt());

        Util.verify(ps.executeUpdate() == 1);
    }

    private ResultSet queryOrg(OrganizationID orgID, String field)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_ORG, C_ORG_ID + "=?", field));
        ps.setInt(1, orgID.getInt());
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            throw new ExNotFound("org " + orgID);
        } else {
            return rs;
        }
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

    private static String andNotTeamServer()
    {
        return " and " + C_USER_ID + " not like ':%' ";
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
                " where " + C_USER_ORG_ID + "=? and " + C_USER_ID + " like ? " + andNotTeamServer() +
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
    public int listUsersCount(OrganizationID orgId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement("select count(*) from " +
                T_USER + " where " + C_USER_ORG_ID + "=?" + andNotTeamServer());

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

    // TODO (WW) refactor the entire logic of listing shared folders. Use SharedFolder objects
    // instead.
    public static class SharedFolderInfo
    {
        public final SID _sid;
        public final String _name;
        public final List<SubjectRolePair> _acl;

        public SharedFolderInfo(SID sid, String name, List<SubjectRolePair> acl)
        {
            _sid = sid;
            _name = name;
            _acl = acl;
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
     *
     * TODO (WW) the query is too complicated. This indicates misdesign at the application layer.
     */
    private static String sidListQuery() {
        return  "select t1." + C_AC_STORE_ID + " from (" +
                "select " + C_AC_STORE_ID + " from " + T_AC + " join " + T_USER
                + " on " + C_AC_USER_ID + "=" + C_USER_ID + " where " +
                C_USER_ORG_ID + "=?" +
                ") as t1 join " + T_AC + " as t2 on t1." + C_AC_STORE_ID + "=" +
                "t2." + C_AC_STORE_ID;
    }

    /**
     * Returns a list of the folders being shared by members of the given organization. To support
     * paging, it takes an offset into the list and a maximum length for the returned sub-list.
     * @param orgId the organization being queried for shared folders
     * @param maxResults the maximum length of the returned list (for paging)
     * @param offset offset into the list of all shared folders to return from
     * @return a list of shared folders for the given orgId beginning at the given offset
     *
     * TODO (WW) the query is too complicated. This indicates misdesign at the application layer.
     */
    public Collection<SharedFolderInfo> listSharedFolders(OrganizationID orgId, int maxResults, int offset)
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

        List<SharedFolderInfo> sfis = Lists.newLinkedList();
        try {
            // iterate through rows in db response, squashing rows with the same store id
            // into single PBSharedFolder objects to be returned

            SID curStoreId = null;
            SharedFolderInfo sfi = null;

            while (rs.next()) {
                SID storeId = new SID(rs.getBytes(1));
                String storeName = "(name not currently saved)";
                List<SubjectRolePair> acl = Lists.newArrayList();
                if (!storeId.equals(curStoreId)) {
                    if (curStoreId != null) {
                        sfis.add(sfi);
                    }
                    curStoreId = storeId;

                    String name = rs.getString(2);
                    if (name != null) storeName = name;
                }

                UserID userId = UserID.fromInternal(rs.getString(3));
                Role role = Role.fromOrdinal(rs.getInt(4));
                acl.add(new SubjectRolePair(userId, role));

                sfi = new SharedFolderInfo(storeId, storeName, acl);
            }
            if (curStoreId != null) {
                sfis.add(sfi); // add final shared folder
            }
        } finally {
            rs.close();
        }
        return sfis;
    }

    public int countSharedFolders(OrganizationID orgId)
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
}
