package com.aerofs.polaris.resources;

import com.aerofs.polaris.ids.Identifier;
import com.aerofs.polaris.logical.LogicalObjectStore;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

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

@Singleton
public final class LocationResource {

    private final LogicalObjectStore logicalObjectStore;

    public LocationResource(@Context LogicalObjectStore logicalObjectStore) {
        this.logicalObjectStore = logicalObjectStore;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getLocationsForContent(@Identifier @PathParam("oid") final String oid, @Min(0) @PathParam("version") final long version, @Identifier @PathParam("did") final String did) {
        return logicalObjectStore.inTransaction(new TransactionCallback<List<String>>() {

            @Override
            public List<String> inTransaction(Handle conn, TransactionStatus status) throws Exception {
                return logicalObjectStore.getLocations(conn, oid, version);
            }
        });
    }

    @POST
    public void markContentAtLocation(@Identifier @PathParam("oid") final String oid, @Min(0) @PathParam("version") final long version, @Identifier @PathParam("did") final String did) {
        logicalObjectStore.inTransaction(new TransactionCallback<Void>() {

            @Override
            public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                logicalObjectStore.addLocation(conn, oid, version, did);
                return null;
            }
        });
    }

    @DELETE
    public void unmarkContentAtLocation(@Identifier @PathParam("oid") final String oid, @Min(0) @PathParam("version") final long version, @Identifier @PathParam("did") final String did) {
        logicalObjectStore.inTransaction(new TransactionCallback<Void>() {

            @Override
            public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                logicalObjectStore.removeLocation(conn, oid, version, did);
                return null;
            }
        });
    }
}
