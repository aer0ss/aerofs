package com.aerofs.polaris.resources;

import com.aerofs.baseline.auth.AeroPrincipal;
import com.aerofs.baseline.db.DBIExceptions;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.batch.LocationBatch;
import com.aerofs.polaris.api.batch.LocationBatchOperation;
import com.aerofs.polaris.api.batch.LocationBatchOperationResult;
import com.aerofs.polaris.api.batch.LocationBatchResult;
import com.aerofs.polaris.logical.DAO;
import com.aerofs.polaris.logical.LogicalObjectStore;
import com.aerofs.polaris.logical.Transactional;
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

@RolesAllowed(AeroPrincipal.Roles.CLIENT)
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
        final LocationBatchResult batchResult = new LocationBatchResult(batch.operations.size());

        LocationBatchOperation operation = null;

        try {
            for (int i = 0; i < batch.operations.size(); i++) {
                operation = batch.operations.get(i);
                accessManager.checkAccess(principal.getUser(), operation.oid, Access.WRITE);

                final LocationBatchOperation submitted = operation;
                objectStore.inTransaction(new Transactional<Void>() {
                    @Override
                    public Void execute(DAO dao) throws Exception {
                        switch (submitted.locationUpdateType) {
                            case INSERT:
                                objectStore.insertLocation(dao, submitted.oid, submitted.version, submitted.did);
                                break;
                            case REMOVE:
                                objectStore.removeLocation(dao, submitted.oid, submitted.version, submitted.did);
                                break;
                            default:
                                throw new IllegalArgumentException("unhandled location update type " + submitted.locationUpdateType.name());
                        }

                        batchResult.results.add(new LocationBatchOperationResult());

                        return null;
                    }
                });
            }
        } catch (CallbackFailedException e) {
            @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
            Throwable cause = DBIExceptions.findRootCause(e);
            LocationBatchOperationResult result = getBatchOperationErrorResult(cause);
            LOGGER.warn("fail location batch operation {}", operation, e);
            batchResult.results.add(result);
        } catch (Exception e) {
            LocationBatchOperationResult result = getBatchOperationErrorResult(e);
            LOGGER.warn("fail location batch operation {}", operation, e);
            batchResult.results.add(result);
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
