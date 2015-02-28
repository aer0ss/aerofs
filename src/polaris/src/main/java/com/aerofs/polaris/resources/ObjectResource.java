package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.api.operation.OperationResult;
import com.aerofs.polaris.logical.ObjectStore;
import com.aerofs.polaris.notification.Notifier;
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
import java.util.stream.Collectors;

@RolesAllowed(Roles.USER)
@Singleton
public final class ObjectResource {

    private final ObjectStore store;
    private final Notifier notifier;
    private final ResourceContext context;

    public ObjectResource(@Context ObjectStore store, @Context Notifier notifier, @Context ResourceContext context) {
        this.store = store;
        this.notifier = notifier;
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
    public OperationResult update(@Context AeroUserDevicePrincipal principal, @PathParam("oid") UniqueID oid, Operation operation) {
        OperationResult result = new OperationResult(store.performTransform(principal.getUser(), principal.getDevice(), oid, operation));

        Set<UniqueID> updatedRoots = Sets.newHashSet();
        updatedRoots.addAll(result.updated.stream().map(updated -> updated.object.root).collect(Collectors.toList()));
        updatedRoots.forEach(notifier::notifyStoreUpdated);

        return result;
    }
}
