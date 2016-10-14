package com.aerofs.daemon.core.status;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.AsyncHttpClient.Function;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryServiceImpl;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserPathResolver;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserStoreHierarchy;
import com.aerofs.daemon.core.polaris.WaldoAsyncClient;
import com.aerofs.daemon.core.polaris.api.LocationStatusBatch;
import com.aerofs.daemon.core.polaris.api.LocationStatusBatchResult;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.status.db.OutOfSyncFilesDatabase;
import com.aerofs.daemon.core.status.db.OutOfSyncFilesDatabase.OutOfSyncFile;
import com.aerofs.daemon.core.status.db.SyncStatusRequests;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.store.StoreCreationOperators;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.daemon.core.update.DPUTUtil;
import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.daemon.lib.db.MetaDatabase;
import com.aerofs.daemon.lib.db.SIDDatabase;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.InMemoryCoreDBCW;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.testlib.AbstractTest;

import org.junit.Before;
import org.mockito.Mock;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractSyncStatusTest extends AbstractTest
{
    private static final Logger logger = Loggers.getLogger(AbstractSyncStatusTest.class);

    InMemoryCoreDBCW dbcw;
    StoreCreationOperators sco;
    MetaDatabase metaDatabase;
    DirectoryServiceImpl directoryService;

    SIDMap sm;
    TransManager transManager;
    @Mock CoreScheduler coreScheduler;

    Queue<AbstractEBSelfHandling> scheduled = new ArrayDeque<>();

    SID rootSID = SID.generate();
    SIndex rootSIndex = new SIndex(1);

    @Mock WaldoAsyncClient waldoClient;
    @Mock WaldoAsyncClient.Factory factWaldo;
    SyncStatusRequests syncStatusRequests;
    OutOfSyncFilesDatabase outOfSyncDatabase;
    SIDDatabase sidDatabase;
    @Mock CfgLocalUser cfgLocalUser;
    @Mock PauseSync pauseSync;

    @Mock UserAndDeviceNames userAndDeviceNames;
    SyncStatusUploadState syncStatusUploadState;

    SyncStatusPropagator propagator;
    SyncStatusOnline syncStatusOnline;
    TestingBatchStatusChecker statusChecker;

    SOID empty = new SOID(rootSIndex, OID.generate());
    SOID anchor = new SOID(rootSIndex, SID.storeSID2anchorOID(SID.generate()));
    SOID foo = new SOID(rootSIndex, OID.generate());
    SOID qux = new SOID(rootSIndex, OID.generate());
    SOID bar = new SOID(rootSIndex, OID.generate());
    SOID baz = new SOID(rootSIndex, OID.generate());
    SOID bax = new SOID(rootSIndex, OID.generate());
    SOID bay = new SOID(rootSIndex, OID.generate());
    SOID baw = new SOID(rootSIndex, OID.generate());
    SOID deep = new SOID(rootSIndex, OID.generate());
    SOID inside = new SOID(rootSIndex, OID.generate());
    SOID the = new SOID(rootSIndex, OID.generate());
    SOID moria = new SOID(rootSIndex, OID.generate());

    SID storeSID;
    SIndex storeSIndex;
    SOID store;

    SOID mines;

    DID storageAgentDID1 = DID.generate();
    DID storageAgentDID2 = DID.generate();
    DID irrelevant = DID.generate();
    UserID teamServer = UserID.UNKNOWN_TEAM_SERVER;

    @Before
    public void commonSetup() throws Exception {
        dbcw = new InMemoryCoreDBCW();
        dbcw.init_();
        DPUTUtil.runDatabaseOperationAtomically_(dbcw, s -> {
            CoreSchema.createOutOfSyncFilesTable(s, dbcw);
        });
        transManager = new TransManager(new Trans.Factory(dbcw));
        sco = new StoreCreationOperators();
        metaDatabase = new MetaDatabase(dbcw, sco);
        directoryService = new DirectoryServiceImpl();

        syncStatusRequests = new SyncStatusRequests();
        outOfSyncDatabase = new OutOfSyncFilesDatabase(dbcw);
        sidDatabase = new SIDDatabase(dbcw);
        sm = new SIDMap(sidDatabase);

        doReturn(false).when(pauseSync).isPaused();

        syncStatusOnline = new SyncStatusOnline(pauseSync);

        doReturn(UserID.UNKNOWN_TEAM_SERVER).when(userAndDeviceNames)
                .getDeviceOwnerNullable_(storageAgentDID1);
        doReturn(UserID.UNKNOWN_TEAM_SERVER).when(userAndDeviceNames)
                .getDeviceOwnerNullable_(storageAgentDID2);
        doReturn(UserID.UNKNOWN).when(userAndDeviceNames).getDeviceOwnerNullable_(irrelevant);
        syncStatusUploadState = new SyncStatusUploadState(userAndDeviceNames, new UploadState());

        StoreDatabase storeDatabase = new StoreDatabase(dbcw);
        SingleuserStoreHierarchy sss = new SingleuserStoreHierarchy(storeDatabase);

        doReturn(UserID.DUMMY).when(cfgLocalUser).get();

        doAnswer(invocation -> {
            scheduled.add((AbstractEBSelfHandling) invocation.getArguments()[0]);
            return null;
        }).when(coreScheduler).schedule(any(IEvent.class));

        doAnswer(invocation -> {
            if ((long) invocation.getArguments()[1] == 0) {
                scheduled.add((AbstractEBSelfHandling) invocation.getArguments()[0]);
            }
            return null;
        }).when(coreScheduler).scheduleCancellable(any(IEvent.class), anyLong());

        directoryService.inject_(metaDatabase, mock(MapAlias2Target.class), transManager, sm, sm,
                mock(StoreDeletionOperators.class), new SingleuserPathResolver.Factory(sss, sm, sm));

        /*
         * creating the following paths:
         *
         * /empty (empty dir)
         *
         * /anchor (points to store)
         *
         * /store (dir, of second store)
         *
         * /store/mines
         *
         * /foo/qux
         *
         * /foo/bar/baz
         *
         * /foo/bar/bax
         *
         * /foo/bar/bay
         *
         * /foo/bar/baw
         *
         * /foo/bar/deep/inside/the/moria
         *
         *
         */
        try (Trans trans = transManager.begin_()) {
            sidDatabase.insertSID_(rootSID, trans);
            storeDatabase.insert_(rootSIndex, "", trans);
            sm.add_(rootSIndex);

            metaDatabase.createStore_(rootSIndex, trans);
            directoryService.createOA_(Type.DIR, rootSIndex, empty.oid(), OID.ROOT, "empty", trans);
            directoryService.createOA_(Type.ANCHOR, rootSIndex, anchor.oid(), OID.ROOT, "anchor", trans);
            directoryService.createOA_(Type.DIR, rootSIndex, foo.oid(), OID.ROOT, "foo", trans);
            directoryService.createOA_(Type.FILE, rootSIndex, qux.oid(), foo.oid(), "qux", trans);
            directoryService.createOA_(Type.DIR, rootSIndex, bar.oid(), foo.oid(), "bar", trans);
            directoryService.createOA_(Type.FILE, rootSIndex, baz.oid(), bar.oid(), "baz", trans);
            directoryService.createOA_(Type.FILE, rootSIndex, bay.oid(), bar.oid(), "bay", trans);
            directoryService.createOA_(Type.FILE, rootSIndex, bax.oid(), bar.oid(), "bax", trans);
            directoryService.createOA_(Type.FILE, rootSIndex, baw.oid(), bar.oid(), "baw", trans);
            directoryService.createOA_(Type.DIR, rootSIndex, deep.oid(), bar.oid(), "deep", trans);
            directoryService.createOA_(Type.DIR, rootSIndex, inside.oid(), deep.oid(), "inside", trans);
            directoryService.createOA_(Type.DIR, rootSIndex, the.oid(), inside.oid(), "the", trans);
            directoryService.createOA_(Type.FILE, rootSIndex, moria.oid(), the.oid(), "moria", trans);
            directoryService.createCA_(qux, KIndex.MASTER, trans);
            directoryService.createCA_(baz, KIndex.MASTER, trans);
            directoryService.createCA_(moria, KIndex.MASTER, trans);

            storeSID = SID.anchorOID2storeSID(anchor.oid());
            storeSIndex = sidDatabase.insertSID_(storeSID, trans);
            storeDatabase.insert_(storeSIndex, "store", trans);
            storeDatabase.insertParent_(storeSIndex, rootSIndex, trans);
            assertNotNull(storeSIndex);
            store = new SOID(storeSIndex, OID.ROOT);
            mines = new SOID(storeSIndex, OID.generate());
            sm.add_(storeSIndex);
            metaDatabase.createStore_(storeSIndex, trans);
            directoryService.createOA_(Type.FILE, storeSIndex, mines.oid(), OID.ROOT, "mines", trans);
            directoryService.createCA_(mines, KIndex.MASTER, trans);

            trans.commit_();
        }
        propagator = new SyncStatusPropagator(directoryService, sm,
                new SingleuserStoreHierarchy(new StoreDatabase(dbcw)), syncStatusRequests,
                outOfSyncDatabase);
        syncStatusOnline.set(true);
        when(factWaldo.create()).thenReturn(waldoClient);
        statusChecker = new TestingBatchStatusChecker(factWaldo);
    }

    public int countOutOfSyncFiles() throws SQLException {
        int oosCount = 0;
        IDBIterator<OutOfSyncFile> results = outOfSyncDatabase.selectPage_(0L, 1000);
        while (results.next_()) {
            results.get_();
            oosCount++;
        }
        return oosCount;
    }

    protected void runScheduled_() {
        AbstractEBSelfHandling ev;
        while ((ev = scheduled.poll()) != null)
            ev.handle_();
    }

    static class TestingBatchStatusChecker extends SyncStatusBatchStatusChecker
    {
        public boolean completed;

        public TestingBatchStatusChecker(WaldoAsyncClient.Factory factWaldo) {
            super(factWaldo);
        }

        @Override
        public void submitLocationStatusBatch(LocationStatusBatch locationStatusBatch,
                AsyncTaskCallback callback,
                Function<LocationStatusBatchResult, Boolean, Exception> responseFunction) {
            logger.trace("enter submitLocationStatusBatch");
            super.submitLocationStatusBatch(locationStatusBatch, callback, responseFunction);
            completed = true;
            logger.trace("exit submitLocationStatusBatch");
        }
    }
}
