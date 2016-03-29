package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.base.BaseLogUtil;
import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.batch.location.*;
import com.aerofs.polaris.api.notification.SyncedLocation;
import com.aerofs.polaris.logical.ObjectStore;
import com.aerofs.polaris.notification.StoreInformationNotifier;
import com.aerofs.polaris.sparta.SpartaAccessManager;
import com.google.common.collect.Lists;

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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RolesAllowed(Roles.USER)
@Singleton
public final class LocationBatchResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformBatchResource.class);

    private final ObjectStore objectStore;
    private final StoreInformationNotifier notifier;
    private final SpartaAccessManager spartaAccessManager;
    private final Set<DID> storageAgentDIDs;
    private long storageAgentDIDsUpdatedTimestamp;
    private final int DELAY = 5000; //milliseconds

    public LocationBatchResource(@Context ObjectStore objectStore, @Context StoreInformationNotifier notifier,
            @Context SpartaAccessManager spartaAccessManager) {
        this.objectStore = objectStore;
        this.notifier = notifier;
        this.spartaAccessManager = spartaAccessManager;
        storageAgentDIDs = ConcurrentHashMap.newKeySet();
        storageAgentDIDsUpdatedTimestamp = 0;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LocationBatchResult submitBatch(@Context final AeroUserDevicePrincipal principal, LocationBatch batch) {
        List<LocationBatchOperationResult> results = Lists.newArrayListWithCapacity(batch.operations.size());
        boolean isFromStorageAgent = principal.getUser().isTeamServerID();

        for (LocationBatchOperation operation : batch.operations) {
            try {
                objectStore.performLocationUpdate(principal.getUser(), operation.locationUpdateType, operation.oid, operation.version, operation.did);
                results.add(new LocationBatchOperationResult());
            } catch (Exception e) {
                Throwable cause = Resources.rootCause(e);
                LocationBatchOperationResult result = new LocationBatchOperationResult(Resources.getBatchErrorFromThrowable(e));
                if (cause instanceof PolarisException || cause instanceof IllegalArgumentException) {
                    LOGGER.info("fail location batch operation {}", operation, BaseLogUtil.suppress(cause));
                } else {
                    LOGGER.warn("unexpected fail location batch operation {}", operation, cause);
                }
                results.add(result);
                break; // abort batch processing early
            }
        }

        if (isFromStorageAgent && batch.operations.size() > 0) {
            notifyLocationsSynced(batch.operations, results);
        }

        return new LocationBatchResult(results);
    }

    @POST
    @Path("/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LocationStatusBatchResult batchStatusCheck(@Context final AeroUserDevicePrincipal principal, LocationStatusBatch batch) {
        List<Boolean> results = new ArrayList<>(batch.operations.size());

        long currentTimeMillis = System.currentTimeMillis();
        if(currentTimeMillis - storageAgentDIDsUpdatedTimestamp > DELAY) {
            storageAgentDIDs.clear();
            spartaAccessManager.getStorageAgentDIDs().forEach(storageAgentDIDs::add);
            storageAgentDIDsUpdatedTimestamp = currentTimeMillis;
        }

        for (LocationStatusBatchOperation operation : batch.operations) {
            try {
                List<DID> locations = objectStore.getLocations(principal.getUser(), operation.oid, operation.version);
                results.add(locations.stream().filter((did) -> storageAgentDIDs.contains(did)).findAny().isPresent());
            } catch (Exception e) {
                results.add(false);
            }
        }

        return new LocationStatusBatchResult(results);
    }

    private void notifyLocationsSynced(List<LocationBatchOperation> operations, final List<LocationBatchOperationResult> results) {
        if (results == null || results.isEmpty()) {
            LOGGER.warn("no locations synced.  TS/SA made empty request or objectStore crashed.");
            return;
        }

        List<SyncedLocation> locations = Lists.newArrayList();
        Iterator<LocationBatchOperation> opsIterator = operations.iterator();
        for (LocationBatchOperationResult result : results) {
            LocationBatchOperation operation = opsIterator.next();
            if (result.successful) {
                locations.add(new SyncedLocation(operation.oid, operation.version));
            }
        }

        if (!locations.isEmpty()) {
            // lookup sid for notification(s)
            UniqueID sid = objectStore.inTransaction(dao -> {
                return objectStore.getStore(dao, operations.get(0).oid);
            });
            notifier.notify("sync/" + sid.toStringFormal(), SyncedLocation.getCollectionBytes(locations),
                    SyncedLocation.SIZE);
        }
    }
}
