/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.auth.IAuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.sparta.Transactional;
import com.google.inject.Inject;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Path(Service.VERSION + "/stats")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class StatsResource extends AbstractSpartaResource
{
    private final Organization.Factory _factOrg;

    @Inject
    public StatsResource(Organization.Factory factOrg)
    {
        _factOrg = factOrg;
    }

    /**
     * Retrieves a count of Users in the org
     */
    @Since("1.4")
    @GET
    @Path("/users")
    public Response users(@Auth IAuthToken token)
            throws SQLException, ExNotFound
    {
        requirePermission(Scope.ORG_ADMIN, token);
        Organization org = _factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);

        Map<String, Integer> usersMap = new HashMap<>();
        usersMap.put("local", org.countLocalUsers());
        usersMap.put("ldap", org.countLdapUsers());
        usersMap.put("total", org.countUsers());

        return Response.ok()
                .entity(usersMap)
                .build();
    }

    /**
     * Retrieves a count of the number of devices in the org by device type
     */
    @Since("1.4")
    @GET
    @Path("/devices")
    public Response devices(@Auth IAuthToken token)
            throws ExNotFound, SQLException
    {
        requirePermission(Scope.ORG_ADMIN, token);
        Organization org = _factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);

        Map<String, Integer> devicesMap = new HashMap<>();
        devicesMap.put("windows", org.countClientDevicesByOS("Windows"));
        devicesMap.put("mac", org.countClientDevicesByOS("Mac OS X"));
        devicesMap.put("linux", org.countClientDevicesByOS("Linux"));
        devicesMap.put("ios", org.countMobileDevicesByOS("iOS"));
        devicesMap.put("android", org.countMobileDevicesByOS("Android"));
        devicesMap.put("team_servers", org.countTeamServers());

        return Response.ok()
                .entity(devicesMap)
                .build();
    }

    /**
     * Retrieves a count of total number of groups in the org
     */
    @Since("1.4")
    @GET
    @Path("/groups")
    public Response groups(@Auth IAuthToken token)
            throws SQLException, ExNotFound
    {
        requirePermission(Scope.ORG_ADMIN, token);
        Organization org = _factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);

        Map<String, Integer> groupsMap = new HashMap<>();
        groupsMap.put("total", org.countGroups());

        return Response.ok()
                .entity(groupsMap)
                .build();
    }

    /**
     * Retrieves a count of the total number of shared folders in the org
     */
    @Since("1.4")
    @GET
    @Path("/shares")
    public Response shares(@Auth IAuthToken token)
            throws SQLException, ExNotFound
    {
        requirePermission(Scope.ORG_ADMIN, token);
        Organization org = _factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);

        Map<String, Integer> sharesMap = new HashMap<>();
        sharesMap.put("total", org.countSharedFolders());

        return Response.ok()
                .entity(sharesMap)
                .build();
    }
}
