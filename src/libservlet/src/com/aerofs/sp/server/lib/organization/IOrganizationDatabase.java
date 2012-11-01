/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.organization;

import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.proto.Sp.ListSharedFoldersResponse.PBSharedFolder;

import java.sql.SQLException;
import java.util.List;

/**
 * This interface defines database methods necessary for organization management functionality.
 * Implementers that want to provide organization management services must implement all of these
 * methods.
 */
public interface IOrganizationDatabase
{
    /**
     * Add a new organization to the sp_organization table
     *
     * @param org new organization to add (assume here that error checking has been done on org's
     *          values when instantiating org in the caller)
     * @throws ExAlreadyExist if the organization exists (i.e. a row exists in the organization
     * table with the same organization id)
     */
    void addOrganization(final Organization org)
            throws SQLException, ExAlreadyExist, ExBadArgs;

    /**
     * @return the Organization indexed by orgId
     * @throws ExNotFound if there is no row indexed by orgId
     */
    Organization getOrganization(final String orgId)
            throws SQLException, ExNotFound;

    /**
     * Sets the preferences for the given organization. Assumes that newOrg._id references a
     * valid and preexisting organization (created by addOrganization above), and updates the
     * organization referenced by that id with the rest of the values set in newOrg.
     * @param newOrg An updated organization object
     * @throws ExNotFound if there is no organization with the organization id in newOrg
     */
    void setOrganizationPreferences(final Organization newOrg)
            throws SQLException, ExNotFound;

    /**
     * Moves the given user into the given organization
     * @throws ExNotFound if the given userId or orgId don't exist
     */
    void moveUserToOrganization(String userId, String orgId)
            throws SQLException, ExNotFound;


    /**
     * Counts the total number of shared folders viewable by calls to listSharedFolders
     * @param orgId organization
     */
    int countSharedFolders(String orgId)
            throws SQLException;

    /**
     * Returns a list of the folders being shared by members of the given organization. To support
     * paging, it takes an offset into the list and a maximum length for the returned sub-list.
     * @param orgId the organization being queried for shared folders
     * @param maxResults the maximum length of the returned list (for paging)
     * @param offset offset into the list of all shared folders to return from
     * @return a list of shared folders for the given orgId beginning at the given offset
     */
    List<PBSharedFolder> listSharedFolders(String orgId, int maxResults, int offset)
            throws SQLException;
}
