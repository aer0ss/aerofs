/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.RestService;
import com.aerofs.daemon.rest.event.EIFolderInfo;
import com.aerofs.daemon.rest.jersey.RestObjectParam;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.restless.Auth;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path(RestService.VERSION + "/folders/{object}")
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM})
public class FoldersResource
{
    private final IIMCExecutor _imce;

    @Inject
    public FoldersResource(CoreIMCExecutor imce)
    {
        _imce = imce.imce();
    }

    @GET
    public Response metadata(@Auth AuthenticatedPrincipal principal,
            @PathParam("object") RestObjectParam object)
    {
        UserID userid = principal.getUserID();
        return new EIFolderInfo(_imce, userid, object.get()).execute();
    }
}
