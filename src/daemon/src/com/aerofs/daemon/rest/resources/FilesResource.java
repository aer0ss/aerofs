/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.event.EIFileInfo;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@javax.ws.rs.Path("/0/files/{path: [^?]+}")
@Produces(MediaType.APPLICATION_JSON)
public class FilesResource
{
    private final IIMCExecutor _imce;

    @Inject
    public FilesResource(CoreIMCExecutor imce)
    {
        _imce = imce.imce();
    }

    @GET
    public Response metadata(@PathParam("path") String path)
    {
        UserID user = null;
        return new EIFileInfo(_imce, user, path).execute();
    }

//    @DELETE
//    public Response delete(UserID user, @PathParam("path") String path)
//    {
//        return new EIDeleteFile(_imce, user, path).execute();
//    }
}

