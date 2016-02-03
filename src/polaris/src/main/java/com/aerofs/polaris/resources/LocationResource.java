package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.batch.location.LocationUpdateType;
import com.aerofs.polaris.api.notification.SyncedLocation;
import com.aerofs.polaris.logical.ObjectStore;
import com.aerofs.polaris.notification.StoreInformationNotifier;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.validation.constraints.Min;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;

@RolesAllowed(Roles.USER)
@Singleton
public final class LocationResource {

    private final ObjectStore objectStore;
    private final StoreInformationNotifier notifier;

    public LocationResource(@Context ObjectStore objectStore, @Context StoreInformationNotifier notifier) {
        this.objectStore = objectStore;
        this.notifier = notifier;
    }

    @POST
    public void markContentAtLocation(
            @Context AeroUserDevicePrincipal principal,
            @PathParam("oid") OID oid,
            @PathParam("version") @Min(0) long version,
            @PathParam("did") DID did) {
        objectStore.performLocationUpdate(principal.getUser(), LocationUpdateType.INSERT, oid, version, did);

        if(principal.getUser().isTeamServerID()) {
            UniqueID sid = objectStore.inTransaction(dao -> {
                return objectStore.getStore(dao, oid);
            });
            notifier.notify("sync/" + sid.toStringFormal(), new SyncedLocation(oid, version).getBytes(), SyncedLocation.SIZE);
        }
    }

    @DELETE
    public void unmarkContentAtLocation(
            @Context AeroUserDevicePrincipal principal,
            @PathParam("oid") OID oid,
            @PathParam("version") @Min(0) long version,
            @PathParam("did") DID did) {
        objectStore.performLocationUpdate(principal.getUser(), LocationUpdateType.REMOVE, oid, version, did);
    }
}
