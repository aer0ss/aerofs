package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.api.operation.OperationResult;
import com.aerofs.polaris.api.operation.OperationType;
import com.aerofs.polaris.api.operation.RenameStore;
import com.aerofs.polaris.logical.ObjectStore;
import com.aerofs.polaris.logical.StoreRenamer;
import com.aerofs.polaris.notification.Notifier;

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
import java.util.Map;
import java.util.stream.Collectors;

import static com.aerofs.polaris.api.PolarisUtilities.stringFromUTF8Bytes;

@RolesAllowed(Roles.USER)
@Singleton
public final class ObjectResource {

    private final ObjectStore objectStore;
    private final Notifier notifier;
    private final ResourceContext context;
    private final StoreRenamer storeRenamer;

    public ObjectResource(@Context ObjectStore objectStore, @Context Notifier notifier, @Context ResourceContext context,
            @Context StoreRenamer storeRenamer) {
        this.objectStore = objectStore;
        this.notifier = notifier;
        this.context = context;
        this.storeRenamer = storeRenamer;
    }

    // NOTE (AG): order the JAX-RS annotations first
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public OperationResult update(@Context AeroUserDevicePrincipal principal, @PathParam("oid") UniqueID oid, Operation operation) {
        OperationResult result = objectStore.performTransform(principal.getUser(), principal.getDevice(), oid, operation);

        if (OperationType.RENAME_STORE.equals(operation.type)) {
            storeRenamer.renameStore(principal, oid, stringFromUTF8Bytes(((RenameStore)operation).newName));
        }

        Map<UniqueID, Long> updatedStores = result.updated.stream().collect(Collectors.toMap(x -> x.object.store, x -> x.transformTimestamp, Math::max));
        updatedStores.forEach(notifier::notifyStoreUpdated);

        return result;
    }
}
