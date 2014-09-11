package com.aerofs.polaris.resources;

import com.aerofs.polaris.api.BatchOperation;
import com.aerofs.polaris.api.BatchUpdate;
import com.aerofs.polaris.api.TransformType;
import com.aerofs.polaris.logical.LogicalObjectStore;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/batch")
@Singleton
public final class BatchedResource {

    private final LogicalObjectStore logicalObjectStore;

    public BatchedResource(@Context LogicalObjectStore logicalObjectStore) {
        this.logicalObjectStore = logicalObjectStore;
    }

    // FIXME(AG): return the correct thing when a batch fails

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void submitBatch(final BatchUpdate batchUpdate) throws BatchException {
        if (batchUpdate.atomic) {
            // for now we only allow combo delete-create
            Preconditions.checkArgument(batchUpdate.batchOperations.size() == 2, "batch operations: %s", batchUpdate.batchOperations.size());
            Preconditions.checkArgument(batchUpdate.batchOperations.get(0).update.child != null);
            Preconditions.checkArgument(batchUpdate.batchOperations.get(1).update.child != null);
            Preconditions.checkArgument(batchUpdate.batchOperations.get(0).update.child.equals(batchUpdate.batchOperations.get(1).update.child));

            List<TransformType> types = Lists.newArrayListWithCapacity(2);
            for (BatchOperation operation : batchUpdate.batchOperations) {
                types.add(operation.update.transformType);
            }

            Preconditions.checkArgument(types.contains(TransformType.INSERT_CHILD) && types.contains(TransformType.DELETE_CHILD));

            logicalObjectStore.inTransaction(new TransactionCallback<Void>() {
                @Override
                public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                    for (BatchOperation operation : batchUpdate.batchOperations) {
                        logicalObjectStore.transform(conn, operation.oid, operation.update);
                    }

                    return null;
                }
            });
        } else {
            for (final BatchOperation operation : batchUpdate.batchOperations) {
                logicalObjectStore.inTransaction(new TransactionCallback<Void>() {
                    @Override
                    public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                        logicalObjectStore.transform(conn, operation.oid, operation.update);
                        return null;
                    }
                });
            }
        }
    }
}
