/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp.organization;

import com.aerofs.lib.C;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.proto.Sp.ListSharedFoldersResponse.PBSharedFolder;
import com.aerofs.servletlib.sp.organization.IOrganizationDatabase;
import com.aerofs.servletlib.sp.organization.Organization;
import com.aerofs.servletlib.sp.user.User;
import com.aerofs.sp.server.sp.user.UserManagement;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // Regex taken from http://myregexp.com/examples.html and modified to allow longer TLDs
    private static final Pattern DOMAIN_NAME_PATTERN =
            Pattern.compile("^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$");

    private static boolean isValidDomainName(String domainName)
    {
        Matcher matcher = DOMAIN_NAME_PATTERN.matcher(domainName);
        return matcher.find();
    }

    private static Integer sanitizeMaxResults(Integer maxResults)
    {
        if (maxResults == null) return DEFAULT_MAX_RESULTS;
        else if (maxResults > ABSOLUTE_MAX_RESULTS) return ABSOLUTE_MAX_RESULTS;
        else if (maxResults < 0) return 0;

        else return maxResults;
    }

    /**
     * TODO: Make this method transactional
     */
    public void addOrganization(String orgId, String orgName, Boolean shareExternal,
            @Nullable String allowedDomain)
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

        l.info("Creating organization '" + orgId + "' with name '" + orgName + "', " +
                "external sharing " + (shareExternal ? "on": "off") + ", and allowedDomain '" +
                allowedDomain + "'.");

        Organization newOrg = new Organization(orgId, orgName, allowedDomain, shareExternal);

        _db.addOrganization(newOrg);
    }

    public Organization getOrganization(String orgId)
            throws SQLException, ExNotFound
    {
        return _db.getOrganization(orgId);
    }

    public void setOrganizationPreferences(String orgId, @Nullable String orgName,
            @Nullable String allowedDomain, @Nullable Boolean shareExternal)
            throws ExNotFound, SQLException, ExBadArgs
    {
        Organization oldOrg = getOrganization(orgId);

        if (orgName == null) {
            orgName = oldOrg._name;
        }

        if (allowedDomain == null) {
            allowedDomain = oldOrg._allowedDomain;
        } else if (allowedDomain.isEmpty()) {
            allowedDomain = Organization.ANY_DOMAIN;
        } else if (!allowedDomain.equals(Organization.ANY_DOMAIN) &&
                !isValidDomainName(allowedDomain)) {
            throw new ExBadArgs("Domain '" + allowedDomain + "' is not a valid domain name.");
        }

        if (shareExternal == null) {
            shareExternal = oldOrg._shareExternally;
        }

        Organization newOrg = new Organization(orgId, orgName, allowedDomain, shareExternal);

        _db.setOrganizationPreferences(newOrg);
    }

    public void moveUserToOrganization(String user, String orgId)
            throws SQLException, IOException, ExNotFound, ExBadArgs
    {
        User newUser = _userManagement.getUser(user);
        if (!newUser._orgId.equals(C.DEFAULT_ORGANIZATION)) {
            throw new ExBadArgs("User " + user + " already belongs to an organization.");
        }
        _db.moveUserToOrganization(user, orgId);
    }

    public List<PBSharedFolder> listSharedFolders(String orgId, Integer maxResults, Integer offset)
            throws SQLException
    {
        if (offset == null) offset = 0;
        assert offset >= 0;

        return _db.listSharedFolders(orgId, sanitizeMaxResults(maxResults), offset);
    }

    public int countSharedFolders(String orgId)
        throws SQLException
    {
        return _db.countSharedFolders(orgId);
    }
}
