package com.aerofs.polaris.resources;

import com.aerofs.baseline.auth.AeroPrincipal;
import com.aerofs.baseline.ids.Identifier;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.api.operation.OperationResult;
import com.aerofs.polaris.logical.LogicalObjectStore;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@RolesAllowed(AeroPrincipal.Roles.CLIENT)
@Singleton
public final class ObjectResource {

    private final LogicalObjectStore objectStore;
    private final AccessManager accessManager;
    private final ResourceContext resourceContext;

    public ObjectResource(@Context LogicalObjectStore objectStore, @Context AccessManager accessManager, @Context ResourceContext resourceContext) {
        this.objectStore = objectStore;
        this.accessManager = accessManager;
        this.resourceContext = resourceContext;
    }

    @Path("/versions")
    public VersionsResource getVersions() {
        return resourceContext.getResource(VersionsResource.class);
    }

    // NOTE (AG): order the JAX-RS annotations first
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public OperationResult update(@Context final AeroPrincipal principal, @PathParam("oid") @Identifier final String oid, final Operation operation) throws AccessException {
        accessManager.checkAccess(principal.getUser(), oid, Access.READ, Access.WRITE);

        return objectStore.inTransaction(new TransactionCallback<OperationResult>() {

            @Override
            public OperationResult inTransaction(Handle conn, TransactionStatus status) throws Exception {
                return new OperationResult(objectStore.performOperation(conn, principal.getDevice(), oid, operation));
            }
        });
    }
}
