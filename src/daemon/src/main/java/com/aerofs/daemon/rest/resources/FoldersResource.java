/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.restless.Version;
import com.aerofs.daemon.rest.event.EICreateObject;
import com.aerofs.daemon.rest.event.EIDeleteObject;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.daemon.rest.event.EIMoveObject;
import com.aerofs.daemon.rest.event.EIObjectInfo;
import com.aerofs.daemon.rest.event.EIObjectInfo.Type;
import com.aerofs.daemon.rest.event.EIObjectPath;
import com.aerofs.daemon.rest.util.Fields;
import com.aerofs.rest.auth.OAuthToken;
import com.aerofs.base.id.RestObject;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Folder;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;

@Path(Service.VERSION + "/folders")
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM})
public class FoldersResource extends AbstractResource
{
    @Since("0.9")
    @GET
    @Path("/{folder_id}")
    public Response metadata(@Auth OAuthToken token,
            @Context Version version,
            @PathParam("folder_id") RestObject object,
            @QueryParam("fields") Fields fields)
    {
        return new EIObjectInfo(_imce, token, object, Type.FOLDER,
                version.compareTo(new Version(1, 2)) < 0 ? null : fields)
                .execute();
    }

    @Since("0.10")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Auth OAuthToken token,
            @Context Version version,
            Folder folder) throws IOException
    {
        checkArgument(folder.parent != null);
        checkArgument(folder.name != null);
        return new EICreateObject(_imce, token, version, folder.parent, folder.name, true)
                .execute();
    }

    @Since("0.10")
    @PUT
    @Path("/{folder_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response move(@Auth OAuthToken token,
            @PathParam("folder_id") RestObject object,
            @HeaderParam(HttpHeaders.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            Folder folder) throws IOException
    {
        checkArgument(folder.parent != null);
        checkArgument(folder.name != null);
        return new EIMoveObject(_imce, token, object, folder.parent, folder.name, ifMatch)
                .execute();
    }

    @Since("0.10")
    @DELETE
    @Path("/{folder_id}")
    public Response delete(@Auth OAuthToken token,
            @PathParam("folder_id") RestObject object,
            @HeaderParam(HttpHeaders.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch)
            throws IOException
    {
        return new EIDeleteObject(_imce, token, object, ifMatch).execute();
    }

    @Since("1.2")
    @GET
    @Path("/{folder_id}/children")
    public Response children(@Auth OAuthToken token,
            @PathParam("folder_id") RestObject object)
    {
        return new EIListChildren(_imce, token, object, false).execute();
    }

    @Since("1.2")
    @GET
    @Path("/{folder_id}/path")
    public Response path(@Auth OAuthToken token,
            @PathParam("folder_id") RestObject object)
    {
        return new EIObjectPath(_imce,  token, object).execute();
    }

    @Since("1.3")
    @PUT
    @Path("/{folder_id}/is_shared")
    public Response share(@Auth OAuthToken token,
            @PathParam("folder_id") RestObject object)
    {
        // Status.NOT_IMPLEMENTED (= 501) is... not implemented.
        return Response.status(501).entity(
            new Error(com.aerofs.rest.api.Error.Type.NOT_IMPLEMENTED,
                      "This route exists only with centralized metadata configuration.")).build();
    }
}
