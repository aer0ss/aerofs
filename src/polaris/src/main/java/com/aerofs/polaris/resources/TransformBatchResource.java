package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.baseline.db.Databases;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.batch.transform.TransformBatch;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperation;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperationResult;
import com.aerofs.polaris.api.batch.transform.TransformBatchResult;
import com.aerofs.polaris.api.operation.Updated;
import com.aerofs.polaris.logical.ObjectStore;
import com.aerofs.polaris.notification.UpdatePublisher;
import com.google.common.collect.Sets;
import org.skife.jdbi.v2.exceptions.DBIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

@RolesAllowed(Roles.USER)
@Singleton
public final class TransformBatchResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformBatchResource.class);

    private final ObjectStore store;
    private final UpdatePublisher publisher;

    public TransformBatchResource(@Context ObjectStore store, @Context UpdatePublisher publisher) {
        this.store = store;
        this.publisher = publisher;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TransformBatchResult submitBatch(@Context AeroUserDevicePrincipal principal, TransformBatch batch) {
        final TransformBatchResult batchResult = new TransformBatchResult(batch.getOperations().size());

        for (TransformBatchOperation operation: batch.getOperations()) {
            try {
                store.inTransaction(dao -> {
                    List<Updated> updated = store.performTransform(dao, principal.getUser(), principal.getDevice(), operation.getOid(), operation.getOperation());
                    batchResult.getResults().add(new TransformBatchOperationResult(updated));
                    return null;
                });
            } catch (Exception e) {
                TransformBatchOperationResult result = getBatchOperationErrorResult(e);
                LOGGER.warn("fail transform batch operation {}", operation, e);
                batchResult.getResults().add(result);
                break; // abort early if a batch operation fails
            }
        }

        Set<String> updatedRoots = Sets.newHashSet();

        for (TransformBatchOperationResult result : batchResult.getResults()) {
            if (result.getUpdated() == null) { // in error case; technically I can 'break' here...
                continue;
            }

            for (Updated updated : result.getUpdated()) {
                updatedRoots.add(updated.getObject().getRoot());
            }
        }

        updatedRoots.forEach(publisher::publishUpdate);

        return batchResult;
    }

    private static TransformBatchOperationResult getBatchOperationErrorResult(Throwable cause) {
        TransformBatchOperationResult result;

        // first, extract the underlying cause of the DBIException
        if (cause instanceof DBIException) {
            cause = Databases.findExceptionRootCause((DBIException) cause);
        }

        // then, figure out what to return
        if (cause instanceof PolarisException) {
            PolarisException polarisException = (PolarisException) cause;
            result = new TransformBatchOperationResult(polarisException.getErrorCode(), polarisException.getMessage());
        } else {
            result = new TransformBatchOperationResult(PolarisError.UNKNOWN, cause.getMessage());
        }

        return result;
    }
}
