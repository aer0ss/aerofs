/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.UserID;
import com.aerofs.servlets.lib.db.AbstractSQLDatabase;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_ORG_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_ORG_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_AUTHORIZATION_LEVEL;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_FIRST_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_LAST_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ORG_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_ORG;
import static com.aerofs.sp.server.lib.SPSchema.T_USER;

/**
 * N.B. only User.java may refer to this class
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
    public void addOrganization(OrgID orgID, String name)
            throws SQLException, ExAlreadyExist
    {
        try {
            PreparedStatement ps = prepareStatement(DBUtil.insert(T_ORG, C_ORG_ID, C_ORG_NAME));

            ps.setInt(1, orgID.getInt());
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException aue) {
            throwOnConstraintViolation(aue);
        }
    }

    public @Nonnull String getName(OrgID orgID)
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
    public void setName(OrgID orgID, String name)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_ORG, C_ORG_ID + "=?", C_ORG_NAME));

        ps.setInt(1, orgID.getInt());
        ps.setString(2, name);

        Util.verify(ps.executeUpdate() == 1);
    }

    private ResultSet queryOrg(OrgID orgID, String... fields)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_ORG, C_ORG_ID + "=?", fields));
        ps.setString(1, orgID.toString());
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            throw new ExNotFound("org #" + orgID);
        } else {
            return rs;
        }
    }

    // TODO (WW) use User class
    public static class UserInfo
    {
        public final UserID _userId;
        public final String _firstName;
        public final String _lastName;

        public UserInfo(UserID userId, String firstName, String lastName)
        {
            _userId = userId;
            _firstName = firstName;
            _lastName = lastName;
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

    /**
     * @param orgId ID of the organization.
     * @param offset Starting index of the results list from the database.
     * @param maxResults Maximum number of results returned from the database.
     * @return List of users under the organization {@code orgId}
     * between [offset, offset + maxResults].
     */
    public List<UserInfo> listUsers(OrgID orgId, int offset, int maxResults)
            throws SQLException
    {
        PreparedStatement psLU = prepareStatement(
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

    /**
     * @param orgId ID of the organization.
     * @param offset Starting index of the results list from the database.
     * @param maxResults Maximum number of results returned from the database.
     * @param search Search term that we want to match in user id.
     * @return List of users that contains the {@code search} term as part of their user IDs.
     * The users are under the organization {@code orgId}, and the list is between
     * [offset, offset + maxResults].
     */
    public List<UserInfo> searchUsers(OrgID orgId, int offset, int maxResults, String search)
            throws SQLException
    {
        PreparedStatement psSLU = prepareStatement("select " + C_USER_ID + "," +
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

    /**
     * @param orgId ID of the organization.
     * @param offset Starting index of the results list from the database.
     * @param maxResults Maximum number of results returned from the database.
     * @param authLevel Authorization level of the users.
     * @return List of users with the given authorization level {@code authLevel} under
     * the organization {@code orgId} between [offset, offset + maxResults].
     */
    public List<UserInfo> listUsersWithAuthorization(OrgID orgId, int offset, int maxResults,
            AuthorizationLevel authLevel)
            throws SQLException
    {
        PreparedStatement psLUA = prepareStatement(
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
    public List<UserInfo> searchUsersWithAuthorization(OrgID orgId, int offset,
            int maxResults, AuthorizationLevel authLevel, String search)
            throws SQLException
    {
        PreparedStatement psSUA = prepareStatement(
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

    /**
     * @param orgId ID of the organization.
     * @return Number of users in the organization {@code orgId}.
     */
    public int listUsersCount(OrgID orgId)
            throws SQLException
    {
        PreparedStatement psLUC = prepareStatement("select count(*) from " +
                T_USER + " where " + C_USER_ORG_ID + "=?");

        psLUC.setInt(1, orgId.getInt());
        ResultSet rs = psLUC.executeQuery();
        try {
            return countResultSet2Int(rs);
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
    public int searchUsersCount(OrgID orgId, String search)
            throws SQLException
    {
        PreparedStatement psSCU = prepareStatement("select count(*) from " +
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

    /**
     * @param authlevel Authorization level of the users.
     * @param orgId ID of the organization.
     * @return Number of users in the organization with the given authorization level.
     */
    public int listUsersWithAuthorizationCount(AuthorizationLevel authlevel, OrgID orgId)
            throws SQLException
    {
        PreparedStatement psLUAC = prepareStatement("select count(*) from " +
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

    /**
     * @param authLevel Authorization level of the users.
     * @param orgId ID of the organization.
     * @param search Search term that we want to match in user id.
     * @return Number of users in the organization {@code orgId} with user ids
     * containing the search term {@code search} and authorization level {@code authLevel}.
     */
    public int searchUsersWithAuthorizationCount(AuthorizationLevel authLevel,
            OrgID orgId, String search)
            throws SQLException
    {
        PreparedStatement psSUAC = prepareStatement(
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
}
