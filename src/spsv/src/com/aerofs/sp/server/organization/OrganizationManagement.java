/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.organization;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.proto.Sp.ListSharedFoldersResponse.PBSharedFolder;
import com.aerofs.sp.server.lib.organization.IOrganizationDatabase;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.user.UserManagement;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Wrapper class for organization-related DB queries
 */
public class OrganizationManagement
{
    private static final int DEFAULT_MAX_RESULTS = 100;
    // To avoid DoS attacks forbid listSharedFolders queries exceeding 1000 returned results
    private static final int ABSOLUTE_MAX_RESULTS = 1000;

    private final IOrganizationDatabase _db;
    private final UserManagement _userManagement;

    private static final Logger l = Util.l(OrganizationManagement.class);

    public OrganizationManagement(IOrganizationDatabase db, UserManagement userManagement)
    {
        _db = db;
        _userManagement = userManagement;
    }

    private static Integer sanitizeMaxResults(Integer maxResults)
    {
        if (maxResults == null) return DEFAULT_MAX_RESULTS;
        else if (maxResults > ABSOLUTE_MAX_RESULTS) return ABSOLUTE_MAX_RESULTS;
        else if (maxResults < 0) return 0;

        else return maxResults;
    }

    public Organization addOrganization(String orgName)
            throws SQLException, ExAlreadyExist, ExBadArgs, ExNotFound
    {
        while (true) {
            // Use a random ID only to prevent competitors from figuring out total number of orgs.
            // It is NOT a security measure.
            OrgID orgId = new OrgID(Util.rand().nextInt());
            Organization org = new Organization(orgId, orgName);
            try {
                _db.addOrganization(org);
                l.info("Organization " + org + " created");
                return org;
            } catch (ExAlreadyExist e) {
                // Ideally we should use return value rather than exceptions on expected conditions
                l.info("Duplicate organization ID " + orgId + ". Try a new one.");
            }
        }
    }

    public Organization getOrganization(OrgID orgId)
            throws SQLException, ExNotFound
    {
        return _db.getOrganization(orgId);
    }

    public void setOrganizationPreferences(OrgID orgId, @Nullable String orgName)
            throws ExNotFound, SQLException, ExBadArgs
    {
        Organization oldOrg = getOrganization(orgId);

        if (orgName == null) orgName = oldOrg._name;

        Organization org = new Organization(orgId, orgName);

        _db.setOrganizationPreferences(org);
    }

    public void moveUserToOrganization(String user, OrgID orgId)
            throws SQLException, IOException, ExNotFound, ExBadArgs
    {
        User newUser = _userManagement.getUser(user);
        if (!newUser._orgID.equals(OrgID.DEFAULT)) {
            throw new ExBadArgs("User " + user + " already belongs to an organization.");
        }
        _db.moveUserToOrganization(user, orgId);
    }

    public List<PBSharedFolder> listSharedFolders(OrgID orgId, Integer maxResults, Integer offset)
            throws SQLException
    {
        if (offset == null) offset = 0;
        assert offset >= 0;

        return _db.listSharedFolders(orgId, sanitizeMaxResults(maxResults), offset);
    }

    public int countSharedFolders(OrgID orgId)
        throws SQLException
    {
        return _db.countSharedFolders(orgId);
    }
}
