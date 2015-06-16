package com.aerofs.polaris.logical;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.db.Databases;
import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.*;
import com.aerofs.polaris.Polaris;
import com.aerofs.polaris.PolarisConfiguration;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.operation.*;
import com.aerofs.polaris.api.types.JobStatus;
import com.aerofs.polaris.api.types.LogicalObject;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.dao.LogicalObjects;
import com.aerofs.polaris.dao.Migrations;
import com.aerofs.polaris.dao.types.*;
import com.aerofs.polaris.notification.Notifier;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;

import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;
import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class TestStoreMigrator {

    private static final UserID USERID = UserID.fromInternal("test@aerofs.com");
    private static final DID DEVICE = DID.generate();

    private BasicDataSource dataSource;
    private DBI dbi;
    private Notifier notifier = mock(Notifier.class);
    private StoreMigrator migrator;
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
        this.migrator = spy(new StoreMigrator(this.dbi, notifier, migratorExecutor));
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

        // make the first call do nothing, as if the server had crashed
        doReturn(OID.generate()).doCallRealMethod().when(this.migrator).migrateStore(eq(SID.folderOID2convertedStoreSID(sharedFolder)), eq(DEVICE));
        shareFolder(sharedFolder);

        dbi.inTransaction((conn, status) -> {
            Migrations migrations = conn.attach(Migrations.class);
            assertFalse(migrations.activeMigrations().hasNext());
            migrations.add(SID.folderOID2convertedStoreSID(sharedFolder), DEVICE, JobStatus.RUNNING);
            return null;
        });

        migrator.start();
        migratorExecutor.shutdown();
        assertTrue("failed to complete migration within 10 seconds", migratorExecutor.awaitTermination(10, TimeUnit.SECONDS));

        LogicalObject nestedFile = dbi.inTransaction((conn, status) -> {
            LogicalObjects objects = conn.attach(LogicalObjects.class);
            return objects.get(fileOID);
        });
        assertThat(nestedFile.store, equalTo(SID.folderOID2convertedStoreSID(sharedFolder)));

        boolean hasActiveMigrations = dbi.inTransaction((conn, status) -> {
            Migrations migrations = conn.attach(Migrations.class);
            return migrations.activeMigrations().hasNext();
        });
        assertFalse("failed to clear all active migrations", hasActiveMigrations);
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
        doReturn(null).when(this.migrator).migrateBatchOfObjects(any(), any(), any(), any());

        SID rootStore = SID.rootSID(USERID);
        OID sharedFolder = newFolder(rootStore, "shared_folder");
        OID folder = newFolder(sharedFolder, "folder");
        shareFolder(sharedFolder);

        verify(this.migrator, timeout(1000).atLeast(1)).migrateBatchOfObjects(any(DAO.class), eq(DEVICE), eq(SID.folderOID2convertedStoreSID(sharedFolder)), any());

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

    private UniqueID shareFolder(UniqueID folder) {
        Operation op = new Share();
        return this.objects.performTransform(USERID, DEVICE, folder, op).jobID;
    }
}
