package com.aerofs.polaris.resources;

import com.aerofs.baseline.auth.aero.AeroPrincipal;
import com.aerofs.baseline.auth.aero.Roles;
import com.aerofs.baseline.db.DBIExceptions;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.batch.LocationBatch;
import com.aerofs.polaris.api.batch.LocationBatchOperation;
import com.aerofs.polaris.api.batch.LocationBatchOperationResult;
import com.aerofs.polaris.api.batch.LocationBatchResult;
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

@RolesAllowed(Roles.CLIENT)
@Singleton
public final class LocationBatchResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformBatchResource.class);

    private final LogicalObjectStore objectStore;
    private final AccessManager accessManager;

    public LocationBatchResource(@Context LogicalObjectStore objectStore, @Context AccessManager accessManager) {
        this.objectStore = objectStore;
        this.accessManager = accessManager;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LocationBatchResult submitBatch(@Context @NotNull final AeroPrincipal principal, @NotNull final LocationBatch batch) {
        List<LocationBatchOperation> operations = batch.getOperations();
        final LocationBatchResult batchResult = new LocationBatchResult(operations.size());

        LocationBatchOperation operation = null;

        try {
            for (int i = 0; i < operations.size(); i++) {
                operation = operations.get(i);
                accessManager.checkAccess(principal.getDevice(), operation.getOid(), Access.WRITE);

                final LocationBatchOperation submitted = operation;
                objectStore.inTransaction(dao -> {
                    switch (submitted.getLocationUpdateType()) {
                        case INSERT:
                            objectStore.insertLocation(dao, submitted.getOid(), submitted.getVersion(), submitted.getDid());
                            break;
                        case REMOVE:
                            objectStore.removeLocation(dao, submitted.getOid(), submitted.getVersion(), submitted.getDid());
                            break;
                        default:
                            throw new IllegalArgumentException("unhandled location update type " + submitted.getLocationUpdateType().name());
                    }

                    batchResult.getResults().add(new LocationBatchOperationResult());

                    return null;
                });
            }
        } catch (CallbackFailedException e) {
            @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
            Throwable cause = DBIExceptions.findRootCause(e);
            LocationBatchOperationResult result = getBatchOperationErrorResult(cause);
            LOGGER.warn("fail location batch operation {}", operation, e);
            batchResult.getResults().add(result);
        } catch (Exception e) {
            LocationBatchOperationResult result = getBatchOperationErrorResult(e);
            LOGGER.warn("fail location batch operation {}", operation, e);
            batchResult.getResults().add(result);
        }

        return batchResult;
    }

    private static LocationBatchOperationResult getBatchOperationErrorResult(Throwable cause) {
        LocationBatchOperationResult result;

        if (cause instanceof PolarisException) {
            PolarisException polarisException = (PolarisException) cause;
            result = new LocationBatchOperationResult(polarisException.getErrorCode(), polarisException.getMessage());
        } else {
            result = new LocationBatchOperationResult(PolarisError.UNKNOWN, cause.getMessage());
        }

        return result;
    }
}
