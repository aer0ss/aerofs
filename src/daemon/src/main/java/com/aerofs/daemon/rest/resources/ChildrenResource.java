/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.rest.auth.OAuthToken;
import com.aerofs.base.id.RestObject;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

// NB: deprecated in 1.2
@Path(Service.VERSION + "/children")
@Produces(MediaType.APPLICATION_JSON)
public class ChildrenResource extends AbstractResource
{
    @Since("0.8")
    @GET
    public Response listUserRoot(@Auth OAuthToken token)
    {
        return list(token, new RestObject(SID.rootSID(token.user()), OID.ROOT));
    }

    @Since("0.9")
    @GET
    @Path("/{folder_id}")
    public Response list(@Auth OAuthToken token,
            @PathParam("folder_id") RestObject object)
    {
        return new EIListChildren(_imce, token, object, true).execute();
    }
}
