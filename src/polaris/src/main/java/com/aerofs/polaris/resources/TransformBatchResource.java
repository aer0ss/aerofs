package com.aerofs.polaris.resources;

import com.aerofs.auth.cert.AeroDevicePrincipal;
import com.aerofs.auth.Roles;
import com.aerofs.baseline.db.DBIExceptions;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.batch.TransformBatch;
import com.aerofs.polaris.api.batch.TransformBatchOperation;
import com.aerofs.polaris.api.batch.TransformBatchOperationResult;
import com.aerofs.polaris.api.batch.TransformBatchResult;
import com.aerofs.polaris.api.operation.Updated;
import com.aerofs.polaris.logical.LogicalObjectStore;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RolesAllowed(Roles.USER)
@Singleton
public final class TransformBatchResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformBatchResource.class);

    private final LogicalObjectStore objectStore;
    private final AccessManager accessManager;

    public TransformBatchResource(@Context LogicalObjectStore objectStore, @Context AccessManager accessManager) {
        this.objectStore = objectStore;
        this.accessManager = accessManager;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TransformBatchResult submitBatch(@Context @NotNull final AeroDevicePrincipal principal, @NotNull final TransformBatch batch) {
        List<TransformBatchOperation> operations = batch.getOperations();
        final TransformBatchResult batchResult = new TransformBatchResult(operations.size());

        TransformBatchOperation operation = null;
        try {
            for (int i = 0; i < operations.size(); i++) {
                operation = operations.get(i);
                accessManager.checkAccess(principal.getDevice(), operation.getOid(), Access.WRITE);

                final TransformBatchOperation submitted = operation;
                objectStore.inTransaction(dao -> {
                    List<Updated> updated = objectStore.performOperation(dao, principal.getDevice(), submitted.getOid(), submitted.getOperation());
                    batchResult.getResults().add(new TransformBatchOperationResult(updated));
                    return null;
                });
            }
        } catch (CallbackFailedException e) {
            @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
            Throwable cause = DBIExceptions.findRootCause(e);
            TransformBatchOperationResult result = getBatchOperationErrorResult(cause);
            LOGGER.warn("fail transform batch operation {}", operation, e);
            batchResult.getResults().add(result);
        } catch (Exception e) {
            TransformBatchOperationResult result = getBatchOperationErrorResult(e);
            LOGGER.warn("fail transform batch operation {}", operation, e);
            batchResult.getResults().add(result);
        }

        return batchResult;
    }

    private static TransformBatchOperationResult getBatchOperationErrorResult(Throwable cause) {
        TransformBatchOperationResult result;

        if (cause instanceof PolarisException) {
            PolarisException polarisException = (PolarisException) cause;
            result = new TransformBatchOperationResult(polarisException.getErrorCode(), polarisException.getMessage());
        } else {
            result = new TransformBatchOperationResult(PolarisError.UNKNOWN, cause.getMessage());
        }

        return result;
    }
}
