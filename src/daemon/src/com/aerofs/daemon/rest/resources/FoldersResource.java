/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.Version;
import com.aerofs.daemon.rest.event.EICreateObject;
import com.aerofs.daemon.rest.event.EIDeleteObject;
import com.aerofs.daemon.rest.event.EIMoveObject;
import com.aerofs.daemon.rest.event.EIObjectInfo;
import com.aerofs.daemon.rest.event.EIObjectInfo.Type;
import com.aerofs.rest.util.AuthToken;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.rest.api.Folder;
import com.aerofs.rest.util.AuthToken.Scope;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.util.EntityTagSet;
import com.google.common.net.HttpHeaders;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;

@Path(Service.VERSION + "/folders")
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM})
public class FoldersResource extends AbstractResource
{
    @Since("0.9")
    @GET
    @Path("/{folder_id}")
    public Response metadata(@Auth AuthToken token,
            @PathParam("folder_id") RestObject object)
    {
        requirePermissionOnFolder(Scope.READ_FILES, token, object);
        return new EIObjectInfo(_imce, token, object, Type.FOLDER).execute();
    }

    @Since("0.10")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Auth AuthToken token,
            @Context Version version,
            Folder folder) throws IOException
    {
        checkArgument(folder.parent != null);
        checkArgument(folder.name != null);
        requirePermissionOnFolder(Scope.WRITE_FILES, token, new RestObject(folder.parent));
        return new EICreateObject(_imce, token, version, folder.parent, folder.name, true)
                .execute();
    }

    @Since("0.10")
    @PUT
    @Path("/{folder_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response move(@Auth AuthToken token,
            @PathParam("folder_id") RestObject object,
            @HeaderParam(HttpHeaders.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            Folder folder) throws IOException
    {
        checkArgument(folder.parent != null);
        checkArgument(folder.name != null);
        requirePermissionOnFolder(Scope.WRITE_FILES, token, object);
        requirePermissionOnFolder(Scope.WRITE_FILES, token, new RestObject(folder.parent));
        return new EIMoveObject(_imce, token, object, folder.parent, folder.name, ifMatch)
                .execute();
    }

    @Since("0.10")
    @DELETE
    @Path("/{folder_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response delete(@Auth AuthToken token,
            @PathParam("folder_id") RestObject object,
            @HeaderParam(HttpHeaders.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch)
            throws IOException
    {
        requirePermissionOnFolder(Scope.WRITE_FILES, token, object);
        return new EIDeleteObject(_imce, token, object, ifMatch).execute();
    }
}
