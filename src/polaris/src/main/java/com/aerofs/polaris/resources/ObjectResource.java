package com.aerofs.polaris.resources;

import com.aerofs.auth.cert.AeroDevicePrincipal;
import com.aerofs.auth.Roles;
import com.aerofs.ids.validation.Identifier;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.api.operation.OperationResult;
import com.aerofs.polaris.logical.DAO;
import com.aerofs.polaris.logical.LogicalObjectStore;
import com.aerofs.polaris.logical.Transactional;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@RolesAllowed(Roles.USER)
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
    public OperationResult update(@Context @NotNull final AeroDevicePrincipal principal, @PathParam("oid") @NotNull @Identifier final String oid, @NotNull final Operation operation) throws AccessException {
        accessManager.checkAccess(principal.getDevice(), oid, Access.READ, Access.WRITE);

        return objectStore.inTransaction(new Transactional<OperationResult>() {

            @Override
            public OperationResult execute(DAO dao) throws Exception {
                return new OperationResult(objectStore.performOperation(dao, principal.getDevice(), oid, operation));
            }
        });
    }
}
