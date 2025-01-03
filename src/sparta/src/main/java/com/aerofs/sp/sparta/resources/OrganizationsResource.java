/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.UniqueID;
import com.aerofs.rest.auth.IAuthToken;
import com.aerofs.rest.auth.IUserAuthToken;
import com.aerofs.rest.auth.PrivilegedServiceToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.sparta.Transactional;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.sql.SQLException;
import java.util.Collections;

@Path(Service.VERSION + "/organizations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class OrganizationsResource extends AbstractSpartaResource
{
    @Since("1.2")
    @GET
    @Path("/{orgid}")
    public Response get(@Auth IAuthToken token, @PathParam("orgid") Organization org)
            throws SQLException, ExNotFound
    {
        if (token instanceof IUserAuthToken
                && !((IUserAuthToken)token).user().equals(org.getTeamServerUser().id())) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok()
                .entity(new com.aerofs.rest.api.Organization(
                        org.id().toHexString(),
                        org.getName(),
                        org.getQuotaPerUser()))
                .build();
    }

    @Since("1.4")
    @POST
    @Path("/{orgid}/storage_agent")
    public Response makeStorageAgent(@Auth PrivilegedServiceToken token, @PathParam("orgid") Organization org)
            throws SQLException, ExAlreadyExist
    {
        UniqueID t = org.generateStorageAgentToken();
        return Response.ok()
                .entity(Collections.singletonMap("token", t.toStringFormal()))
                .build();
    }
}
