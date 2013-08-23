/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.event.EIDeleteFolder;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.google.inject.Inject;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@javax.ws.rs.Path("/0/folders/{path: [^?]*}")
@Produces(MediaType.APPLICATION_JSON)
public class FoldersResource
{
    private final IIMCExecutor _imce;

    @Inject
    public FoldersResource(CoreIMCExecutor imce)
    {
        _imce = imce.imce();
    }

    @GET
    public Response list(/*UserID user, */@PathParam("path") String path)
    {
        UserID user = UserID.fromInternal("greg+3@aerofs.com");
        return new EIListChildren(_imce, user, path).execute();
    }

    @DELETE
    public Response delete(UserID user, @PathParam("path") String path,
            @QueryParam("recurse") @DefaultValue("false") /*BooleanParam*/ Boolean recurse)
    {
        return new EIDeleteFolder(_imce, user, path, recurse).execute();
    }
}
