package com.aerofs.polaris.logical;

import com.aerofs.baseline.Managed;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;

@Singleton
public class StoreMigrator implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStore.class);
    private final DBI dbi;
    private final Notifier notifier;
    private final ListeningExecutorService executor;
    private final Map<UniqueID, Long> versionMap;

    @Inject
    public StoreMigrator(DBI dbi, Notifier notifier)
    {
        this(dbi, notifier, MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()));
    }

    public StoreMigrator(DBI dbi, Notifier notifier, ListeningExecutorService executor)
    {
        this.dbi = dbi;
        this.notifier = notifier;
        this.executor = executor;
        this.versionMap = Maps.newHashMap();
    }

    @Override
    public void start() throws Exception {
        LOGGER.info("starting store migrator");
        resumeMigrations();
    }

    @Override
    public void stop() {
        LOGGER.info("stopping store migrator");
        executor.shutdown();
    }

    public JobStatus getJobStatus(UniqueID jobID)
    {
        // we use the store ID as a job ID
        SID store = new SID(jobID);
        MigrationJob job = dbi.inTransaction((conn, status) -> {
            Migrations migrations = conn.attach(Migrations.class);
            return migrations.get(store);
        });
        if (job == null) {
            throw new NotFoundException(jobID);
        }
        return job.status;
    }

    // returns the Job ID associated with this migration
    public UniqueID migrateStore(SID storeSID, DID originator)
    {
        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            dao.migrations.add(storeSID, originator, JobStatus.RUNNING);
            return null;
        });

        startStoreMigration(storeSID, originator);
        // TODO(RD) consistent Job ID schema
        return storeSID;
    }

    private void resumeMigrations()
    {
        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            ResultIterator<MigrationJob> activeJobs = dao.migrations.activeMigrations();
            while (activeJobs.hasNext()) {
                MigrationJob job = activeJobs.next();
                startStoreMigration(job.store, job.originator);
            }
            return null;
        });
    }

    private void startStoreMigration(SID storeSID, DID originator)
    {
        LOGGER.debug("starting store migration for {}", storeSID);
        ListenableFuture<Void> future = executor.submit(() -> {
            List<Update> updates = updateStoreIDs(originator, storeSID);
            updates.forEach(x -> notifier.notifyStoreUpdated(x.store, x.latestLogicalTimestamp));
            return null;
        });

        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                LOGGER.info("finished migration of store {}", storeSID);
                dbi.inTransaction((conn, status) -> {
                    DAO dao = new DAO(conn);
                    dao.migrations.updateStatus(storeSID, JobStatus.COMPLETED);
                    return null;
                });
            }

            @Override
            public void onFailure(Throwable t) {
                LOGGER.warn("failed migration of store {}, retrying", storeSID, t);
                startStoreMigration(storeSID, originator);
            }
        });
    }

    private List<Update> updateStoreIDs(DID originator, SID store) {
        Queue<ChildMigrationState> migrationQueue = Queues.newArrayDeque();
        List<Update> updates = Lists.newArrayList();

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            LogicalObject oldFolder = getObject(dao, SID.convertedStoreSID2folderOID(store));
            versionMap.put(oldFolder.oid, oldFolder.version);
            try (ResultIterator<Child> oldChildren = dao.children.getChildren(oldFolder.oid)) {
                while (oldChildren.hasNext()) {
                    Child child = oldChildren.next();
                    dao.objects.setLocked(child.oid, true);
                    migrationQueue.add(new ChildMigrationState(oldFolder, child));
                }
            }

            LogicalObject storeRoot = getObject(dao, store);
            versionMap.put(storeRoot.oid, storeRoot.version);
            try (ResultIterator<Child> rootChildren = dao.children.getChildren(store)) {
                while (rootChildren.hasNext()) {
                    Child child = rootChildren.next();
                    dao.objects.setLocked(child.oid, true);
                    migrationQueue.add(new ChildMigrationState(storeRoot, child));
                }
            }
            return null;
        });

        while (!migrationQueue.isEmpty()) {
            IncrementalMigrationResult result = dbi.inTransaction((conn, status) -> {
                DAO dao = new DAO(conn);
                return migrateBatchOfObjects(dao, originator, store, migrationQueue);
            });
            if (result.performedWork) {
                updates = Arrays.asList(result.migratingFromUpdate, result.migratingToUpdate);
            }
        }

        return updates;
    }

    // package-private for testing purposes
    IncrementalMigrationResult migrateBatchOfObjects(DAO dao, DID originator, SID newStore, Queue<ChildMigrationState> searchQueue)
    {
        Preconditions.checkArgument(!searchQueue.isEmpty(), "tried to migrate an empty queue of objects");
        LOGGER.debug("performing batch migration of objects to {}", newStore);

        int operationsDone = 0;
        long migratingToTimestamp = -1, migratingFromTimestamp = -1;
        UniqueID oldStore = null;
        UniqueID convertedFolderOID = SID.convertedStoreSID2folderOID(newStore);

        while (!searchQueue.isEmpty() && operationsDone < Constants.MIGRATION_OPERATION_BATCH_SIZE) {
            ChildMigrationState migrating = searchQueue.remove();
            LogicalObject migratingObject = getObject(dao, migrating.child.oid);

            if (!migratingObject.store.equals(newStore)) {
                long newParentVersion;
                long oldParentVersion;
                UniqueID newParent;
                LOGGER.debug("migrating object {} to {}", migratingObject.oid, newStore);

                if (migrating.parent.oid.equals(convertedFolderOID)) {
                    // need to change the parents table for direct children of the store anchor
                    newParent = newStore;
                    dao.children.add(newStore, migratingObject.oid, migrating.child.name);
                    dao.children.remove(migrating.parent.oid, migratingObject.oid);
                } else {
                    newParent = migrating.parent.oid;
                }

                oldStore = migratingObject.store;
                newParentVersion = versionMap.get(newParent) + 1;
                oldParentVersion = versionMap.get(migrating.parent.oid) + 1;
                dao.objects.changeStore(newStore, migrating.child.oid);
                dao.objects.update(newStore, newParent, newParentVersion);

                Atomic atomic = new Atomic(2);
                migratingToTimestamp = dao.transforms.add(originator, newStore, newParent, TransformType.INSERT_CHILD, newParentVersion, migratingObject.oid, migrating.child.name, System.currentTimeMillis(), atomic);
                migratingFromTimestamp = dao.transforms.add(originator, oldStore, migrating.parent.oid, TransformType.REMOVE_CHILD, oldParentVersion, migratingObject.oid, null, System.currentTimeMillis(), atomic);
                versionMap.replace(newStore, newParentVersion);
                versionMap.replace(migrating.parent.oid, oldParentVersion);
            }

            if (migratingObject.objectType == ObjectType.FOLDER) {
                versionMap.put(migratingObject.oid, migratingObject.version);
                try (ResultIterator<Child> children = dao.children.getChildren(migrating.child.oid)) {
                    while (children.hasNext()) {
                        Child child = children.next();
                        dao.objects.setLocked(child.oid, true);
                        searchQueue.add(new ChildMigrationState(migratingObject, child));
                    }
                }
            }

            dao.objects.setLocked(migratingObject.oid, false);
            operationsDone++;
        }

        if (oldStore != null) {
            // meaning that we did migrate some objects in this batch
            dao.logicalTimestamps.updateLatest(newStore, migratingToTimestamp);
            dao.logicalTimestamps.updateLatest(oldStore, migratingFromTimestamp);
            return new IncrementalMigrationResult(true, new Update(oldStore, migratingFromTimestamp), new Update(newStore, migratingToTimestamp));
        } else {
            return new IncrementalMigrationResult(false, null, null);
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
        final LogicalObject parent;
        final Child child;

        public ChildMigrationState(LogicalObject parent, Child child)
        {
            this.parent = parent;
            this.child = child;
        }
    }

    static class IncrementalMigrationResult
    {
        final boolean performedWork;
        @Nullable final Update migratingFromUpdate;
        @Nullable final Update migratingToUpdate;

        public IncrementalMigrationResult(boolean performedWork, @Nullable Update from, @Nullable Update to)
        {
            this.performedWork = performedWork;
            this.migratingFromUpdate = from;
            this.migratingToUpdate = to;
        }
    }

    public static class MigrationJob
    {
        final DID originator;
        final SID store;
        final JobStatus status;

        public MigrationJob(SID store, DID originator, JobStatus status)
        {
            this.originator = originator;
            this.store = store;
            this.status = status;
        }
    }
}