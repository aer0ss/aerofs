/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Revision;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.linked.db.NRODatabase;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.daemon.core.phy.linked.linker.IgnoreList;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.ex.ExFileIO;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.aerofs.rocklog.RockLog;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestLinkedStorage extends AbstractTest
{
    // a big stack of mocks needed for a LinkedStorage instance...
    @Mock private DirectoryService ds;
    @Mock private InjectableDriver dr;
    @Mock private CfgAbsRoots cfgAbsRoots;
    @Mock private CfgDatabase cfgDb;
    @Mock private CfgStoragePolicy cfgStoragePolicy;
    @Mock private IgnoreList il;
    @Mock private OA oa;
    @Mock private CoreDBCW dbcw;
    @Mock LinkerRootMap lrm;
    @Mock IOSUtil os;

    SOID soid = new SOID(new SIndex(1), new OID(UniqueID.generate()));
    SOKID sokid = new SOKID(soid, KIndex.MASTER);
    private final FID fid = new FID(new byte[0]);

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private InjectableFile.Factory factFile;
    private InjectableFile rootDir;
    private InjectableFile revDir;
    private LinkedStorage storage;
    private TransManager tm;
    private boolean useHistory;

    private final SIndex sidx = new SIndex(1);
    private final SID rootSID = SID.generate();

    @Before
    public void before() throws Exception
    {
        AppRoot.set("foo");

        factFile = new InjectableFile.Factory();
        InjectableFile tmpDir = factFile.create(tempFolder.getRoot().getPath());
        rootDir = factFile.create(tmpDir, "AeroFS");
        rootDir.mkdirs();
        String auxDir = Cfg.absAuxRootForPath(rootDir.getAbsolutePath(), rootSID);
        revDir = factFile.create(auxDir, AuxFolder.HISTORY._name);
        revDir.ensureDirExists();
        factFile.create(auxDir, AuxFolder.PREFIX._name).ensureDirExists();

        l.info("{} {}", rootDir.getAbsolutePath(), revDir.getAbsolutePath());

        // these mocks are used to set up the LinkedStorage but also when
        // committing/ending transactions.
        when(dbcw.get()).then(RETURNS_MOCKS);
        when(dr.getFIDAndTypeNullable(any(String.class))).thenReturn(new FIDAndType(fid, false));
        when(ds.getOA_(soid)).thenReturn(oa);
        when(ds.getOANullable_(soid)).thenReturn(oa);
        when(oa.fid()).thenReturn(fid);

        when(lrm.absRootAnchor_(rootSID)).thenReturn(rootDir.getAbsolutePath());
        when(cfgAbsRoots.getNullable(rootSID)).thenReturn(rootDir.getAbsolutePath());
        when(cfgAbsRoots.getAll()).thenReturn(ImmutableMap.of(rootSID, rootDir.getAbsolutePath()));

        when(cfgStoragePolicy.useHistory()).thenAnswer(new Answer<Boolean>()
        {
            @Override
            public Boolean answer(InvocationOnMock invocation)
                    throws Throwable
            {
                return useHistory;
            }
        });

        IMapSIndex2SID sidx2sid = mock(IMapSIndex2SID.class);
        when(sidx2sid.get_(sidx)).thenReturn(rootSID);

        IStores stores = mock(IStores.class);
        when(stores.getPhysicalRoot_(any(SIndex.class))).thenReturn(sidx);

        NRODatabase nro = mock(NRODatabase.class);
        IDBIterator<SOID> it = new IDBIterator<SOID>() {
            @Override
            public SOID get_() throws SQLException
            {
                return null;
            }

            @Override
            public boolean next_() throws SQLException
            {
                return false;
            }

            @Override
            public void close_() throws SQLException
            {
            }

            @Override
            public boolean closed_()
            {
                throw new UnsupportedOperationException();
            }
        };

        when(nro.getConflicts_(any(SOID.class))).thenReturn(it);

        // NB: this is to avoid running afoul of the FID consistency check
        when(ds.getSOIDNullable_(any(FID.class))).thenReturn(sokid.soid());

        RepresentabilityHelper rh = new RepresentabilityHelper(os, dr, lrm,
                mock(IMetaDatabase.class), nro, factFile, mock(RitualNotificationServer.class), ds);

        storage = new LinkedStorage(factFile, new IFIDMaintainer.Factory(dr, ds), lrm,
                mock(IOSUtil.class), mock(InjectableDriver.class), rh, stores, sidx2sid,
                cfgAbsRoots, cfgStoragePolicy, il, null, mock(LinkedStagingArea.class),
                new LinkedRevProvider(lrm, factFile, new LinkedRevProvider.TimeSource()),
                mock(RockLog.class), mock(CoreScheduler.class));

        tm = new TransManager(new Trans.Factory(dbcw));

        useHistory = true;
    }

    @After
    public void after() throws Exception
    {
        useHistory = true;
    }

    @Test
    public void shouldUndoMove() throws IOException, SQLException
    {
        int fcount =  rootDir.listFiles().length;
        IPhysicalFile pre;
        Trans trans;

        // create premove & create the PhysicalFile object. Only create the PhysicalFile
        // for post to have a "to" destination in the move call.
        pre = createNamedFile("premove");
        ResolvedPath post = new ResolvedPath(rootSID,
                ImmutableList.of(sokid.soid()),
                ImmutableList.of("postmove"));

        assert rootDir.list().length == (fcount + 1) : "Wrong # files";
        trans = tm.begin_();
        pre.move_(post, sokid.kidx(), PhysicalOp.APPLY, trans);
        trans.end_();
        assert rootDir.list().length == (fcount + 1) : "Wrong # files";

        // using getLength() as a simple wrapper for throwIfNotFile
        FileUtil.getLength(new File(pre.getAbsPath_()));
        try
        {
            FileUtil.getLength(new File(storage.newFile_(post, sokid.kidx()).getAbsPath_()));
            assert false : "Should have thrown ExFileIO. postfile exists?";
        } catch (ExFileIO expected) {}
    }

    @Test
    public void shouldMoveToRevOnDelete() throws IOException, SQLException
    {
        int fcount =  rootDir.listFiles().length;
        IPhysicalFile pfile;
        Trans trans;

        // create a test file on the disk and in LinkedStorage:
        trans = tm.begin_();
        pfile = createNamedFile("TestLocalStore");
        assert rootDir.list().length == (fcount + 1) : "Didn't create a new file?!";
        checkVersionCount("TestLocalStore", 0);
        trans.commit_();
        trans.end_();

        // force move-to-rev and check that the file counts are updated correctly:
        trans = tm.begin_();
        pfile.delete_(PhysicalOp.APPLY, trans);
        assert rootDir.list().length == (fcount) : "Move out of root";
        checkVersionCount("TestLocalStore", 1);
        trans.commit_();
        trans.end_();
    }

    @Test
    public void shouldIncRevOnApply() throws IOException, SQLException
    {
        LinkedPrefix prefix;
        IPhysicalFile pfile;

        // create a test file on the disk and in LinkedStorage:
        Trans txn = tm.begin_();
        pfile = createNamedFile("updateme");
        txn.commit_();
        txn.end_();

        checkVersionCount("updateme", 0);

        // update & check that the revision dir is populated:
        prefix = new LinkedPrefix(storage, sokid,
                LinkedPath.auxiliary(null, storage.auxFilePath(sokid, AuxFolder.PREFIX)));
        System.out.println(prefix._f.getAbsolutePath());
        prefix._f.createNewFile();

        txn = tm.begin_();
        storage.apply_(prefix, pfile, true, new Date().getTime(), txn);
        txn.commit_();
        txn.end_();

        checkVersionCount("updateme", 1);
    }

    @Test
    public void shouldNotIncOnApplyRollback() throws IOException, SQLException
    {
        LinkedPrefix prefix;
        IPhysicalFile pfile;

        Trans txn = tm.begin_();
        pfile = createNamedFile("rollbackapply");
        txn.commit_();
        txn.end_();

        checkVersionCount("rollbackapply", 0);

        prefix = new LinkedPrefix(storage, sokid,
                LinkedPath.auxiliary(null, storage.auxFilePath(sokid, AuxFolder.PREFIX)));
        FileUtil.createNewFile(new File(prefix._f.getAbsolutePath()));

        txn = tm.begin_();
        storage.apply_(prefix, pfile, true, 0, txn);
        txn.end_();

        checkVersionCount("rollbackapply", 0);
    }

    // test behavior of delete when sync history is disabled.
    @Test
    public void shouldNotUseHistoryOnDelete() throws IOException, SQLException
    {
        int fcount =  rootDir.listFiles().length;
        IPhysicalFile pfile;
        Trans trans;

        useHistory = false;

        // create a test file on the disk and in LinkedStorage:
        trans = tm.begin_();
        pfile = createNamedFile("TestLocalStore");
        assert rootDir.list().length == (fcount + 1) : "Didn't create a new file?!";
        checkVersionCount("TestLocalStore", 0);
        trans.commit_();
        trans.end_();

        // should be no versions stored after commit
        trans = tm.begin_();
        pfile.delete_(PhysicalOp.APPLY, trans);
        assert rootDir.list().length == (fcount) : "Move out of root";
        trans.commit_();
        trans.end_();
        checkVersionCount("TestLocalStore", 0);

        // delete with rollback should preserve original file:
        trans = tm.begin_();
        pfile = createNamedFile("TestRollback");
        trans.commit_();
        trans.end_();

        trans = tm.begin_();
        assert rootDir.list().length == (fcount + 1) : "invariant";
        pfile.delete_(PhysicalOp.APPLY, trans);
        assert rootDir.list().length == (fcount) : "delete from root";
        trans.end_();

        checkVersionCount("TestRollback", 0);
        assert rootDir.list().length == (fcount + 1) : "restored to root";
        FileUtil.getLength(new File(pfile.getAbsPath_()));
    }

    // test behavior of apply_ when sync history is disabled
    @Test
    public void shouldNotUseHistoryOnApply() throws IOException, SQLException
    {
        LinkedPrefix prefix;
        IPhysicalFile pfile;

        useHistory = false;

        // create a test file on the disk and in LinkedStorage:
        Trans txn = tm.begin_();
        pfile = createNamedFile("update.nohistory");
        txn.commit_();
        txn.end_();

        // apply a few updates, checking that after each commit there is no growth in
        // revisions dir:
        prefix = new LinkedPrefix(storage, sokid,
                LinkedPath.auxiliary(null, storage.auxFilePath(sokid, AuxFolder.PREFIX)));
        FileUtil.createNewFile(new File(prefix._f.getAbsolutePath()));

        txn = tm.begin_();
        storage.apply_(prefix, pfile, true, 0, txn);
        txn.commit_();
        txn.end_();

        checkVersionCount("update.nohistory", 0);

        // test the original file is replaced on rollback:
        prefix = new LinkedPrefix(storage, sokid,
                LinkedPath.auxiliary(null, storage.auxFilePath(sokid, AuxFolder.PREFIX)));
        FileUtil.createNewFile(new File(prefix._f.getAbsolutePath()));
        txn = tm.begin_();
        storage.apply_(prefix, pfile, true, 0, txn);
        txn.end_();

        checkVersionCount("update.nohistory", 0);
        assert FileUtil.getLength(new File(pfile.getAbsPath_())) == 0 : "Original file missing";
    }

    @Test
    public void shouldHandleCommitError() throws IOException, SQLException
    {
        IPhysicalFile pfile;
        useHistory = false;

        Trans txn = tm.begin_();
        pfile = createNamedFile("updateme");
        txn.commit_();
        txn.end_();

        // delete the rev file to provoke an IO exception in commit handler:
        txn = tm.begin_();
        pfile.delete_(PhysicalOp.APPLY, txn);

        for (InjectableFile ifile : revDir.listFiles())
        {
            ifile.deleteIgnoreErrorRecursively();
        }
        txn.commit_();
        txn.end_();
        // should commit without rethrowing.
    }

    private IPhysicalFile createNamedFile(String fname) throws IOException, SQLException
    {
        IPhysicalFile retval = storage.newFile_(new ResolvedPath(rootSID,
                ImmutableList.of(sokid.soid()),
                ImmutableList.of(fname)),
                sokid.kidx());
        FileUtil.createNewFile(new File(retval.getAbsPath_()));
        return retval;
    }

    private void checkVersionCount(String path, int expected) throws IOException, SQLException
    {
        Collection<Revision> revisions = storage.getRevProvider()
                .listRevHistory_(Path.fromString(rootSID, path));
        if (revisions.size() != expected)
        {
            for (Revision rev : revisions)
            {
                System.out.println(": " + BaseUtil.utf2string(rev._index));
            }
            fail("revDir " + revisions.size() + " , expected " + expected);
        }
    }
}
