package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.batch.transform.TransformBatch;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperation;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperationResult;
import com.aerofs.polaris.api.batch.transform.TransformBatchResult;
import com.aerofs.polaris.api.operation.Updated;
import com.aerofs.polaris.logical.ObjectStore;
import com.aerofs.polaris.notification.Notifier;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
import java.util.Map;
import java.util.stream.Collectors;

@RolesAllowed(Roles.USER)
@Singleton
public final class TransformBatchResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformBatchResource.class);

    private final ObjectStore objectStore;
    private final Notifier notifier;

    public TransformBatchResource(@Context ObjectStore objectStore, @Context Notifier notifier) {
        this.objectStore = objectStore;
        this.notifier = notifier;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TransformBatchResult submitBatch(@Context AeroUserDevicePrincipal principal, TransformBatch batch) {
        List<TransformBatchOperationResult> results = Lists.newArrayListWithCapacity(batch.operations.size());
        Map<UniqueID, Long> updatedStores = Maps.newHashMap();

        for (TransformBatchOperation operation: batch.operations) {
            try {
                List<Updated> updated = objectStore.performTransform(principal.getUser(), principal.getDevice(), operation.oid, operation.operation);
                results.add(new TransformBatchOperationResult(updated));
                updated.stream().collect(Collectors.toMap(x -> x.object.store, x -> x.transformTimestamp, Math::max)).forEach((k, v) -> updatedStores.merge(k, v, Math::max));
            } catch (Exception e) {
                TransformBatchOperationResult result = new TransformBatchOperationResult(Resources.getBatchErrorFromThrowable(e));
                LOGGER.warn("fail transform batch operation {}", operation, e);
                results.add(result);
                break; // abort early if a batch operation fails
            }
        }

        updatedStores.forEach(notifier::notifyStoreUpdated);

        return new TransformBatchResult(results);
    }
}
