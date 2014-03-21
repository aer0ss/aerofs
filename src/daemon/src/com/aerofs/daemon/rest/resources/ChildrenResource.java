/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.rest.util.AuthToken;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.oauth.Scope;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path(Service.VERSION + "/children")
@Produces(MediaType.APPLICATION_JSON)
public class ChildrenResource extends AbstractResource
{
    @Since("0.8")
    @GET
    public Response listUserRoot(@Auth AuthToken token)
    {
        // TODO: when files.read is restricted, list the accessible shared folders
        requirePermissionOnFolder(Scope.READ_FILES, token, SID.rootSID(token.user));
        return new EIListChildren(_imce, token, new RestObject(SID.rootSID(token.user), OID.ROOT))
                .execute();
    }

    @Since("0.9")
    @GET
    @Path("/{folder_id}")
    public Response list(@Auth AuthToken token,
            @PathParam("folder_id") RestObject object)
    {
        requirePermissionOnFolder(Scope.READ_FILES, token, object);
        return new EIListChildren(_imce, token, object).execute();
    }
}
