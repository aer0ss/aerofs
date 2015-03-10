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

    private final ObjectStore objectStore;
    private final Notifier notifier;
    private final ResourceContext context;

    public ObjectResource(@Context ObjectStore objectStore, @Context Notifier notifier, @Context ResourceContext context) {
        this.objectStore = objectStore;
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
        OperationResult result = new OperationResult(objectStore.performTransform(principal.getUser(), principal.getDevice(), oid, operation));

        Set<UniqueID> updatedStores = Sets.newHashSet();
        updatedStores.addAll(result.updated.stream().map(updated -> updated.object.store).collect(Collectors.toList()));
        updatedStores.forEach(notifier::notifyStoreUpdated);

        return result;
    }
}
