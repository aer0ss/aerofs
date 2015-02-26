package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.polaris.api.batch.location.LocationBatch;
import com.aerofs.polaris.api.batch.location.LocationBatchOperation;
import com.aerofs.polaris.api.batch.location.LocationBatchOperationResult;
import com.aerofs.polaris.api.batch.location.LocationBatchResult;
import com.aerofs.polaris.logical.ObjectStore;
import com.google.common.collect.Lists;
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

@RolesAllowed(Roles.USER)
@Singleton
public final class LocationBatchResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformBatchResource.class);

    private final ObjectStore store;

    public LocationBatchResource(@Context ObjectStore store) {
        this.store = store;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LocationBatchResult submitBatch(@Context final AeroUserDevicePrincipal principal, LocationBatch batch) {
        List<LocationBatchOperationResult> results = Lists.newArrayListWithCapacity(batch.operations.size());

        for (LocationBatchOperation operation : batch.operations) {
            try {
                store.inTransaction(dao -> {
                    store.performLocationUpdate(dao, principal.getUser(), operation.locationUpdateType, operation.oid, operation.version, operation.did);
                    results.add(new LocationBatchOperationResult());
                    return null;
                });
            } catch (Exception e) {
                LocationBatchOperationResult result = new LocationBatchOperationResult(Resources.getBatchErrorFromThrowable(e));
                LOGGER.warn("fail location batch operation {}", operation, e);
                results.add(result);
                break; // abort batch processing early
            }
        }

        return new LocationBatchResult(results);
    }
}
