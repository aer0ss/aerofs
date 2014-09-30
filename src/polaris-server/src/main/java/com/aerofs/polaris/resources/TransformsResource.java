package com.aerofs.polaris.resources;

import com.aerofs.baseline.auth.AeroPrincipal;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.Transform;
import com.aerofs.polaris.api.operation.AppliedTransforms;
import com.aerofs.polaris.ids.Identifier;
import com.aerofs.polaris.logical.LogicalObjectStore;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RolesAllowed(AeroPrincipal.CLIENT_ROLE)
@Path("/transforms")
@Singleton
public final class TransformsResource {

    private final LogicalObjectStore objectStore;
    private final AccessManager accessManager;

    public TransformsResource(@Context LogicalObjectStore objectStore, @Context AccessManager accessManager) {
        this.objectStore = objectStore;
        this.accessManager = accessManager;
    }

    @Path("/{oid}")
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    public AppliedTransforms getTransformsSince(
            @Context AeroPrincipal principal,
            @PathParam("oid") @Identifier final String oid,
            @QueryParam("since") @Min(-1) final long since,
            @QueryParam("count") @Min(1) @Max(Constants.MAX_RETURNED_TRANSFORMS) final int resultCount) throws AccessException {
        accessManager.checkAccess(principal.getUser(), oid, Access.READ);

        return objectStore.inTransaction(new TransactionCallback<AppliedTransforms>() {
            @Override
            public AppliedTransforms inTransaction(Handle conn, TransactionStatus status) throws Exception {
                int transformCount = objectStore.getTransformCount(conn, oid);
                List<Transform> transforms = objectStore.getTransformsSince(conn, oid, since, resultCount);
                return new AppliedTransforms(transformCount, transforms);
            }
        });
    }
}
