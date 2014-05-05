/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.rest.util.AuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.sparta.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.sql.SQLException;

@Path(Service.VERSION + "/organizations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class OrganizationsResource extends AbstractSpartaResource
{
    private static Logger l = LoggerFactory.getLogger(OrganizationsResource.class);

    @Since("1.2")
    @GET
    @Path("/{orgid}")
    public Response get(@Auth AuthToken token, @PathParam("orgid") Organization org)
            throws SQLException, ExNotFound
    {
        if (!token.user().equals(org.getTeamServerUser().id())) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok()
                .entity(new com.aerofs.rest.api.Organization(
                        org.id().toHexString(),
                        org.getName(),
                        org.getQuotaPerUser()))
                .build();
    }
}
