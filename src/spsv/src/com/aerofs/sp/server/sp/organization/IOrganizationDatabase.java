/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp.organization;

import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.proto.Sp.ListSharedFoldersResponse.PBSharedFolder;

import java.sql.SQLException;
import java.util.List;

/**
 * Interface for Organization-related database methods
 */
public interface IOrganizationDatabase
{
    void addOrganization(final Organization org)
            throws SQLException, ExAlreadyExist, ExBadArgs;
    Organization getOrganization(final String orgId)
            throws SQLException, ExNotFound;
    void moveUserToOrganization(String userId, String orgId)
            throws SQLException, ExNotFound;

    int countSharedFolders(String orgId)
            throws SQLException;
    List<PBSharedFolder> listSharedFolders(String orgId, int maxResults, int offset)
            throws SQLException;
}
