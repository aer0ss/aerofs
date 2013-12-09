/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.daemon.rest.event.EIObjectInfo;
import com.aerofs.daemon.rest.event.EIObjectInfo.Type;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.google.inject.Inject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path(Service.VERSION + "/folders")
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM})
public class FoldersResource
{
    private final IIMCExecutor _imce;

    @Inject
    public FoldersResource(CoreIMCExecutor imce)
    {
        _imce = imce.imce();
    }

    @Since("0.9")
    @GET
    @Path("/{folder_id}")
    public Response metadata(@Auth AuthenticatedPrincipal principal,
            @PathParam("folder_id") RestObject object)
    {
        UserID userid = principal.getUserID();
        return new EIObjectInfo(_imce, userid, object, Type.FOLDER).execute();
    }
}
