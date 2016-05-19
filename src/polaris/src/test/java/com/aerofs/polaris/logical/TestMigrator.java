package com.aerofs.polaris.logical;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.db.Databases;
import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.*;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.Polaris;
import com.aerofs.polaris.PolarisConfiguration;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.operation.*;
import com.aerofs.polaris.api.types.DeletableChild;
import com.aerofs.polaris.api.types.LogicalObject;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.dao.LockStatus;
import com.aerofs.polaris.dao.LogicalObjects;
import com.aerofs.polaris.dao.Migrations;
import com.aerofs.polaris.dao.types.*;
import com.aerofs.polaris.notification.Notifier;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import org.junit.*;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;

import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static com.aerofs.polaris.PolarisHelpers.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.*;
import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class TestMigrator
{

    private static final UserID USERID = UserID.fromInternal("test@aerofs.com");
    private static final DID DEVICE = DID.generate();

    private static BasicDataSource dataSource;
    private static DBI realdbi;
    private DBI dbi;
    private Notifier notifier;
    private Migrator migrator;
    private ListeningExecutorService migratorExecutor;
    private ObjectStore objects;

    @ClassRule
    public static MySQLDatabase database = new MySQLDatabase("test");

    @BeforeClass
    public static void setup() throws Exception {
        // setup database
        PolarisConfiguration configuration = Configuration.loadYAMLConfigurationFromResources(Polaris.class, "polaris_test_server.yml");
        DatabaseConfiguration database = configuration.getDatabase();
        dataSource = (BasicDataSource) Databases.newDataSource(database);

        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.migrate();

        // setup JDBI
        DBI dbi = Databases.newDBI(dataSource);
        dbi.registerArgumentFactory(new UniqueIDTypeArgument.UniqueIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new OIDTypeArgument.OIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new SIDTypeArgument.SIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new DIDTypeArgument.DIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new ObjectTypeArgument.ObjectTypeArgumentFactory());
        dbi.registerArgumentFactory(new TransformTypeArgument.TransformTypeArgumentFactory());
        dbi.registerArgumentFactory(new JobStatusArgument.JobStatusArgumentFactory());
        dbi.registerArgumentFactory(new LockStatusArgument.LockStatusArgumentFactory());
        realdbi = dbi;
    }

    @AfterClass
    public static void tearDown()
    {
        try {
            dataSource.close();
        } catch (SQLException e) {
            // noop
        }
    }

    @Before
    public void setupMocks() throws Exception
    {
        // spy on it
        this.dbi = spy(realdbi);

        this.notifier = mock(Notifier.class);
        this.migratorExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        this.migrator = spy(new Migrator(this.dbi, notifier, migratorExecutor));
        this.objects = new ObjectStore(mock(AccessManager.class), dbi, migrator);
        migrator.start();
    }

    @After
    public void clearData()
    {
        migrator.stop();
        database.clear();
    }

    @Test
    public void shouldContinueMigrationOnStartup() throws Exception
    {
        SID rootStore = SID.rootSID(USERID);
        OID sharedFolder = newFolder(rootStore, "shared_folder", USERID, DEVICE, objects);
        OID folder = newFolder(sharedFolder, "folder", USERID, DEVICE, objects);
        OID fileOID = newFile(folder, "file", USERID, DEVICE, objects);
        SID share2 = SID.generate();
        OID immigrantFolder = newFolder(share2, "migrant_folder", USERID, DEVICE, objects);
        // is the equivalent to immigrant_folder in rootstore
        OID migrationDestination = newFolder(rootStore, "migrant_folder", USERID, DEVICE, objects);
        newFile(immigrantFolder, "migrant_file", USERID, DEVICE, objects);
        SID share3 = SID.generate();
        insertAnchor(rootStore, share3, "shared_folder_2", USERID, DEVICE, objects);
        newFile(share3, "migrant_file_2", USERID, DEVICE, objects);

        // make the first call do nothing, as if the server had crashed
        doNothing().doCallRealMethod().when(this.migrator).startStoreMigration(eq(SID.folderOID2convertedStoreSID(sharedFolder)), any(UniqueID.class), eq(DEVICE));
        UniqueID job1 = shareFolder(rootStore, sharedFolder, USERID, DEVICE, objects);
        doNothing().doCallRealMethod().when(this.migrator).startFolderMigration(eq(immigrantFolder), eq(migrationDestination), any(UniqueID.class), eq(DEVICE));
        UniqueID job2 = moveObject(share2, migrationDestination, immigrantFolder, "migrant_folder".getBytes(), USERID, DEVICE, objects).jobID;
        doNothing().doCallRealMethod().when(this.migrator).startFolderMigration(eq(share3), any(OID.class), any(UniqueID.class), eq(DEVICE));
        UniqueID job3 = moveObject(rootStore, share2, share3, "migrant_folder_2".getBytes(), USERID, DEVICE, objects).jobID;

        migrator.start();
        waitForJobCompletion(migrator, job1, 10);
        waitForJobCompletion(migrator, job2, 10);
        waitForJobCompletion(migrator, job3, 10);

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            assertTrue("failed to delete migrated object", dao.children.getActiveReferenceCount(immigrantFolder) == 0);
            UniqueID migratedFolder = dao.children.getActiveChildNamed(migrationDestination, "migrant_folder".getBytes());
            assertTrue("failed to migrate folder to new store", migratedFolder != null);
            assertTrue("failed to migrate file to new store", dao.children.getActiveChildNamed(migratedFolder, "migrant_file".getBytes()) != null);

            UniqueID migratedStore = dao.children.getActiveChildNamed(share2, "migrant_folder_2".getBytes());
            assertTrue("failed to migrate shared folder to new store", migratedStore != null);
            assertTrue("failed to migrate file under shared folder to new store", dao.children.getActiveChildNamed(migratedStore, "migrant_file_2".getBytes()) != null);

            assertThat(dao.objects.get(fileOID).store, equalTo(SID.folderOID2convertedStoreSID(sharedFolder)));
            return null;
        });

        dbi.inTransaction((conn, status) -> {
            Migrations migrations = conn.attach(Migrations.class);
            assertFalse("failed to clear all active migrations", migrations.activeMigrations().hasNext());
            return null;
        });
    }

    @Test
    public void batchesMigratingLargeFolders() throws Exception
    {
        SID rootStore = SID.rootSID(USERID);
        OID sharedFolder = newFolder(rootStore, "shared_folder", USERID, DEVICE, objects);
        SID newStore = SID.folderOID2convertedStoreSID(sharedFolder);
        OID folder = null, nestedFile = null, unnestedFile = null;
        for (int i = 0; i < 50; i++) {
            folder = newFolder(sharedFolder, String.format("folder-%d", i), USERID, DEVICE, objects);
            for (int j = 0; j < 10; j++) {
                nestedFile = newFile(folder, String.format("file-%d", j), USERID, DEVICE, objects);
            }
        }
        for (int i = 0; i < 50; i++) {
            unnestedFile = newFile(sharedFolder, String.format("file-%d", i), USERID, DEVICE, objects);
        }

        waitForJobCompletion(migrator, shareFolder(rootStore, sharedFolder, USERID, DEVICE, objects), 100);

        // at least 550 * 2 notifications. 1 for the creation, and the 1 operations for cross-store move
        verify(notifier).notifyStoreUpdated(eq(newStore), gt(1100L));
        // FIXME (RD) find out the number of transactions to migrate a smaller shared folder programatically
        verify(dbi, atLeast(6)).open();

        OID[] objectIDs = {folder, nestedFile, unnestedFile};
        for (OID oid : objectIDs) {
            LogicalObject object = dbi.inTransaction((conn, status) -> {
                LogicalObjects objects = conn.attach(LogicalObjects.class);
                return objects.get(oid);
            });

            assertEquals(object.store, newStore);
        }
    }

    @Test
    public void shouldLockFoldersAfterSharing() throws Exception
    {
        doReturn(null).when(this.migrator).updateStoreForBatch(any(), any(), any(), any(), any());

        SID rootStore = SID.rootSID(USERID);
        OID sharedFolder = newFolder(rootStore, "shared_folder", USERID, DEVICE, objects);
        OID folder = newFolder(sharedFolder, "folder", USERID, DEVICE, objects);
        shareFolder(rootStore, sharedFolder, USERID, DEVICE, objects);

        verify(this.migrator, timeout(1000).atLeast(1)).updateStoreForBatch(any(DAO.class), eq(DEVICE), eq(SID.folderOID2convertedStoreSID(sharedFolder)), any(), any());

        LockableLogicalObject folderObject = dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            return dao.objects.get(folder);
        });

        assertTrue("did not lock folder", folderObject.locked == LockStatus.LOCKED);
    }

    @Test
    public void shouldNotOperateOnLockedObjects() throws Exception
    {
        SID store = SID.generate();
        OID lockedFolder = newFolder(store, "folder1", USERID, DEVICE, objects);
        OID unlockedFolder = newFolder(store, "folder2", USERID, DEVICE, objects);
        OID fileUnderLockedFolder = newFile(lockedFolder, "file1", USERID, DEVICE, objects);
        OID fileUnderUnlockedFolder = newFile(unlockedFolder, "file2", USERID, DEVICE, objects);
        OID deletedLockedFile = newFile(unlockedFolder, "deleted", USERID, DEVICE, objects);
        OID lockedFile = newFile(unlockedFolder, "locked", USERID, DEVICE, objects);
        objects.performTransform(USERID, DEVICE, unlockedFolder, new RemoveChild(deletedLockedFile));


        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            dao.objects.setLocked(lockedFolder, LockStatus.LOCKED);
            dao.objects.setLocked(deletedLockedFile, LockStatus.LOCKED);
            dao.objects.setLocked(lockedFile, LockStatus.LOCKED);
            return null;
        });

        try {
            objects.performTransform(USERID, DEVICE, lockedFolder, new InsertChild(OID.generate(), ObjectType.FOLDER, "a folder", null));
            fail();
        } catch (CallbackFailedException e) {
            assertTrue(e.getCause().getClass().equals(ObjectLockedException.class));
        }

        try {
            objects.performTransform(USERID, DEVICE, lockedFolder, new RemoveChild(fileUnderLockedFolder));
            fail();
        } catch (CallbackFailedException e) {
            assertTrue(e.getCause().getClass().equals(ObjectLockedException.class));
        }

        try {
            byte[] hash = new byte[32];
            Random random = new Random();
            random.nextBytes(hash);
            objects.performTransform(USERID, DEVICE, lockedFile, new UpdateContent(0, hash, 100, 1024, null));
            fail();
        } catch (CallbackFailedException e) {
            assertTrue(e.getCause().getClass().equals(ObjectLockedException.class));
        }

        try {
            objects.performTransform(USERID, DEVICE, lockedFolder, new MoveChild(fileUnderLockedFolder, unlockedFolder, "file1"));
            fail();
        } catch (CallbackFailedException e) {
            assertTrue(e.getCause().getClass().equals(ObjectLockedException.class));
        }

        try {
            objects.performTransform(USERID, DEVICE, unlockedFolder, new MoveChild(fileUnderUnlockedFolder, lockedFolder, "file2"));
            fail();
        } catch (CallbackFailedException e) {
            assertTrue(e.getCause().getClass().equals(ObjectLockedException.class));
        }

        // a simple rename doesn't need parent to be unlocked
        objects.performTransform(USERID, DEVICE, lockedFolder, new MoveChild(fileUnderLockedFolder, lockedFolder, "new_filename"));

        // though you can't rename a locked object
        try {
            objects.performTransform(USERID, DEVICE, store, new MoveChild(lockedFolder, store, "new_foldername"));
            fail();
        } catch (CallbackFailedException e) {
            assertTrue(e.getCause().getClass().equals(ObjectLockedException.class));
        }

        // can't reinsert a deleted locked object
        try {
            objects.performTransform(USERID, DEVICE, deletedLockedFile, new Restore());
            fail();
        } catch (CallbackFailedException e) {
            assertTrue(e.getCause().getClass().equals(ObjectLockedException.class));
        }

        // can't delete a locked object
        try {
            objects.performTransform(USERID, DEVICE, unlockedFolder, new RemoveChild(lockedFile));
            fail();
        } catch (CallbackFailedException e) {
            assertTrue(e.getCause().getClass().equals(ObjectLockedException.class));
        }
    }

    @Test
    public void shouldMoveAnchorsInMigration()
            throws Exception
    {
        SID rootStore = SID.rootSID(USERID);
        OID folder = newFolder(rootStore, "folder", USERID, DEVICE, objects);
        SID share1 = SID.generate(), share2 = SID.generate();
        newFile(share1, "file", USERID, DEVICE, objects);
        OID destFolder = newFolder(share2, "folder", USERID, DEVICE, objects);
        insertAnchor(folder, share1, "share", USERID, DEVICE, objects);

        migrator.start();
        // this is emulating as if folder was moved to be under share2, and its migrant equivalent were destFolder
        UniqueID job = dbi.inTransaction((conn, handle) -> {
            DAO dao = new DAO(conn);
            return migrator.moveCrossStore(dao, folder, rootStore, destFolder, DEVICE);
        });
        waitForJobCompletion(migrator, job, 10);

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            ResultIterator<DeletableChild> migrants = dao.children.getChildren(destFolder);
            assertTrue("did not migrate any children under destination", migrants.hasNext());
            DeletableChild child = migrants.next();
            assertThat("did not migrate share name", new String(child.name), equalTo("share"));
            assertThat("did not convert anchor to folder", dao.objects.get(child.oid).objectType, equalTo(ObjectType.FOLDER));
            migrants = dao.children.getChildren(child.oid);
            assertTrue("did not migrate any children under anchor", migrants.hasNext());
            child = migrants.next();
            assertThat("did not migrate shared file", child.name, equalTo("file".getBytes()));
            assertThat("did not mark root anchor as deleted", dao.mountPoints.getMountPointParent(rootStore, share1), nullValue());
            return null;
        });
    }

    // TODO(RD): this test replicates the db operations of the migrator, make it less fragile to changes in migrator code
    // upon restarting a sharing migration, the tree traversal needs to be made both from the origin and destination folders
    // otherwise, direct children of the new shared folder root and their children subtrees could be skipped by migration
    @Test
    public void shouldContinueSharingFromOriginAndDestination()
            throws Exception
    {
        SID rootStore = SID.rootSID(USERID);
        OID sharedFolder = newFolder(rootStore, "shared_folder", USERID, DEVICE, objects);
        OID folder = newFolder(sharedFolder, "folder", USERID, DEVICE, objects);
        OID file = newFile(folder, "file", USERID, DEVICE, objects);
        OID folder2 = newFolder(sharedFolder, "folder2", USERID, DEVICE, objects);

        OID newAnchor = SID.folderOID2convertedAnchorOID(sharedFolder);
        SID newStore = SID.folderOID2convertedStoreSID(sharedFolder);

        // make the first call do nothing, as if the server had crashed
        doNothing().doCallRealMethod().when(this.migrator).startStoreMigration(eq(SID.folderOID2convertedStoreSID(sharedFolder)), any(UniqueID.class), eq(DEVICE));
        UniqueID job = shareFolder(rootStore, sharedFolder, USERID, DEVICE, objects);

        // fake some work being done before crash
        dbi.inTransaction(((conn, status) -> {
            DAO dao = new DAO(conn);
            dao.children.add(newAnchor, folder, "folder".getBytes());
            dao.children.remove(sharedFolder, folder);
            dao.objects.changeStore(newStore, folder);
            return null;
        }));

        migrator.start();
        waitForJobCompletion(migrator, job, 10);

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            assertThat(dao.objects.get(file).store, equalTo(newStore));
            assertThat(dao.objects.get(folder2).store, equalTo(newStore));
            return null;
        });

        dbi.inTransaction((conn, status) -> {
            Migrations migrations = conn.attach(Migrations.class);
            assertFalse("failed to clear all active migrations", migrations.activeMigrations().hasNext());
            return null;
        });
    }

    // TODO(RD): this test replicates the db operations of the migrator, make it less fragile to changes in migrator code
    @Test
    public void shouldContinueInterruptedMigration()
            throws Exception
    {
        SID rootStore = SID.rootSID(USERID);
        SID share2 = SID.generate();
        OID migrationRoot = newFolder(share2, "migrant_folder", USERID, DEVICE, objects);
        // is the equivalent to immigrant_folder in rootstore
        newFile(migrationRoot, "migrant_file", USERID, DEVICE, objects);
        OID folder = newFolder(migrationRoot, "folder", USERID, DEVICE, objects);
        newFile(folder, "nested_file", USERID, DEVICE, objects);

        // make the first call do nothing, as if the server had crashed
        doNothing().doCallRealMethod().when(this.migrator).startFolderMigration(eq(migrationRoot), any(OID.class), any(UniqueID.class), eq(DEVICE));
        UniqueID jobID = moveObject(share2, rootStore, migrationRoot, "migrant_folder".getBytes(), USERID, DEVICE, objects).jobID;
        assertTrue("failed to return a job id for migration", jobID != null);

        // fake some work being done before server crash
        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            OID newFolderID = OID.generate();
            UniqueID migratedRootFolder = dao.children.getActiveChildNamed(rootStore, "migrant_folder".getBytes());
            dao.migrations.addOidMapping(folder, newFolderID, jobID);
            dao.objects.add(rootStore, newFolderID, 1L);
            dao.objectTypes.add(newFolderID, ObjectType.FOLDER);
            dao.children.add(migratedRootFolder, newFolderID, "folder".getBytes());

            return null;
        });

        migrator.start();
        waitForJobCompletion(migrator, jobID, 10);

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            assertTrue("failed to delete migrated object", dao.children.getActiveReferenceCount(migrationRoot) == 0);
            UniqueID migratedRootFolder = dao.children.getActiveChildNamed(rootStore, "migrant_folder".getBytes());
            assertTrue("failed to migrate file to new store", dao.children.getActiveChildNamed(migratedRootFolder, "migrant_file".getBytes()) != null);
            UniqueID migratedFolder = dao.children.getActiveChildNamed(migratedRootFolder, "folder".getBytes());
            assertTrue("failed to migrate folder to new store", migratedFolder != null);
            assertTrue("failed to migrate nested file to new store", dao.children.getActiveChildNamed(migratedFolder, "nested_file".getBytes()) != null);
            return null;
        });

        dbi.inTransaction((conn, status) -> {
            Migrations migrations = conn.attach(Migrations.class);
            assertFalse("failed to clear all active migrations", migrations.activeMigrations().hasNext());
            return null;
        });
    }

    @Test
    public void shouldNotAllowNestedSharingDuringActiveMigration()
            throws Exception
    {
        SID rootStore = SID.rootSID(USERID);
        OID sharedFolder = newFolder(rootStore, "shared_folder", USERID, DEVICE, objects);
        OID folder = newFolder(sharedFolder, "folder", USERID, DEVICE, objects);
        // ensure the second shareFolder call is performed before the migrator can do any work
        doReturn(UniqueID.generate()).doCallRealMethod().when(this.migrator).migrateStore(any(DAO.class), eq(SID.folderOID2convertedStoreSID(sharedFolder)), eq(DEVICE));
        shareFolder(rootStore, sharedFolder, USERID, DEVICE, objects);
        try {
            shareFolder(sharedFolder, folder, USERID, DEVICE, objects);
            fail();
        } catch (CallbackFailedException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void shouldLockFoldersUntilAllChildrenAreMigrated()
            throws Exception
    {
        SID store1 = SID.generate(), store2 = SID.generate();
        OID migrationRoot = newFolder(store1, "folder", USERID, DEVICE, objects);
        for (int i = 0; i < 2 * Constants.MIGRATION_OPERATION_BATCH_SIZE; i++) {
            newFile(migrationRoot, "file" + Integer.toString(i), USERID, DEVICE, objects);
        }

        final Semaphore sem = new Semaphore(0);
        doCallRealMethod().doAnswer(invocation -> {
            sem.release();
            return null;
        }).when(this.migrator).moveBatchCrossStore(any(DAO.class), any(DID.class), any(UniqueID.class), any(), any(), any(), any());
        moveObject(store1, store2, migrationRoot, "folder".getBytes(), USERID, DEVICE, objects);
        // wait until the first batch is migrated
        sem.acquire();

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            ResultIterator<DeletableChild> migrants = dao.children.getChildren(store2);
            assertTrue("did not migrate any children under destination", migrants.hasNext());
            DeletableChild child = migrants.next();
            LockableLogicalObject logicalObject = dao.objects.get(child.oid);
            assertThat("did not lock folder until all children were migrated", logicalObject.locked, equalTo(LockStatus.LOCKED));
            return null;
        });
    }

}
