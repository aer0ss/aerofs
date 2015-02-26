package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
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
            @PathParam("oid") OID oid,
            @PathParam("version") @Min(0) long version,
            @PathParam("did") DID did) {
        store.performLocationUpdate(principal.getUser(), LocationUpdateType.INSERT, oid, version, did);
    }

    @DELETE
    public void unmarkContentAtLocation(
            @Context AeroUserDevicePrincipal principal,
            @PathParam("oid") OID oid,
            @PathParam("version") @Min(0) long version,
            @PathParam("did") DID did) {
        store.performLocationUpdate(principal.getUser(), LocationUpdateType.REMOVE, oid, version, did);
    }
}
