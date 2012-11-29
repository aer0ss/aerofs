/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.organization;

import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.id.SID;

import java.sql.SQLException;
import java.util.List;

/**
 * TODO (WW) merge functions from this interface to OrganizationDatabase and remove this interface
 *
 * This interface defines database methods necessary for organization management functionality.
 * Implementers that want to provide organization management services must implement all of these
 * methods.
 */
public interface IOrganizationDatabase
{
    /**
     * Counts the total number of shared folders viewable by calls to listSharedFolders
     * @param orgId organization
     */
    int countSharedFolders(OrgID orgId)
            throws SQLException;

    // TODO (WW) make this class an active class, similar to User and Organization
    public static class SharedFolder
    {
        public final SID _sid;
        public final String _name;
        public final List<SubjectRolePair> _acl;

        public SharedFolder(SID sid, String name, List<SubjectRolePair> acl)
        {
            _sid = sid;
            _name = name;
            _acl = acl;
        }
    }

    /**
     * Returns a list of the folders being shared by members of the given organization. To support
     * paging, it takes an offset into the list and a maximum length for the returned sub-list.
     * @param orgId the organization being queried for shared folders
     * @param maxResults the maximum length of the returned list (for paging)
     * @param offset offset into the list of all shared folders to return from
     * @return a list of shared folders for the given orgId beginning at the given offset
     */
    List<SharedFolder> listSharedFolders(OrgID orgId, int maxResults, int offset)
            throws SQLException;
}
