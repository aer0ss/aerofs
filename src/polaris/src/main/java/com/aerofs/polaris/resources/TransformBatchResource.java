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
import com.aerofs.polaris.notification.UpdatePublisher;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
        List<TransformBatchOperationResult> results = Lists.newArrayListWithCapacity(batch.operations.size());

        for (TransformBatchOperation operation: batch.operations) {
            try {
                store.inTransaction(dao -> {
                    List<Updated> updated = store.performTransform(dao, principal.getUser(), principal.getDevice(), operation.oid, operation.operation);
                    results.add(new TransformBatchOperationResult(updated));
                    return null;
                });
            } catch (Exception e) {
                TransformBatchOperationResult result = new TransformBatchOperationResult(Resources.getBatchErrorFromThrowable(e));
                LOGGER.warn("fail transform batch operation {}", operation, e);
                results.add(result);
                break; // abort early if a batch operation fails
            }
        }

        Set<UniqueID> updatedRoots = Sets.newHashSet();

        for (TransformBatchOperationResult result : results) {
            if (result.updated == null) { // in error case; technically I can 'break' here...
                continue;
            }

            for (Updated updated : result.updated) {
                updatedRoots.add(updated.object.root);
            }
        }

        updatedRoots.forEach(publisher::publishUpdate);

        return new TransformBatchResult(results);
    }
}
