package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.ids.validation.Identifier;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.logical.DAO;
import com.aerofs.polaris.logical.LogicalObjectStore;
import com.aerofs.polaris.logical.Transactional;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RolesAllowed(Roles.USER)
@Singleton
public final class LocationResource {

    private final LogicalObjectStore objectStore;
    private final AccessManager accessManager;

    public LocationResource(@Context LogicalObjectStore objectStore, @Context AccessManager accessManager) {
        this.objectStore = objectStore;
        this.accessManager = accessManager;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getLocationsForContent(@Context @NotNull AeroUserDevicePrincipal principal, @PathParam("oid") @NotNull @Identifier String oid, @PathParam("version") @Min(0) long version, @PathParam("did") @NotNull @Identifier String did) throws AccessException {
        accessManager.checkAccess(principal.getUser(), oid, Access.READ);

        return objectStore.inTransaction(new Transactional<List<String>>() {

            @Override
            public List<String> execute(DAO dao) throws Exception {
                return objectStore.getLocations(dao, oid, version);
            }
        });
    }

    @POST
        public void markContentAtLocation(@Context @NotNull AeroUserDevicePrincipal principal, @PathParam("oid") @NotNull @Identifier String oid, @PathParam("version") @Min(0) long version, @PathParam("did") @NotNull @Identifier String did) throws AccessException {
        accessManager.checkAccess(principal.getUser(), oid, Access.WRITE);

        objectStore.inTransaction(new Transactional<Object>() {

            @Override
            public Void execute(DAO dao) throws Exception {
                objectStore.insertLocation(dao, oid, version, did);
                return null;
            }
        });
    }

    @DELETE
    public void unmarkContentAtLocation( @Context @NotNull AeroUserDevicePrincipal principal, @PathParam("oid") @NotNull @Identifier String oid, @PathParam("version") @Min(0) long version, @PathParam("did") @NotNull @Identifier String did) throws AccessException {
        accessManager.checkAccess(principal.getUser(), oid, Access.WRITE);

        objectStore.inTransaction(new Transactional<Object>() {

            @Override
            public Void execute(DAO dao) throws Exception {
                objectStore.removeLocation(dao, oid, version, did);
                return null;
            }
        });
    }
}
