/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp.organization;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.proto.Sp.ListSharedFoldersResponse.PBSharedFolder;
import com.aerofs.sp.server.sp.user.UserManagement;
import com.aerofs.srvlib.sp.organization.IOrganizationDatabase;
import com.aerofs.srvlib.sp.organization.Organization;
import com.aerofs.srvlib.sp.user.AuthorizationLevel;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wrapper class for organization-related DB queries
 */
public class OrganizationManagement
{
    private final IOrganizationDatabase _db;
    private final UserManagement _userManagement;

    private static final Logger l = Util.l(OrganizationManagement.class);

    public OrganizationManagement(IOrganizationDatabase db, UserManagement userManagement)
    {
        _db = db;
        _userManagement = userManagement;
    }

    // Regex taken from http://myregexp.com/examples.html and modified to allow longer TLDs
    private static final Pattern DOMAIN_NAME_PATTERN =
            Pattern.compile("^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$");

    private static boolean isValidDomainName(String domainName)
    {
        Matcher matcher = DOMAIN_NAME_PATTERN.matcher(domainName);
        return matcher.find();
    }

    /**
     * TODO: Make this method transactional
     */
    public void addOrganization(String orgId, String orgName, Boolean shareExternal,
            @Nullable String allowedDomain, String orgAdminId)
            throws SQLException, ExAlreadyExist, ExBadArgs, ExNotFound
    {
        if (allowedDomain == null || allowedDomain.isEmpty()) {
            allowedDomain = Organization.ANY_DOMAIN;
        } else if (!isValidDomainName(allowedDomain)) {
            throw new ExBadArgs("Domain '" + allowedDomain + "' is not a valid domain name.");
        }

        if (!isValidDomainName(orgId)) {
            throw new ExBadArgs("Organization ID '" + orgId + "' is not a valid domain name.");
        }

        // TODO: the check below is redundant, but currently necessary because this method is not
        // transactional. Once there is a _db.deleteOrganization(), replace the block at the end
        // of the method with:
        // try {
        //     _db.addOrganization(newOrg);
        //     moveUserToOrganization(orgAdminId, orgId);
        // } catch (ExBadArgs e) {
        //     l.info("bad user args");
        //     _db.removeOrganization(newOrg);
        //     throw e;
        // }
        Organization newOrg = new Organization(orgId, orgName, allowedDomain, shareExternal);
        if (!newOrg.domainMatches(orgAdminId)) {
            throw new ExBadArgs("User id of user creating organization (" + orgAdminId + ") does " +
                    "not match allowed domain of new organization.");
        }

        l.info("Creating organization '" + orgId + "' with name '" + orgName + "', " +
                "external sharing " + (shareExternal ? "on": "off") + ", and allowedDomain '" +
                allowedDomain + "'.");

        _db.addOrganization(newOrg);
        moveUserToOrganization(orgAdminId, orgId);
        _userManagement.setAuthorizationLevel(orgAdminId, AuthorizationLevel.ADMIN);
    }

    public Organization getOrganization(String orgId)
            throws SQLException, ExNotFound
    {
        return _db.getOrganization(orgId);
    }

    public void moveUserToOrganization(String user, String orgId)
            throws SQLException, ExNotFound, ExBadArgs
    {
        Organization org = getOrganization(orgId);
        if (!org.domainMatches(user)) {
            throw new ExBadArgs("User id " + user + " does not match allowed domain of organization '"
                    + org._name + "'.");
        }
        _db.moveUserToOrganization(user, orgId);
    }

    public List<PBSharedFolder> listSharedFolders(String orgId, int maxResults, int offset)
            throws SQLException
    {
        return _db.listSharedFolders(orgId, maxResults, offset);
    }

    public int countSharedFolders(String orgId)
        throws SQLException
    {
        return _db.countSharedFolders(orgId);
    }
}
