package com.aerofs.polaris.logical;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.db.Databases;
import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.*;
import com.aerofs.testlib.LoggerSetup;
import com.aerofs.polaris.Polaris;
import com.aerofs.polaris.PolarisConfiguration;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.operation.*;
import com.aerofs.polaris.api.types.DeletableChild;
import com.aerofs.polaris.api.types.LogicalObject;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.dao.LogicalObjects;
import com.aerofs.polaris.dao.Migrations;
import com.aerofs.polaris.dao.types.*;
import com.aerofs.polaris.notification.Notifier;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;

import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.*;
import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class TestMigrator {
    static {
        LoggerSetup.init();
    }

    private static final UserID USERID = UserID.fromInternal("test@aerofs.com");
    private static final DID DEVICE = DID.generate();

    private BasicDataSource dataSource;
    private DBI dbi;
    private Notifier notifier = mock(Notifier.class);
    private Migrator migrator;
    private ListeningExecutorService migratorExecutor;
    private ObjectStore objects;

    @Rule
    public MySQLDatabase database = new MySQLDatabase("test");

    @Before
    public void setup() throws Exception {

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

        // spy on it
        this.dbi = spy(dbi);

        this.migratorExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        this.migrator = spy(new Migrator(this.dbi, notifier, migratorExecutor));
        migrator.start();

        this.objects = new ObjectStore(mock(AccessManager.class), dbi, migrator);
    }

    @After
    public void tearDown()
    {
        migrator.stop();
        try {
            dataSource.close();
        } catch (SQLException e) {
            // noop
        }
    }

    @Test
    public void shouldContinueMigrationOnStartup() throws Exception
    {
        SID rootStore = SID.rootSID(USERID);
        OID sharedFolder = newFolder(rootStore, "shared_folder");
        OID folder = newFolder(sharedFolder, "folder");
        OID fileOID = newFile(folder, "file");
        SID share2 = SID.generate();
        OID immigrantFolder = newFolder(share2, "migrant_folder");
        // is the equivalent to immigrant_folder in rootstore
        OID migrationDestination = newFolder(rootStore, "migrant_folder");
        newFile(immigrantFolder, "migrant_file");

        // make the first call do nothing, as if the server had crashed
        doNothing().doCallRealMethod().when(this.migrator).startStoreMigration(eq(SID.folderOID2convertedStoreSID(sharedFolder)), any(UniqueID.class), eq(DEVICE));
        shareFolder(sharedFolder);
        doNothing().doCallRealMethod().when(this.migrator).startFolderMigration(eq(immigrantFolder), any(OID.class), any(UniqueID.class), eq(DEVICE));
        moveObject(share2, migrationDestination, immigrantFolder, "migrant_folder".getBytes());

        migrator.start();
        migratorExecutor.shutdown();
        assertTrue("failed to complete migration within 10 seconds", migratorExecutor.awaitTermination(10, TimeUnit.SECONDS));

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            assertTrue("failed to delete migrated object", dao.children.getActiveReferenceCount(immigrantFolder) == 0);
            UniqueID migratedFolder = dao.children.getActiveChildNamed(migrationDestination, "migrant_folder".getBytes());
            assertTrue("failed to migrate folder to new store", migratedFolder != null);
            assertTrue("failed to migrate file to new store", dao.children.getActiveChildNamed(migratedFolder, "migrant_file".getBytes()) != null);

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
        OID sharedFolder = newFolder(rootStore, "shared_folder");
        SID newStore = SID.folderOID2convertedStoreSID(sharedFolder);
        OID folder = null, nestedFile = null, unnestedFile = null;
        for (int i = 0; i < 50; i++) {
            folder = newFolder(sharedFolder, String.format("folder-%d", i));
            for (int j = 0; j < 10; j++) {
                nestedFile = newFile(folder, String.format("file-%d", j));
            }
        }
        for (int i = 0; i < 50; i++) {
            unnestedFile = newFile(sharedFolder, String.format("file-%d", i));
        }

        shareFolder(sharedFolder);
        migratorExecutor.shutdown();
        assertTrue("failed to complete migration within 10 seconds", migratorExecutor.awaitTermination(10, TimeUnit.SECONDS));

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
    public void shouldLockFolders() throws Exception
    {
        doReturn(null).when(this.migrator).migrateBatchOfObjects(any(), any(), any(), any(), any());

        SID rootStore = SID.rootSID(USERID);
        OID sharedFolder = newFolder(rootStore, "shared_folder");
        OID folder = newFolder(sharedFolder, "folder");
        shareFolder(sharedFolder);

        verify(this.migrator, timeout(1000).atLeast(1)).migrateBatchOfObjects(any(DAO.class), eq(DEVICE), eq(SID.folderOID2convertedStoreSID(sharedFolder)), any(), any());

        LockableLogicalObject folderObject = dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            return dao.objects.get(folder);
        });

        assertTrue("did not lock folder", folderObject.locked);
    }

    @Test
    public void shouldNotOperateOnLockedObjects() throws Exception
    {
        SID store = SID.generate();
        OID lockedFolder = newFolder(store, "folder1");
        OID unlockedFolder = newFolder(store, "folder2");
        OID fileUnderLockedFolder = newFile(lockedFolder, "file1");
        OID fileUnderUnlockedFolder = newFile(unlockedFolder, "file2");
        OID deletedLockedFile = newFile(unlockedFolder, "deleted");
        OID lockedFile = newFile(unlockedFolder, "locked");
        objects.performTransform(USERID, DEVICE, unlockedFolder, new RemoveChild(deletedLockedFile));


        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            dao.objects.setLocked(lockedFolder, true);
            dao.objects.setLocked(deletedLockedFile, true);
            dao.objects.setLocked(lockedFile, true);
            return null;
        });

        try {
            objects.performTransform(USERID, DEVICE, lockedFolder, new InsertChild(OID.generate(), ObjectType.FOLDER, "a folder"));
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
            objects.performTransform(USERID, DEVICE, unlockedFolder, new InsertChild(deletedLockedFile, null, "deleted"));
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
        OID folder = newFolder(rootStore, "folder");
        SID share1 = SID.generate(), share2 = SID.generate();
        newFile(share1, "file");
        OID destFolder = newFolder(share2, "folder");
        insertAnchor(folder, share1, "share");

        migrator.start();
        migrator.moveCrossStore(folder, destFolder, DEVICE);
        migratorExecutor.shutdown();
        assertTrue("failed to complete migration within 10 seconds", migratorExecutor.awaitTermination(10, TimeUnit.SECONDS));

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            ResultIterator<DeletableChild> migrants = dao.children.getChildren(destFolder);
            assertTrue("did not migrate any children under destination", migrants.hasNext());
            DeletableChild child = migrants.next();
            assertThat("did not migrate share name", child.name, equalTo("share".getBytes()));
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
        OID sharedFolder = newFolder(rootStore, "shared_folder");
        OID folder = newFolder(sharedFolder, "folder");
        OID file = newFile(folder, "file");
        OID folder2 = newFolder(sharedFolder, "folder2");

        OID newAnchor = SID.folderOID2convertedAnchorOID(sharedFolder);
        SID newStore = SID.folderOID2convertedStoreSID(sharedFolder);

        // make the first call do nothing, as if the server had crashed
        doNothing().doCallRealMethod().when(this.migrator).startStoreMigration(eq(SID.folderOID2convertedStoreSID(sharedFolder)), any(UniqueID.class), eq(DEVICE));
        shareFolder(sharedFolder);

        // fake some work being done before crash
        dbi.inTransaction(((conn, status) -> {
            DAO dao = new DAO(conn);
            dao.children.add(newAnchor, folder, "folder".getBytes(), false);
            dao.children.remove(sharedFolder, folder);
            dao.objects.changeStore(newStore, folder);
            return null;
        }));

        migrator.start();
        migratorExecutor.shutdown();
        assertTrue("failed to complete migration within 10 seconds", migratorExecutor.awaitTermination(10, TimeUnit.SECONDS));

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
        OID migrationRoot = newFolder(share2, "migrant_folder");
        // is the equivalent to immigrant_folder in rootstore
        newFile(migrationRoot, "migrant_file");
        OID folder = newFolder(migrationRoot, "folder");
        newFile(folder, "nested_file");

        // make the first call do nothing, as if the server had crashed
        doNothing().doCallRealMethod().when(this.migrator).startFolderMigration(eq(migrationRoot), any(OID.class), any(UniqueID.class), eq(DEVICE));
        UniqueID jobID = moveObject(share2, rootStore, migrationRoot, "migrant_folder".getBytes()).jobID;
        assertTrue("failed to return a job id for migration", jobID != null);

        // fake some work being done before server crash
        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            OID newFolderID = OID.generate();
            UniqueID migratedRootFolder = dao.children.getActiveChildNamed(rootStore, "migrant_folder".getBytes());
            dao.migrations.addOidMapping(folder, newFolderID, jobID);
            dao.objects.add(rootStore, newFolderID, 1L);
            dao.objectTypes.add(newFolderID, ObjectType.FOLDER);
            dao.children.add(migratedRootFolder, newFolderID, "folder".getBytes(), false);

            return null;
        });

        migrator.start();
        migratorExecutor.shutdown();
        assertTrue("failed to complete migration within 10 seconds", migratorExecutor.awaitTermination(10, TimeUnit.SECONDS));

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
        OID sharedFolder = newFolder(rootStore, "shared_folder");
        OID folder = newFolder(sharedFolder, "folder");
        // ensure the second shareFolder call is performed before the migrator can do any work
        doReturn(UniqueID.generate()).doCallRealMethod().when(this.migrator).migrateStore(eq(SID.folderOID2convertedStoreSID(sharedFolder)), eq(DEVICE));
        shareFolder(sharedFolder);
        try {
            shareFolder(folder);
            fail();
        } catch (CallbackFailedException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    private OperationResult moveObject(UniqueID parent, UniqueID newParent, UniqueID child, byte[] childName)
    {
        Operation op = new MoveChild(child, newParent, childName);
        return this.objects.performTransform(USERID, DEVICE, parent, op);
    }

    private OID newFolder(UniqueID parent, String name) {
        OID folder = OID.generate();
        Operation op = new InsertChild(folder, ObjectType.FOLDER, name);
        this.objects.performTransform(USERID, DEVICE, parent, op);
        return folder;
    }

    private OID newFile(UniqueID parent, String name) {
        OID file = OID.generate();
        Operation op = new InsertChild(file, ObjectType.FILE, name);
        this.objects.performTransform(USERID, DEVICE, parent, op);
        return file;
    }

    private void insertAnchor(UniqueID parent, SID store, String name) {
        Operation op = new InsertChild(store, ObjectType.STORE, name);
        this.objects.performTransform(USERID, DEVICE, parent, op);
    }

    private UniqueID shareFolder(UniqueID folder) {
        Operation op = new Share();
        return this.objects.performTransform(USERID, DEVICE, folder, op).jobID;
    }
}
