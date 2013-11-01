/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.RestObject;
import com.aerofs.daemon.rest.RestService;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.daemon.rest.jersey.RestObjectParam;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.restless.Auth;
import com.google.inject.Inject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path(RestService.VERSION + "/children")
@Produces(MediaType.APPLICATION_JSON)
public class ChildrenResource
{
    private final IIMCExecutor _imce;

    @Inject
    public ChildrenResource(CoreIMCExecutor imce)
    {
        _imce = imce.imce();
    }

    @GET
    public Response listUserRoot(@Auth AuthenticatedPrincipal principal)
    {
        UserID userid = principal.getUserID();
        return new EIListChildren(_imce, userid, new RestObject(SID.rootSID(userid), OID.ROOT))
                .execute();
    }

    @GET
    @Path("/{object}")
    public Response list(@Auth AuthenticatedPrincipal principal,
            @PathParam("object") RestObjectParam object)
    {
        UserID userid = principal.getUserID();
        return new EIListChildren(_imce, userid, object.get())
                .execute();
    }
}
