package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.PolarisConfiguration;
import com.aerofs.polaris.api.operation.AppliedTransforms;
import com.aerofs.polaris.api.types.Transform;
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
import java.util.List;

@RolesAllowed(Roles.USER)
@Path("/transforms")
@Singleton
public final class TransformsResource {

    private final ObjectStore store;
    private final int maxReturnedTransforms;

    public TransformsResource(@Context ObjectStore store, @Context PolarisConfiguration configuration) {
        this.store = store;
        this.maxReturnedTransforms = configuration.getMaxReturnedTransforms();
    }

    @Path("/{oid}")
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    public AppliedTransforms getTransforms(
            @Context AeroUserDevicePrincipal principal,
            @PathParam("oid") UniqueID root,
            @QueryParam("since") @Min(-1) long since,
            @QueryParam("count") @Min(1) int requestedResultCount) {
        return store.inTransaction(dao -> {
            int transformCount = store.getTransformCount(dao, principal.getUser(), root);
            int resultCount = Math.min(requestedResultCount, maxReturnedTransforms);
            List<Transform> transforms = store.getTransforms(dao, principal.getUser(), root, since, resultCount);
            return new AppliedTransforms(transformCount, transforms);
        });
    }
}
