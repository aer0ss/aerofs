package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.ids.validation.Identifier;
import com.aerofs.polaris.api.batch.location.LocationUpdateType;
import com.aerofs.polaris.logical.ObjectStore;

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

    private final ObjectStore store;

    public LocationResource(@Context ObjectStore store) {
        this.store = store;
    }

    @POST
    public void markContentAtLocation(
            @Context AeroUserDevicePrincipal principal,
            @PathParam("oid") @Identifier String oid,
            @PathParam("version") @Min(0) long version,
            @PathParam("did") @Identifier String did) {
        store.inTransaction(dao -> {
            store.performLocationUpdate(dao, principal.getUser(), LocationUpdateType.INSERT, oid, version, did);
            return null;
        });
    }

    @DELETE
    public void unmarkContentAtLocation(
            @Context AeroUserDevicePrincipal principal,
            @PathParam("oid") @Identifier String oid,
            @PathParam("version") @Min(0) long version,
            @PathParam("did") @Identifier String did) {
        store.inTransaction(dao -> {
            store.performLocationUpdate(dao, principal.getUser(), LocationUpdateType.REMOVE, oid, version, did);
            return null;
        });
    }
}
