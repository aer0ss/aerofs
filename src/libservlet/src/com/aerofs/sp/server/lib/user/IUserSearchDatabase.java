/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.user;

import com.aerofs.proto.Sp.PBUser;
import com.aerofs.sp.server.lib.organization.OrgID;

import java.sql.SQLException;
import java.util.List;

/**
 * This is a first attempt at interfacing part of the user database.
 * It is currently unclear which methods should belong to a hypothetical IUserDatabase, so for now,
 * this interface specializes in listing or searching users that meet a certain criteria.
 */
public interface IUserSearchDatabase
{
    /**
     * @param orgId ID of the organization.
     * @param offset Starting index of the results list from the database.
     * @param maxResults Maximum number of results returned from the database.
     * @return List of users under the organization {@code orgId}
     * between [offset, offset + maxResults].
     */
    List<PBUser> listUsers(OrgID orgId, int offset, int maxResults)
            throws SQLException;

    /**
     * @param orgId ID of the organization.
     * @param offset Starting index of the results list from the database.
     * @param maxResults Maximum number of results returned from the database.
     * @param search Search term that we want to match in user id.
     * @return List of users that contains the {@code search} term as part of their user IDs.
     * The users are under the organization {@code orgId}, and the list is between
     * [offset, offset + maxResults].
     */
    List<PBUser> searchUsers(OrgID orgId, int offset, int maxResults, String search)
            throws SQLException;

    /**
     * @param orgId ID of the organization.
     * @param offset Starting index of the results list from the database.
     * @param maxResults Maximum number of results returned from the database.
     * @param authLevel Authorization level of the users.
     * @return List of users with the given authorization level {@code authLevel} under
     * the organization {@code orgId} between [offset, offset + maxResults].
     */
    List<PBUser> listUsersWithAuthorization(OrgID orgId, int offset, int maxResults,
            AuthorizationLevel authLevel)
            throws SQLException;


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
    List<PBUser> searchUsersWithAuthorization(OrgID orgId, int offset, int maxResults,
            AuthorizationLevel authLevel, String search)
            throws SQLException;

    /**
     * @param orgId ID of the organization.
     * @return Number of users in the organization {@code orgId}.
     */
    int listUsersCount(OrgID orgId) throws SQLException;

    /**
     * @param orgId ID of the organization.
     * @param search Search term that we want to match in user id.
     * @return Number of users in the organization {@code orgId}
     * with user ids containing the search term {@code search}.
     */
    int searchUsersCount(OrgID orgId, String search) throws SQLException;

    /**
     * @param authlevel Authorization level of the users.
     * @param orgId ID of the organization.
     * @return Number of users in the organization with the given authorization level.
     */
    int listUsersWithAuthorizationCount(AuthorizationLevel authlevel, OrgID orgId)
            throws SQLException;

    /**
     * @param authLevel Authorization level of the users.
     * @param orgId ID of the organization.
     * @param search Search term that we want to match in user id.
     * @return Number of users in the organization {@code orgId} with user ids
     * containing the search term {@code search} and authorization level {@code authLevel}.
     */
    int searchUsersWithAuthorizationCount(AuthorizationLevel authLevel, OrgID orgId, String search)
            throws SQLException;
}
