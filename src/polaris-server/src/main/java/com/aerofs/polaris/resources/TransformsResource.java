package com.aerofs.polaris.resources;

import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.Transform;
import com.aerofs.polaris.ids.Identifier;
import com.aerofs.polaris.logical.LogicalObjectStore;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import javax.inject.Singleton;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/transforms")
@Singleton
public final class TransformsResource {

    private final LogicalObjectStore logicalObjectStore;

    public TransformsResource(@Context LogicalObjectStore logicalObjectStore) {
        this.logicalObjectStore = logicalObjectStore;
    }

    @Path("/{oid}")
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    public List<Transform> getChangesSince(@Identifier @PathParam("oid") final String oid, @Min(-1) @QueryParam("since") final long since, @Min(1) @Max(Constants.MAX_RETURNED_TRANSFORMS) @QueryParam("count") final int resultCount) {
        return logicalObjectStore.inTransaction(new TransactionCallback<List<Transform>>() {
            @Override
            public List<Transform> inTransaction(Handle conn, TransactionStatus status) throws Exception {
                return logicalObjectStore.getTransformsSince(conn, oid, since, resultCount);
            }
        });
    }
}
