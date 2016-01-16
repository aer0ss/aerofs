package com.aerofs.polaris.resources.external_api;

import com.aerofs.auth.server.AeroOAuthPrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.base.id.RestObject;
import com.aerofs.polaris.external_api.etag.EntityTagSet;
import com.aerofs.polaris.external_api.metadata.MetadataBuilder;
import com.aerofs.polaris.external_api.rest.util.Since;
import com.aerofs.polaris.external_api.rest.util.Version;
import com.aerofs.rest.api.Folder;

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
@Path(EXTERNAL_API_VERSION + "/folders")
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM})
public class FoldersResource
{
    private final MetadataBuilder metadataBuilder;

    public FoldersResource(@Context MetadataBuilder metadataBuilder)
    {
        this.metadataBuilder = metadataBuilder;
    }

    @Since("0.9")
    @GET
    @Path("/{folder_id}")
    public Response metadata(@Context AeroOAuthPrincipal principal,
            @PathParam("folder_id") RestObject object, @QueryParam("fields") String queryParam,
            @Context Version version)
    {
        String qp = version.compareTo(new Version(1, 2)) < 0 ? null : queryParam;
        // TODO (AS): Annoying boolean field at the end because we want to check for files being
        // routed to this path accidentally. Necessary? Ask HB.
        return metadataBuilder.metadata(principal, object, qp, false);
    }

    @Since("0.10")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Context AeroOAuthPrincipal principal, @Context Version version,
            Folder folder)
    {
        return metadataBuilder.create(principal, folder.parent, folder.name, version, false);
    }

    @Since("0.10")
    @PUT
    @Path("/{folder_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response move(@Context AeroOAuthPrincipal principal, @PathParam("folder_id") RestObject object,
            @HeaderParam(HttpHeaders.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            Folder folder)
    {
        checkArgument(folder != null);
        return metadataBuilder.move(principal, object, folder.parent, folder.name, ifMatch);
    }

    @Since("0.10")
    @DELETE
    @Path("/{folder_id}")
    public Response delete(@Context AeroOAuthPrincipal principal,
            @HeaderParam(HttpHeaders.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            @PathParam("folder_id") RestObject object)
    {
        return metadataBuilder.delete(principal, object, ifMatch);
    }

    @Since("1.2")
    @GET
    @Path("/{folder_id}/children")
    public Response children(@Context AeroOAuthPrincipal principal, @Context Version version,
            @PathParam("folder_id") RestObject object)
    {
        return metadataBuilder.children(principal, object, false);
    }

    @Since("1.2")
    @GET
    @Path("/{folder_id}/path")
    public Response path(@Context AeroOAuthPrincipal principal,
            @PathParam("folder_id") RestObject object)
    {
        return metadataBuilder.path(principal, object);
    }

    @Since("1.3")
    @PUT
    @Path("/{folder_id}/is_shared")
    public Response share(@Context AeroOAuthPrincipal principal,
            @PathParam("folder_id") RestObject object)
    {
        return metadataBuilder.share(principal, object);
    }
}
