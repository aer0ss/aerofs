package com.aerofs.polaris.resources;

import com.aerofs.baseline.auth.AeroPrincipal;
import com.aerofs.ids.validation.Identifier;
import com.aerofs.polaris.PolarisConfiguration;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.operation.AppliedTransforms;
import com.aerofs.polaris.api.types.Transform;
import com.aerofs.polaris.logical.DAO;
import com.aerofs.polaris.logical.LogicalObjectStore;
import com.aerofs.polaris.logical.Transactional;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RolesAllowed(AeroPrincipal.Roles.CLIENT)
@Path("/transforms")
@Singleton
public final class TransformsResource {

    private final LogicalObjectStore objectStore;
    private final AccessManager accessManager;
    private final int maxReturnedTransforms;

    public TransformsResource(@Context LogicalObjectStore objectStore, @Context AccessManager accessManager, @Context PolarisConfiguration configuration) {
        this.objectStore = objectStore;
        this.accessManager = accessManager;
        this.maxReturnedTransforms = configuration.getMaxReturnedTransforms();
    }

    @Path("/{oid}")
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    public AppliedTransforms getTransformsSince(
            @Context @NotNull AeroPrincipal principal,
            @PathParam("oid") @NotNull @Identifier final String oid,
            @QueryParam("since") @Min(-1) final long since,
            @QueryParam("count") @Min(1) int resultCount) throws AccessException {
        accessManager.checkAccess(principal.getUser(), oid, Access.READ);

        final int actualResultCount = Math.min(resultCount, maxReturnedTransforms);
        return objectStore.inTransaction(new Transactional<AppliedTransforms>() {
            @Override
            public AppliedTransforms execute(DAO dao) throws Exception {
                int transformCount = objectStore.getTransformCount(dao, oid);
                List<Transform> transforms = objectStore.getTransformsSince(dao, oid, since, actualResultCount);
                return new AppliedTransforms(transformCount, transforms);
            }
        });
    }
}
