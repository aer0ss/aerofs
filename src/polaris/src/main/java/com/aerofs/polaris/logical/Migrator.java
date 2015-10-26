package com.aerofs.polaris.logical;

import com.aerofs.baseline.Managed;
import com.aerofs.ids.*;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.notification.Update;
import com.aerofs.polaris.api.types.*;
import com.aerofs.polaris.dao.Atomic;
import com.aerofs.polaris.dao.LockStatus;
import com.aerofs.polaris.dao.Migrations;
import com.aerofs.polaris.dao.types.LockableLogicalObject;
import com.aerofs.polaris.notification.Notifier;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.*;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.ResultIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;

//TODO (RD) make this threadsafe and bump the executor to be multithreaded
// as it is now, upon a server crash there's no guarantee of what order the jobs will be restarted in - leading to issues if two jobs overlap
@Singleton
public class Migrator implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStore.class);
    private final DBI dbi;
    private final Notifier notifier;
    private final ListeningExecutorService executor;

    @Inject
    public Migrator(DBI dbi, Notifier notifier)
    {
        this(dbi, notifier, MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()));
    }

    public Migrator(DBI dbi, Notifier notifier, ListeningExecutorService executor)
    {
        this.dbi = dbi;
        this.notifier = notifier;
        this.executor = executor;
    }

    @Override
    public void start() throws Exception {
        LOGGER.info("starting migrator");
        resumeMigrations();
    }

    @Override
    public void stop() {
        LOGGER.info("stopping migrator");
        executor.shutdown();
    }

    public JobStatus getJobStatus(UniqueID jobID)
    {
        MigrationJob job = dbi.inTransaction((conn, status) -> conn.attach(Migrations.class).getJob(jobID));
        if (job == null) {
            throw new NotFoundException(jobID);
        }
        return job.status;
    }

    /*
     * @return the Job ID associated with this migration
     */
    public UniqueID migrateStore(DAO dao, SID storeSID, DID originator)
    {
        UniqueID jobID = UniqueID.generate();
        dao.migrations.addMigration(SID.convertedStoreSID2folderOID(storeSID), storeSID, jobID, originator, JobStatus.RUNNING);
        startStoreMigration(storeSID, jobID, originator);
        return jobID;
    }

    /*
     * @return the Job ID associated with this migration
     */
    public UniqueID moveCrossStore(DAO dao, UniqueID child, OID destination, DID originator)
    {
        UniqueID jobID = UniqueID.generate();
        dao.migrations.addMigration(child, destination, jobID, originator, JobStatus.RUNNING);
        dao.migrations.addOidMapping(child, destination, jobID);
        startFolderMigration(child, destination, jobID, originator);
        return jobID;
    }

    /*
     * @return the Job ID associated with this restore
     */
    public UniqueID restoreMigratedObjects(DAO dao, UniqueID restored, DID originator)
    {
        UniqueID jobID = UniqueID.generate();
        dao.migrations.addMigration(restored, restored, jobID, originator, JobStatus.RUNNING);
        startRestore(restored, jobID);
        return jobID;
    }

    private void resumeMigrations()
    {
        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            ResultIterator<MigrationJob> activeJobs = dao.migrations.activeMigrations();
            while (activeJobs.hasNext()) {
                MigrationJob job = activeJobs.next();
                if (job.isSharingJob()) {
                    startStoreMigration(new SID(job.to), job.jobID, job.originator);
                } else if (job.isRestoreJob()) {
                    startRestore(job.from, job.jobID);
                } else {
                    startFolderMigration(job.from, new OID(job.to), job.jobID, job.originator);
                }
            }
            return null;
        });
    }

    // package-private for testing purposes
    void startStoreMigration(SID storeSID, UniqueID jobID, DID originator)
    {
        LOGGER.info("starting store migration for {}", storeSID);
        ListenableFuture<Void> future = executor.submit(() -> {
            Update update = updateStoreIDs(originator, storeSID);
            if (update != null) {
                notifier.notifyStoreUpdated(update.store, update.latestLogicalTimestamp);
            }
            update = createShareTransformForStore(originator, storeSID);
            notifier.notifyStoreUpdated(update.store, update.latestLogicalTimestamp);
            return null;
        });

        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOGGER.info("finished migration of store {}", storeSID);
                dbi.inTransaction((conn, status) -> conn.attach(Migrations.class).updateStatus(jobID, JobStatus.COMPLETED));
            }

            @Override
            public void onFailure(Throwable t) {
                LOGGER.warn("failed migration of store {}, retrying", storeSID, t);
                startStoreMigration(storeSID, jobID, originator);
            }
        });
    }

    // package-private for testing purposes
    void startFolderMigration(UniqueID migratingFolder, OID destination, UniqueID jobID, DID originator)
    {
        LOGGER.info("starting cross-store migration from {} to {}", migratingFolder, destination);
        ListenableFuture<Void> future = executor.submit(() -> {
            Update update = moveCrossStore(migratingFolder, destination, jobID, originator);
            if (update != null) {
                notifier.notifyStoreUpdated(update.store, update.latestLogicalTimestamp);
            }
            update = removeMigratedFolder(migratingFolder, destination, originator);
            notifier.notifyStoreUpdated(update.store, update.latestLogicalTimestamp);
            return null;
        });

        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOGGER.info("finished cross-store migration from {} to {}", migratingFolder, destination);
                dbi.inTransaction((conn, status) -> {
                    Migrations migrations = conn.attach(Migrations.class);
                    migrations.updateStatus(jobID, JobStatus.COMPLETED);
                    migrations.clearAllMappingsForJob(jobID);
                    return null;
                });
            }

            @Override
            public void onFailure(Throwable t) {
                LOGGER.warn("failed cross-store migration from {} to {}, retrying", migratingFolder, destination, t);
                startFolderMigration(migratingFolder, destination, jobID, originator);
            }
        });
    }

    void startRestore(UniqueID restored, UniqueID jobID)
    {
        LOGGER.info("starting restore job of subtree from {}", restored);
        ListenableFuture<Void> future = executor.submit(() -> {
            restoreObjectsUnder(restored);
            return null;
        });
        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOGGER.info("finished restoring objects under {}", restored);
                dbi.inTransaction((conn, status) -> conn.attach(Migrations.class).updateStatus(jobID, JobStatus.COMPLETED));
            }

            @Override
            public void onFailure(Throwable t) {
                LOGGER.warn("failed restoring objects under {}, retrying", restored, t);
                startRestore(restored, jobID);
            }
        });
    }

    private @Nullable Update updateStoreIDs(DID originator, SID store) {
        Queue<ChildMigrationState> migrationQueue = Queues.newArrayDeque();
        Map<UniqueID, Long> versionMap = Maps.newHashMap();

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            LogicalObject oldFolder = getObject(dao, SID.convertedStoreSID2folderOID(store));
            LogicalObject storeRoot = getObject(dao, store);
            versionMap.put(storeRoot.oid, storeRoot.version);
            lockChildrenAndAddToQueue(dao, migrationQueue, oldFolder.oid, true);
            lockChildrenAndAddToQueue(dao, migrationQueue, store, true);
            return null;
        });

        Update update = null;
        while (!migrationQueue.isEmpty()) {
            Update result = dbi.inTransaction((conn, status) -> updateStoreForBatch(new DAO(conn), originator, store, migrationQueue, versionMap));
            if (result != null) {
                update = result;
            }
        }
        return update;
    }

    private Update createShareTransformForStore(DID originator, SID store) {
        OID folder = SID.convertedStoreSID2folderOID(store);
        return dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);

            LogicalObject oldFolder = getObject(dao, folder);
            dao.objects.update(oldFolder.store, folder, oldFolder.version + 1);
            long logicalTimestamp = dao.transforms.add(originator, oldFolder.store, folder, TransformType.SHARE, oldFolder.version + 1, null, null, null, System.currentTimeMillis(), null);
            dao.logicalTimestamps.updateLatest(oldFolder.store, logicalTimestamp);

            return new Update(oldFolder.store, logicalTimestamp);
        });
    }

    private void lockChildrenAndAddToQueue(DAO dao, Queue<ChildMigrationState> queue, UniqueID parent, boolean persistLocking) {
        try (ResultIterator<DeletableChild> oldChildren = dao.children.getChildren(parent)) {
            while (oldChildren.hasNext()) {
                DeletableChild child = oldChildren.next();
                dao.objects.setLocked(child.oid, LockStatus.LOCKED);
                queue.add(new ChildMigrationState(parent, child, persistLocking));
            }
        }
    }

    private Update moveCrossStore(UniqueID migrant, OID destination, UniqueID jobID, DID originator) {
        Queue<ChildMigrationState> queue = Queues.newArrayDeque();
        Map<UniqueID, Long> versionMap = Maps.newHashMap();
        Map<UniqueID, UniqueID> newOIDs = Maps.newHashMap();

        UniqueID newStore = dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);

            try (ResultIterator<IDPair> oldMappings = dao.migrations.getAllOIDMappingsForJob(jobID)) {
                while (oldMappings.hasNext()) {
                    IDPair mapping = oldMappings.next();
                    newOIDs.put(mapping.oldOID, mapping.newOID);
                }
            }

            LogicalObject migrantRoot = getObject(dao, migrant);
            boolean persistLocking = migrantRoot.objectType != ObjectType.STORE;
            lockChildrenAndAddToQueue(dao, queue, migrant, persistLocking);
            if (!persistLocking) {
                dao.objects.setLocked(migrant, LockStatus.UNLOCKED);
            }

            LogicalObject destinationRoot = getObject(dao, destination);
            versionMap.put(destinationRoot.oid, destinationRoot.version);
            return destinationRoot.store;
        });

        Update update = null;
        while (!queue.isEmpty()) {
            Update result = dbi.inTransaction((conn, status) -> moveBatchCrossStore(new DAO(conn), originator, newStore, queue, versionMap, new IDMap(jobID, newOIDs)));
            if (result != null) {
                update = result;
            }
        }
        return update;
    }

    private Update removeMigratedFolder(UniqueID migrant, UniqueID destination, DID originator)
    {
        return dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            UniqueID originalParentID = dao.children.getParent(migrant);
            Preconditions.checkState(originalParentID != null, "could not find original parent of migrating object");
            LogicalObject originalParent = getObject(dao, originalParentID);
            ObjectType migratingObjectType = dao.objectTypes.get(migrant);

            if (migratingObjectType != ObjectType.STORE && dao.children.getActiveReferenceCount(migrant) == 1) {
                dao.children.setDeleted(migrant, true);
            } else {
                dao.children.remove(originalParentID, migrant);
            }

            long logicalTimestamp = dao.transforms.add(originator, originalParent.store, originalParentID, TransformType.REMOVE_CHILD, originalParent.version + 1, migrant, null, destination, System.currentTimeMillis(), null);
            dao.logicalTimestamps.updateLatest(originalParent.store, logicalTimestamp);
            return new Update(originalParent.store, logicalTimestamp);
        });
    }

    private void removeAnchor(DAO dao, DID originator, UniqueID parent, UniqueID anchor) {
        LogicalObject parentObject = getObject(dao, parent);
        dao.transforms.add(originator, parentObject.store, parentObject.oid, TransformType.REMOVE_CHILD, parentObject.version + 1, anchor, null, null, System.currentTimeMillis(), null);
        dao.mountPoints.remove(parentObject.store, anchor);
        dao.children.remove(parent, anchor);
        dao.objects.update(parentObject.store, parent, parentObject.version + 1);
    }

    private void restoreObjectsUnder(UniqueID restored) {
        Queue<UniqueID> restoreQueue = Queues.newArrayDeque();
        restoreQueue.add(restored);
        while (!restoreQueue.isEmpty()) {
            dbi.inTransaction((conn, status) -> {
                restoreBatch(new DAO(conn), restoreQueue);
                return null;
            });
        }
    }

    // package-private for testing purposes
    @Nullable Update updateStoreForBatch(DAO dao, DID originator, SID newStore, Queue<ChildMigrationState> searchQueue, Map<UniqueID, Long> versionMap)
    {
        Preconditions.checkArgument(!searchQueue.isEmpty(), "tried to migrate an empty queue of objects");
        LOGGER.debug("performing batch migration of objects to {}", newStore);

        int operationsDone = 0;
        long migratingToTimestamp = -1;
        UniqueID convertedFolderOID = SID.convertedStoreSID2folderOID(newStore);

        while (!searchQueue.isEmpty() && operationsDone < Constants.MIGRATION_OPERATION_BATCH_SIZE) {
            ChildMigrationState next = searchQueue.remove();
            LogicalObject migrating = getObject(dao, next.child.oid);

            if (!migrating.store.equals(newStore)) {
                UniqueID newParent = next.parent;
                long newParentVersion;
                LOGGER.trace("migrating object {} to store {}", migrating.oid, newStore);

                if (next.parent.equals(convertedFolderOID)) {
                    // need to move the object from the old folder to the new store root
                    dao.children.add(newStore, migrating.oid, next.child.name, next.child.deleted);
                    dao.children.remove(convertedFolderOID, migrating.oid);
                    newParent = newStore;
                }
                newParentVersion = versionMap.get(newParent);

                Atomic atomic = next.child.deleted ? new Atomic(2) : null;
                migratingToTimestamp = dao.transforms.add(originator, newStore, newParent, TransformType.INSERT_CHILD, ++newParentVersion, migrating.oid, next.child.name, null, System.currentTimeMillis(), atomic);
                if (next.child.deleted) {
                    migratingToTimestamp = dao.transforms.add(originator, newStore, newParent, TransformType.REMOVE_CHILD, ++newParentVersion, migrating.oid, null, null, System.currentTimeMillis(), atomic);
                } else if (migrating.objectType == ObjectType.FILE && migrating.version > Constants.INITIAL_OBJECT_VERSION) {
                    migratingToTimestamp = dao.transforms.add(originator, newStore, migrating.oid, TransformType.UPDATE_CONTENT, migrating.version, null, null, null, System.currentTimeMillis(), null);
                }

                dao.objects.changeStore(newStore, migrating.oid);
                dao.objects.update(newStore, newParent, newParentVersion);
                versionMap.replace(newParent, newParentVersion);
            }

            if (migrating.objectType == ObjectType.FOLDER) {
                versionMap.put(migrating.oid, migrating.version);
                try (ResultIterator<DeletableChild> children = dao.children.getChildren(migrating.oid)) {
                    while (children.hasNext()) {
                        DeletableChild child = children.next();
                        dao.objects.setLocked(child.oid, LockStatus.LOCKED);
                        searchQueue.add(new ChildMigrationState(migrating.oid, child));
                    }
                }
            }

            dao.objects.setLocked(migrating.oid, LockStatus.UNLOCKED);
            operationsDone++;
        }

        // meaning that we did migrate some objects in this batch
        if (migratingToTimestamp != -1) {
            dao.logicalTimestamps.updateLatest(newStore, migratingToTimestamp);
            return new Update(newStore, migratingToTimestamp);
        } else {
            return null;
        }
    }

    private @Nullable Update moveBatchCrossStore(DAO dao, DID originator, UniqueID newStore, Queue<ChildMigrationState> searchQueue, Map<UniqueID, Long> versionMap, IDMap newOIDs)
    {
        Preconditions.checkArgument(!searchQueue.isEmpty(), "tried to move an empty queue of objects");
        LOGGER.debug("performing batch cross store migration for job {}", newOIDs.jobID);

        int operationsDone = 0;
        long migratingToTimestamp = -1;

        while (!searchQueue.isEmpty() && operationsDone < Constants.MIGRATION_OPERATION_BATCH_SIZE) {
            ChildMigrationState next = searchQueue.remove();
            LogicalObject migrating = getObject(dao, next.child.oid);

            if (!newOIDs.containsKey(migrating.oid)) {
                LOGGER.trace("migrating object {}", migrating.oid);
                UniqueID newParent = newOIDs.get(next.parent);
                long newParentVersion = versionMap.get(newParent);
                OID newOID = OID.generate();

                dao.objects.add(newStore, newOID, migrating.version);
                // migrated anchors become normal folders when migrated
                dao.objectTypes.add(newOID, migrating.objectType == ObjectType.STORE ? ObjectType.FOLDER : migrating.objectType);
                dao.children.add(newParent, newOID, next.child.name, next.child.deleted);

                migratingToTimestamp = dao.transforms.add(originator, newStore, newParent, TransformType.INSERT_CHILD, ++newParentVersion, newOID, next.child.name, migrating.oid, System.currentTimeMillis(), null);
                if (next.child.deleted) {
                    migratingToTimestamp = dao.transforms.add(originator, newStore, newParent, TransformType.REMOVE_CHILD, ++newParentVersion, newOID, null, null, System.currentTimeMillis(), null);
                } else if (migrating.objectType == ObjectType.FILE && migrating.version > Constants.INITIAL_OBJECT_VERSION) {
                    Content oldFile = dao.objectProperties.getLatest(migrating.oid);
                    Preconditions.checkState(oldFile != null, "could not find latest file content for migrating file %s", migrating.oid);
                    Preconditions.checkState(oldFile.version == migrating.version, "found conflicting versions for migrating file %s", migrating.oid);
                    migratingToTimestamp = dao.transforms.add(originator, newStore, newOID, TransformType.UPDATE_CONTENT, oldFile.version, null, null, null, System.currentTimeMillis(), null);
                    dao.objectProperties.add(newOID, oldFile.version, oldFile.hash, oldFile.size, oldFile.mtime);
                    dao.objects.update(newStore, newOID, oldFile.version);
                }

                if (migrating.objectType == ObjectType.STORE) {
                    // auto-leave shared folders upon them being migrated and converted to normal folders
                    removeAnchor(dao, originator, next.parent, migrating.oid);
                }

                dao.objects.update(newStore, newParent, newParentVersion);
                versionMap.replace(newParent, newParentVersion);
                newOIDs.put(dao, migrating.oid, newOID);
            }

            if (migrating.objectType == ObjectType.FOLDER || migrating.objectType == ObjectType.STORE) {
                versionMap.put(newOIDs.get(migrating.oid), migrating.version);
                boolean persistLocking = next.persistentLock && migrating.objectType != ObjectType.STORE;
                lockChildrenAndAddToQueue(dao, searchQueue, migrating.oid, persistLocking);
            }

            if (!next.persistentLock || migrating.objectType == ObjectType.STORE) {
                dao.objects.setLocked(migrating.oid, LockStatus.UNLOCKED);
            } else {
                dao.objects.setLocked(migrating.oid, LockStatus.MIGRATED);
            }
            operationsDone++;
        }

        // meaning that we did migrate some objects in this batch
        if (migratingToTimestamp != -1) {
            dao.logicalTimestamps.updateLatest(newStore, migratingToTimestamp);
            return new Update(newStore, migratingToTimestamp);
        } else {
            return null;
        }
    }

    private void restoreBatch(DAO dao, Queue<UniqueID> queue)
    {
        Preconditions.checkArgument(!queue.isEmpty(), "tried to move an empty queue of objects");
        LOGGER.debug("performing batch restore of migrated objects");

        int operationsDone = 0;

        while (!queue.isEmpty() && operationsDone < Constants.MIGRATION_OPERATION_BATCH_SIZE) {
            UniqueID toRestore = queue.remove();
            LockableLogicalObject restoredObject = getObject(dao, toRestore);
            if (restoredObject.locked == LockStatus.MIGRATED) {
                dao.objects.setLocked(toRestore, LockStatus.UNLOCKED);
            }
            // TODO (RD) do something when encounter a LOCKED obj?

            // N.B. we don't recurse into STORE types here because those (and their subtrees) are not persistently locked by migration
            if (restoredObject.objectType == ObjectType.FOLDER) {
                try (ResultIterator<DeletableChild> children = dao.children.getChildren(toRestore)) {
                    while (children.hasNext()) {
                        queue.add(children.next().oid);
                    }
                }
            }
            operationsDone++;
        }
    }

    private LockableLogicalObject getObject(DAO dao, UniqueID oid) {
        LockableLogicalObject object = dao.objects.get(oid);
        if (object == null) {
            throw new NotFoundException(oid);
        }
        return object;
    }

    private static class ChildMigrationState
    {
        final UniqueID parent;
        final DeletableChild child;
        boolean persistentLock = false;

        public ChildMigrationState(UniqueID parent, DeletableChild child)
        {
            this.parent = parent;
            this.child = child;
        }

        public ChildMigrationState(UniqueID parent, DeletableChild child, boolean persistentLock)
        {
            this.parent = parent;
            this.child = child;
            this.persistentLock = persistentLock;
        }
    }

    public static class MigrationJob
    {
        final DID originator;
        final UniqueID from;
        final UniqueID to;
        final UniqueID jobID;
        final JobStatus status;

        public MigrationJob(UniqueID from, UniqueID to, UniqueID jobID, DID originator, JobStatus status)
        {
            this.from = from;
            this.to = to;
            this.jobID = jobID;
            this.originator = originator;
            this.status = status;
        }

        boolean isSharingJob() {
            return SID.folderOID2convertedStoreSID(new OID(this.from)).equals(this.to);
        }

        boolean isRestoreJob() {
            return this.from.equals(this.to);
        }
    }

    public static class IDPair
    {
        final UniqueID oldOID;
        final UniqueID newOID;

        public IDPair(UniqueID oldOID, UniqueID newOID)
        {
            this.oldOID = oldOID;
            this.newOID = newOID;
        }
    }

    private static class IDMap
    {
        final UniqueID jobID;
        final Map<UniqueID, UniqueID> map;

        IDMap(UniqueID jobID, Map<UniqueID, UniqueID> map)
        {
            this.jobID = jobID;
            this.map = map;
        }

        UniqueID get(UniqueID oldID)
        {
            return map.get(oldID);
        }

        void put(DAO dao, UniqueID oldID, OID newID)
        {
            map.put(oldID, newID);
            dao.migrations.addOidMapping(oldID, newID, jobID);
        }

        boolean containsKey(UniqueID ID)
        {
            return map.containsKey(ID);
        }
    }
}
