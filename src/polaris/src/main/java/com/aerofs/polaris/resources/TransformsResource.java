package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroPrincipal;
import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.PolarisConfiguration;
import com.aerofs.polaris.api.operation.Transforms;
import com.aerofs.polaris.logical.ObjectStore;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.validation.constraints.Min;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@RolesAllowed({Roles.SERVICE, Roles.USER})
@Path("/transforms")
@Singleton
public final class TransformsResource {

    private final ObjectStore objectStore;
    private final int maxReturnedTransforms;

    public TransformsResource(@Context ObjectStore objectStore, @Context PolarisConfiguration configuration) {
        this.objectStore = objectStore;
        this.maxReturnedTransforms = configuration.getMaxReturnedTransforms();
    }

    @Path("/{oid}")
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    public Transforms getTransforms(
            @Context AeroPrincipal principal,
            @PathParam("oid") UniqueID store,
            @QueryParam("since") @Min(-1) long since,
            @QueryParam("count") @Min(1) int requestedResultCount) {
        int resultCount = Math.min(requestedResultCount, maxReturnedTransforms);
        return objectStore.getTransforms((principal instanceof AeroUserDevicePrincipal ? ((AeroUserDevicePrincipal) principal).getUser() : null), store, since, resultCount);
    }
}
