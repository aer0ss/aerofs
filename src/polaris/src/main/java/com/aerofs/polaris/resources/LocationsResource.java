package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.polaris.logical.ObjectStore;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.validation.constraints.Min;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RolesAllowed(Roles.USER)
@Singleton
public final class LocationsResource {

    private final ObjectStore store;
    private final ResourceContext context;

    public LocationsResource(@Context ObjectStore store, @Context ResourceContext context) {
        this.store = store;
        this.context = context;
    }

    @Path("/{did}")
    public LocationResource operation() {
        return context.getResource(LocationResource.class);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<DID> getLocationsForContent(@Context AeroUserDevicePrincipal principal, @PathParam("oid") OID oid, @PathParam("version") @Min(0) long version) {
        return store.inTransaction(dao -> store.getLocations(dao, principal.getUser(), oid, version));
    }
}
