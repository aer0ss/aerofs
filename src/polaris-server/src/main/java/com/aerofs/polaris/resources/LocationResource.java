package com.aerofs.polaris.resources;

import com.aerofs.baseline.auth.AeroPrincipal;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.ids.Identifier;
import com.aerofs.polaris.logical.LogicalObjectStore;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.validation.constraints.Min;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RolesAllowed(AeroPrincipal.CLIENT_ROLE)
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
    public List<String> getLocationsForContent(
            @Context AeroPrincipal principal,
            @PathParam("oid") @Identifier final String oid,
            @PathParam("version") @Min(0) final long version,
            @PathParam("did") @Identifier final String did) throws AccessException {
        accessManager.checkAccess(principal.getUser(), oid, Access.READ);

        return objectStore.inTransaction(new TransactionCallback<List<String>>() {

            @Override
            public List<String> inTransaction(Handle conn, TransactionStatus status) throws Exception {
                return objectStore.getLocations(conn, oid, version);
            }
        });
    }

    @POST
    public void markContentAtLocation(
            @Context AeroPrincipal principal,
            @PathParam("oid") @Identifier final String oid,
            @PathParam("version") @Min(0) final long version,
            @PathParam("did") @Identifier final String did) throws AccessException {
        accessManager.checkAccess(principal.getUser(), oid, Access.WRITE);

        objectStore.inTransaction(new TransactionCallback<Void>() {

            @Override
            public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                objectStore.addLocation(conn, oid, version, did);
                return null;
            }
        });
    }

    @DELETE
    public void unmarkContentAtLocation(
            @Context AeroPrincipal principal,
            @PathParam("oid") @Identifier final String oid,
            @PathParam("version") @Min(0) final long version,
            @PathParam("did") @Identifier final String did) throws AccessException {
        accessManager.checkAccess(principal.getUser(), oid, Access.WRITE);

        objectStore.inTransaction(new TransactionCallback<Void>() {

            @Override
            public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                objectStore.removeLocation(conn, oid, version, did);
                return null;
            }
        });
    }
}
