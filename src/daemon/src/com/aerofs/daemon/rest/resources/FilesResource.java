/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.Version;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.event.EICreateObject;
import com.aerofs.daemon.rest.event.EIDeleteObject;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.daemon.rest.event.EIMoveObject;
import com.aerofs.daemon.rest.event.EIObjectInfo;
import com.aerofs.daemon.rest.event.EIObjectInfo.Type;
import com.aerofs.daemon.rest.util.EntityTagSet;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.daemon.rest.util.OAuthToken;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.rest.api.File;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;

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
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

@Path(Service.VERSION + "/files")
@Produces(MediaType.APPLICATION_JSON)
public class FilesResource
{
    private final IIMCExecutor _imce;

    @Inject
    public FilesResource(CoreIMCExecutor imce)
    {
        _imce = imce.imce();
    }

    @Since("0.9")
    @GET
    @Path("/{file_id}")
    public Response metadata(@Auth OAuthToken token,
            @PathParam("file_id") RestObject object)
    {
        return new EIObjectInfo(_imce, token, object, Type.FILE).execute();
    }

    @Since("0.9")
    @GET
    @Path("/{file_id}/content")
    @Produces({MediaType.APPLICATION_OCTET_STREAM, "multipart/byteranges"})
    public Response content(@Auth OAuthToken token,
            @PathParam("file_id") RestObject object,
            @HeaderParam(HttpHeaders.IF_RANGE) String ifRange,
            @HeaderParam(HttpHeaders.RANGE) String range,
            @HeaderParam(HttpHeaders.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
    {
        EntityTag etIfRange = EntityTagUtil.parse(ifRange);
        return new EIFileContent(_imce, token, object, etIfRange, range, ifNoneMatch).execute();
    }

    @Since("0.10")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Auth OAuthToken token,
            @Context Version version,
            File file) throws IOException
    {
        Preconditions.checkArgument(file.parent != null);
        Preconditions.checkArgument(file.name != null);
        return new EICreateObject(_imce, token, version, file.parent, file.name, false).execute();
    }

    @Since("0.10")
    @PUT
    @Path("/{file_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response move(@Auth OAuthToken token,
            @PathParam("file_id") RestObject object,
            @HeaderParam(HttpHeaders.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            File file) throws IOException
    {
        Preconditions.checkArgument(file.parent != null);
        Preconditions.checkArgument(file.name != null);
        return new EIMoveObject(_imce, token, object, file.parent, file.name, ifMatch).execute();
    }

    @Since("0.10")
    @DELETE
    @Path("/{file_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response delete(@Auth OAuthToken token,
            @PathParam("file_id") RestObject object,
            @HeaderParam(HttpHeaders.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch)
            throws IOException
    {
        return new EIDeleteObject(_imce, token, object, ifMatch).execute();
    }
}

