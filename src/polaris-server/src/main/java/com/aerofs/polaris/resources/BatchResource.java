package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.DBIExceptions;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.ErrorCode;
import com.aerofs.polaris.api.LogicalObject;
import com.aerofs.polaris.api.batch.Batch;
import com.aerofs.polaris.api.batch.BatchOperation;
import com.aerofs.polaris.api.batch.BatchOperationResult;
import com.aerofs.polaris.api.batch.BatchResult;
import com.aerofs.polaris.logical.LogicalObjectStore;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/batch")
@Singleton
public final class BatchResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchResource.class);

    private final LogicalObjectStore logicalObjectStore;

    public BatchResource(@Context LogicalObjectStore logicalObjectStore) {
        this.logicalObjectStore = logicalObjectStore;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public BatchResult submitBatch(final Batch batch) {
        final BatchResult batchResult = new BatchResult(batch.operations.size());

        for (final BatchOperation operation : batch.operations) {
            try {
                logicalObjectStore.inTransaction(new TransactionCallback<Void>() {
                    @Override
                    public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                        List<LogicalObject> updated = logicalObjectStore.performOperation(conn, operation.oid, operation.operation);
                        batchResult.results.add(new BatchOperationResult(updated));
                        return null;
                    }
                });
            } catch (CallbackFailedException e) {
                @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
                Throwable cause = DBIExceptions.findRootCause(e);

                BatchOperationResult result;
                if (cause instanceof PolarisException) {
                    PolarisException polarisException = (PolarisException) cause;
                    result = new BatchOperationResult(polarisException.getErrorCode(), polarisException.getMessage());
                } else {
                    result = new BatchOperationResult(ErrorCode.UNKNOWN, cause.getMessage());
                }

                LOGGER.warn("fail batch operation {} err:{}", operation, e.getMessage());
                batchResult.results.add(result);
            }
        }

        return batchResult;
    }
}
