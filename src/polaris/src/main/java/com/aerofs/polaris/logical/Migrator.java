package com.aerofs.polaris.logical;

import com.aerofs.baseline.Managed;
import com.aerofs.ids.*;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.notification.Update;
import com.aerofs.polaris.api.types.*;
import com.aerofs.polaris.dao.Atomic;
import com.aerofs.polaris.dao.Migrations;
import com.aerofs.polaris.notification.Notifier;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
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
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;

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
        // we use the store ID as a job ID
        MigrationJob job = dbi.inTransaction((conn, status) -> {
            Migrations migrations = conn.attach(Migrations.class);
            return migrations.getJob(jobID);
        });
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
        dao.migrations.addStoreMigration(SID.convertedStoreSID2folderOID(storeSID), storeSID, jobID, originator, JobStatus.RUNNING);
        startStoreMigration(storeSID, jobID, originator);
        return jobID;
    }

    public UniqueID moveCrossStore(DAO dao, UniqueID child, OID destination, DID originator)
    {
        UniqueID jobID = UniqueID.generate();
        dao.migrations.addFolderMigration(child, destination, jobID, originator, JobStatus.RUNNING);
        dao.migrations.addOidMapping(child, destination, jobID);
        startFolderMigration(child, destination, jobID, originator);
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
            update = markOldFolderAsStore(originator, storeSID);
            notifier.notifyStoreUpdated(update.store, update.latestLogicalTimestamp);
            return null;
        });

        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOGGER.info("finished migration of store {}", storeSID);
                dbi.inTransaction((conn, status) -> {
                    conn.attach(Migrations.class).updateStatus(jobID, JobStatus.COMPLETED);
                    return null;
                });
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
            List<Update> updates = updateOIDs(migratingFolder, destination, jobID, originator);
            updates.forEach((x) -> notifier.notifyStoreUpdated(x.store, x.latestLogicalTimestamp));
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

    private @Nullable Update updateStoreIDs(DID originator, SID store) {
        Queue<ChildMigrationState> migrationQueue = Queues.newArrayDeque();
        Map<UniqueID, Long> versionMap = Maps.newHashMap();

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            LogicalObject oldFolder = getObject(dao, SID.convertedStoreSID2folderOID(store));
            try (ResultIterator<DeletableChild> oldChildren = dao.children.getChildren(oldFolder.oid)) {
                while (oldChildren.hasNext()) {
                    DeletableChild child = oldChildren.next();
                    dao.objects.setLocked(child.oid, true);
                    migrationQueue.add(new ChildMigrationState(oldFolder.oid, child));
                }
            }

            LogicalObject storeRoot = getObject(dao, store);
            versionMap.put(storeRoot.oid, storeRoot.version);

            try (ResultIterator<DeletableChild> rootChildren = dao.children.getChildren(store)) {
                while (rootChildren.hasNext()) {
                    DeletableChild child = rootChildren.next();
                    dao.objects.setLocked(child.oid, true);
                    migrationQueue.add(new ChildMigrationState(storeRoot.oid, child));
                }
            }
            return null;
        });

        Update update = null;
        while (!migrationQueue.isEmpty()) {
            Update result = dbi.inTransaction((conn, status) -> migrateBatchOfObjects(new DAO(conn), originator, store, migrationQueue, versionMap));
            if (result != null) {
                update = result;
            }
        }

        return update;
    }

    private Update markOldFolderAsStore(DID originator, SID store) {
        OID folder = SID.convertedStoreSID2folderOID(store);
        return dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            LogicalObject oldFolder = getObject(dao, folder);

            long logicalTimestamp = dao.transforms.add(originator, oldFolder.store, folder, TransformType.SHARE, oldFolder.version + 1, null, null, System.currentTimeMillis(), null);
            dao.objects.update(oldFolder.store, folder, oldFolder.version + 1);

            return new Update(oldFolder.store, logicalTimestamp);
        });
    }

    private List<Update> updateOIDs(UniqueID migrant, OID destination, UniqueID jobID, DID originator) {
        Queue<ChildMigrationState> migrationQueue = Queues.newArrayDeque();
        Map<UniqueID, Long> versionMap = Maps.newHashMap();
        UniqueID newStore = dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            dao.objects.setLocked(migrant, true);
            LogicalObject migrantRoot = getObject(dao, migrant);
            LogicalObject destinationRoot = getObject(dao, destination);
            versionMap.put(destinationRoot.oid, destinationRoot.version);

            try (ResultIterator<DeletableChild> migrantChildren = dao.children.getChildren(migrant)) {
                while (migrantChildren.hasNext()) {
                    DeletableChild child = migrantChildren.next();
                    dao.objects.setLocked(child.oid, true);
                    migrationQueue.add(new ChildMigrationState(migrantRoot.oid, child));
                }
            }

            return destinationRoot.store;
        });

        Map<UniqueID, UniqueID> newOIDs = Maps.newHashMap();
        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            try (ResultIterator<IDPair> oldMappings = dao.migrations.getAllOIDMappingsForJob(jobID)) {
                while (oldMappings.hasNext()) {
                    IDPair mapping = oldMappings.next();
                    newOIDs.put(mapping.oldOID, mapping.newOID);
                }
            }
            return null;
        });

        Update newStoreUpdate = null;
        while (!migrationQueue.isEmpty()) {
            Update result = dbi.inTransaction((conn, status) -> {
                DAO dao = new DAO(conn);
                return migrateBatchOfObjectsWithNewOIDs(dao, originator, newStore, migrationQueue, versionMap, new IDMap(jobID, newOIDs));
            });
            if (result != null) {
                newStoreUpdate = result;
            }
        }

        Update oldStoreUpdate = dbi.inTransaction((conn, status) -> {
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

            newOIDs.forEach((oldOid, newOid) -> {
                if (Identifiers.isMountPoint(oldOid)) {
                    dao.mountPoints.remove(originalParent.store, oldOid);
                }
            });

            return new Update(originalParent.store, dao.transforms.add(originator, originalParent.store, originalParentID, TransformType.REMOVE_CHILD, originalParent.version + 1, migrant, null, System.currentTimeMillis(), null));
        });

        List<Update> updates = Lists.newArrayList(oldStoreUpdate);
        if (newStoreUpdate != null) {
            updates.add(newStoreUpdate);
        }
        return updates;
    }

    // package-private for testing purposes
    @Nullable Update migrateBatchOfObjects(DAO dao, DID originator, SID newStore, Queue<ChildMigrationState> searchQueue, Map<UniqueID, Long> versionMap)
    {
        Preconditions.checkArgument(!searchQueue.isEmpty(), "tried to migrate an empty queue of objects");
        LOGGER.debug("performing batch migration of objects to {}", newStore);

        int operationsDone = 0;
        long migratingToTimestamp = -1;
        UniqueID convertedFolderOID = SID.convertedStoreSID2folderOID(newStore);

        while (!searchQueue.isEmpty() && operationsDone < Constants.MIGRATION_OPERATION_BATCH_SIZE) {
            ChildMigrationState migrating = searchQueue.remove();
            LogicalObject migratingObject = getObject(dao, migrating.child.oid);

            if (!migratingObject.store.equals(newStore)) {
                UniqueID newParent = migrating.parent;
                long newParentVersion;
                LOGGER.trace("migrating object {} to store {}", migratingObject.oid, newStore);

                if (migrating.parent.equals(convertedFolderOID)) {
                    // need to move the object from the old folder to the new store root
                    dao.children.add(newStore, migratingObject.oid, migrating.child.name, migrating.child.deleted);
                    dao.children.remove(convertedFolderOID, migratingObject.oid);
                    newParent = newStore;
                }
                newParentVersion = versionMap.get(newParent);

                Atomic atomic = migrating.child.deleted ? new Atomic(2) : null;
                migratingToTimestamp = dao.transforms.add(originator, newStore, newParent, TransformType.INSERT_CHILD, ++newParentVersion, migratingObject.oid, migrating.child.name, System.currentTimeMillis(), atomic);
                if (migrating.child.deleted) {
                    migratingToTimestamp = dao.transforms.add(originator, newStore, newParent, TransformType.REMOVE_CHILD, ++newParentVersion, migratingObject.oid, null, System.currentTimeMillis(), atomic);
                } else if (migratingObject.objectType == ObjectType.FILE && migratingObject.version > Constants.INITIAL_OBJECT_VERSION) {
                    migratingToTimestamp = dao.transforms.add(originator, newStore, migratingObject.oid, TransformType.UPDATE_CONTENT, migratingObject.version, null, null, System.currentTimeMillis(), null);
                }

                dao.objects.changeStore(newStore, migratingObject.oid);
                dao.objects.update(newStore, newParent, newParentVersion);
                versionMap.replace(newParent, newParentVersion);
            }

            if (migratingObject.objectType == ObjectType.FOLDER) {
                versionMap.put(migratingObject.oid, migratingObject.version);
                try (ResultIterator<DeletableChild> children = dao.children.getChildren(migratingObject.oid)) {
                    while (children.hasNext()) {
                        DeletableChild child = children.next();
                        dao.objects.setLocked(child.oid, true);
                        searchQueue.add(new ChildMigrationState(migratingObject.oid, child));
                    }
                }
            }

            dao.objects.setLocked(migratingObject.oid, false);
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

    private @Nullable Update migrateBatchOfObjectsWithNewOIDs(DAO dao, DID originator, UniqueID newStore, Queue<ChildMigrationState> searchQueue, Map<UniqueID, Long> versionMap, IDMap newOIDs)
    {
        Preconditions.checkArgument(!searchQueue.isEmpty(), "tried to move an empty queue of objects");
        LOGGER.debug("performing batch cross store migration for job {}", newOIDs.jobID);

        int operationsDone = 0;
        long migratingToTimestamp = -1;

        while (!searchQueue.isEmpty() && operationsDone < Constants.MIGRATION_OPERATION_BATCH_SIZE) {
            ChildMigrationState migrating = searchQueue.remove();
            LogicalObject migratingObject = getObject(dao, migrating.child.oid);

            if (!newOIDs.containsKey(migratingObject.oid)) {
                LOGGER.trace("migrating object {}", migratingObject.oid);
                UniqueID newParent = newOIDs.get(migrating.parent);
                long newParentVersion = versionMap.get(newParent);
                OID newOID = OID.generate();

                dao.objects.add(newStore, newOID, migratingObject.version);
                // migrated anchors become normal folders
                dao.objectTypes.add(newOID, migratingObject.objectType == ObjectType.STORE ? ObjectType.FOLDER : migratingObject.objectType);
                dao.children.add(newParent, newOID, migrating.child.name, migrating.child.deleted);

                // TODO(RD): atomic operations?
                migratingToTimestamp = dao.transforms.add(originator, newStore, newParent, TransformType.INSERT_CHILD, ++newParentVersion, newOID, migrating.child.name, System.currentTimeMillis(), null);
                if (migrating.child.deleted) {
                    migratingToTimestamp = dao.transforms.add(originator, newStore, newParent, TransformType.REMOVE_CHILD, ++newParentVersion, newOID, null, System.currentTimeMillis(), null);
                } else if (migratingObject.objectType == ObjectType.FILE && migratingObject.version > Constants.INITIAL_OBJECT_VERSION) {
                    Content oldFile = dao.objectProperties.getLatest(migratingObject.oid);
                    Preconditions.checkState(oldFile != null, "could not find latest file content for migrating file %s", migratingObject.oid);
                    migratingToTimestamp = dao.transforms.add(originator, newStore, newOID, TransformType.UPDATE_CONTENT, migratingObject.version, null, null, System.currentTimeMillis(), null);
                    dao.objectProperties.add(newOID, migratingObject.version, oldFile.hash, oldFile.size, oldFile.mtime);
                }

                dao.objects.update(newStore, newParent, newParentVersion);
                versionMap.replace(newParent, newParentVersion);

                newOIDs.put(migratingObject.oid, newOID, dao.migrations);
            }

            if (migratingObject.objectType == ObjectType.FOLDER || migratingObject.objectType == ObjectType.STORE) {
                versionMap.put(newOIDs.get(migratingObject.oid), migratingObject.version);
                try (ResultIterator<DeletableChild> children = dao.children.getChildren(migrating.child.oid)) {
                    while (children.hasNext()) {
                        DeletableChild child = children.next();
                        dao.objects.setLocked(child.oid, true);
                        searchQueue.add(new ChildMigrationState(migratingObject.oid, child));
                    }
                }
            }

            dao.objects.setLocked(migratingObject.oid, false);
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

    private LogicalObject getObject(DAO dao, UniqueID oid) {
        LogicalObject object = dao.objects.get(oid);
        if (object == null) {
            throw new NotFoundException(oid);
        }
        return object;
    }

    private static class ChildMigrationState
    {
        final UniqueID parent;
        final DeletableChild child;

        public ChildMigrationState(UniqueID parent, DeletableChild child)
        {
            this.parent = parent;
            this.child = child;
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

        void put(UniqueID oldID, OID newID, Migrations db)
        {
            map.put(oldID, newID);
            db.addOidMapping(oldID, newID, jobID);
        }

        boolean containsKey(UniqueID ID)
        {
            return map.containsKey(ID);
        }
    }
}
