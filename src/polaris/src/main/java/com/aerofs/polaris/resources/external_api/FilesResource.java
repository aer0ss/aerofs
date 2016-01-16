package com.aerofs.polaris.resources.external_api;

import com.aerofs.auth.server.AeroOAuthPrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.base.id.RestObject;
import com.aerofs.polaris.external_api.etag.EntityTagSet;
import com.aerofs.polaris.external_api.metadata.MetadataBuilder;
import com.aerofs.polaris.external_api.rest.util.Since;
import com.aerofs.polaris.external_api.rest.util.Version;
import com.aerofs.rest.api.File;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.aerofs.polaris.external_api.Constants.EXTERNAL_API_VERSION;
import static com.google.common.base.Preconditions.checkArgument;

@RolesAllowed(Roles.USER)
@Singleton
@Path(EXTERNAL_API_VERSION + "/files")
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM})
public class FilesResource
{
    private final MetadataBuilder metadataBuilder;

    public FilesResource(@Context MetadataBuilder metadataBuilder)
    {
        this.metadataBuilder = metadataBuilder;
    }

    @Since("0.9")
    @GET
    @Path("/{file_id}")
    public Response metadata(@Context AeroOAuthPrincipal principal,
            @PathParam("file_id") RestObject object, @Context Version version,
            @QueryParam("fields") String queryParam)
    {
        String qp = version.compareTo(new Version(1, 2)) < 0 ? null : queryParam;
        return metadataBuilder.metadata(principal, object, qp, true);
    }

    @Since("0.10")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Context AeroOAuthPrincipal principal, @Context Version version,
            File file)
    {
        checkArgument(file != null);
        return metadataBuilder.create(principal, file.parent, file.name, version, true);
    }

    @Since("0.10")
    @PUT
    @Path("/{file_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response move(@Context AeroOAuthPrincipal principal, @PathParam("file_id") RestObject object,
            @HeaderParam(HttpHeaders.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            File file)
    {
        checkArgument(file != null);
        return metadataBuilder.move(principal, object, file.parent, file.name, ifMatch);
    }

    @Since("0.10")
    @DELETE
    @Path("/{file_id}")
    public Response delete(@Context AeroOAuthPrincipal principal,
            @HeaderParam(HttpHeaders.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            @PathParam("file_id") RestObject object)
    {
        return metadataBuilder.delete(principal, object, ifMatch);
    }

    @Since("1.2")
    @GET
    @Path("/{file_id}/path")
    public Response path(@Context AeroOAuthPrincipal principal,
            @PathParam("file_id") RestObject object)
    {
        return metadataBuilder.path(principal, object);
    }
}
