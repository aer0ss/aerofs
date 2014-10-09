package com.aerofs.polaris.resources;

import com.aerofs.baseline.auth.AeroPrincipal;
import com.aerofs.baseline.db.DBIExceptions;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.batch.Batch;
import com.aerofs.polaris.api.batch.BatchOperation;
import com.aerofs.polaris.api.batch.BatchOperationResult;
import com.aerofs.polaris.api.batch.BatchResult;
import com.aerofs.polaris.api.operation.Updated;
import com.aerofs.polaris.logical.LogicalObjectStore;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RolesAllowed(AeroPrincipal.Roles.CLIENT)
@Path("/batch")
@Singleton
public final class BatchResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchResource.class);

    private final LogicalObjectStore objectStore;
    private final AccessManager accessManager;

    public BatchResource(@Context LogicalObjectStore objectStore, @Context AccessManager accessManager) {
        this.objectStore = objectStore;
        this.accessManager = accessManager;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public BatchResult submitBatch(@Context final AeroPrincipal principal, final Batch batch) {

        final BatchResult batchResult = new BatchResult(batch.operations.size());

        BatchOperation operation = null;
        try {
            for (int i = 0; i < batch.operations.size(); i++) {
                operation = batch.operations.get(i);
                accessManager.checkAccess(principal.getUser(), operation.oid, Access.WRITE);

                final BatchOperation submitted = operation;
                objectStore.inTransaction(new TransactionCallback<Void>() {
                    @Override
                    public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                        List<Updated> updated = objectStore.performOperation(conn, principal.getDevice(), submitted.oid, submitted.operation);
                        batchResult.results.add(new BatchOperationResult(updated));
                        return null;
                    }
                });
            }
        } catch (CallbackFailedException e) {
            @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
            Throwable cause = DBIExceptions.findRootCause(e);
            BatchOperationResult result = getBatchOperationErrorResult(cause);
            LOGGER.warn("fail batch operation {} err:{}", operation, e.getMessage());
            batchResult.results.add(result);
        } catch (Exception e) {
            BatchOperationResult result = getBatchOperationErrorResult(e);
            LOGGER.warn("fail batch operation {} err:{}", operation, e.getMessage());
            batchResult.results.add(result);
        }

        return batchResult;
    }

    private static BatchOperationResult getBatchOperationErrorResult(Throwable cause) {
        BatchOperationResult result;

        if (cause instanceof PolarisException) {
            PolarisException polarisException = (PolarisException) cause;
            result = new BatchOperationResult(polarisException.getErrorCode(), polarisException.getMessage());
        } else {
            result = new BatchOperationResult(PolarisError.UNKNOWN, cause.getMessage());
        }

        return result;
    }
}
