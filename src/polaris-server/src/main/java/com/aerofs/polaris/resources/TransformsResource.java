package com.aerofs.polaris.resources;

import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.Transform;
import com.aerofs.polaris.logical.LogicalObjectStore;
import com.google.common.base.Preconditions;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Singleton
public final class TransformsResource {

    private final LogicalObjectStore logicalObjectStore;

    public TransformsResource(@Context LogicalObjectStore logicalObjectStore) {
        this.logicalObjectStore = logicalObjectStore;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Transform> getChangesSince(@PathParam("oid") final String oid, @QueryParam("since") final long since, @QueryParam("batchSize") final int batchSize) {
        Preconditions.checkArgument(since >= 0, "since parameter %s out of range", since);
        Preconditions.checkArgument(batchSize >= 0, "batchSize parameter %s out of range", batchSize);
        Preconditions.checkArgument(batchSize <= Constants.MAX_RETURNED_TRANSFORMS, "batchSize parameter %s out of range", batchSize);

        return logicalObjectStore.inTransaction(new TransactionCallback<List<Transform>>() {
            @Override
            public List<Transform> inTransaction(Handle conn, TransactionStatus status) throws Exception {
                return logicalObjectStore.getTransformsSince(conn, oid, since, batchSize);
            }
        });
    }
}
