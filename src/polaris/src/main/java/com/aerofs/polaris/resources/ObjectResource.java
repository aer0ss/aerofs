package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.ids.validation.Identifier;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.api.operation.OperationResult;
import com.aerofs.polaris.api.operation.Updated;
import com.aerofs.polaris.logical.ObjectStore;
import com.aerofs.polaris.notification.UpdatePublisher;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

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
import java.util.Set;

@RolesAllowed(Roles.USER)
@Singleton
public final class ObjectResource {

    private final ObjectStore store;
    private final UpdatePublisher publisher;
    private final ResourceContext context;

    public ObjectResource(@Context ObjectStore store, @Context UpdatePublisher publisher, @Context ResourceContext context) {
        this.store = store;
        this.publisher = publisher;
        this.context = context;
    }

    @Path("/versions")
    public VersionsResource getVersions() {
        return context.getResource(VersionsResource.class);
    }

    // NOTE (AG): order the JAX-RS annotations first
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public OperationResult update(@Context AeroUserDevicePrincipal principal, @PathParam("oid") @Identifier String oid, Operation operation) {
        OperationResult result = store.inTransaction(dao -> new OperationResult(store.performTransform(dao, principal.getUser(), principal.getDevice(), oid, operation)));

        Preconditions.checkState(result.getUpdated() != null, "no updates made for %s", operation);
        Set<String> updatedRoots = Sets.newHashSet();

        for (Updated updated : result.getUpdated()) {
            updatedRoots.add(updated.getObject().getRoot());
        }

        for (String root : updatedRoots) {
            publisher.publishUpdate(root);
        }

        return result;
    }
}
