/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.linker.IgnoreList;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LogUtil;
import com.aerofs.lib.LogUtil.Level;
import com.aerofs.lib.Param;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.ex.ExFileIO;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.testlib.AbstractTest;
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
import java.util.Date;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.when;

public class TestLinkedStorage extends AbstractTest
{
    // a big stack of mocks needed for a LinkedStorage instance...
    @Mock private DirectoryService _ds;
    @Mock private InjectableDriver _dr;
    @Mock private CfgAbsRootAnchor _cfgAbsRootAnchor;
    @Mock private CfgAbsAuxRoot _cfgAbsAuxRoot;
    @Mock private CfgStoragePolicy _cfgStoragePolicy;
    @Mock private IgnoreList _il;
    @Mock private OA _oa;
    @Mock private CoreDBCW _dbcw;

    SOID _soid = new SOID(new SIndex(1), new OID(UniqueID.generate()));
    SOKID _sokid = new SOKID(_soid, KIndex.MASTER);
    private final FID _fid = new FID(new byte[0]);

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    private InjectableFile.Factory _factFile;
    private InjectableFile _rootDir;
    private InjectableFile _dataDir;
    private InjectableFile _revDir;
    private LinkedStorage _storage;
    private TransManager _tm;
    private boolean _useHistory;

    @Before
    public void before() throws Exception
    {
        LogUtil.setLevel(TestLinkedStorage.class, Level.INFO);

        _factFile = new InjectableFile.Factory();
        _rootDir = _factFile.create(_tempFolder.getRoot().getPath());
        _dataDir = _factFile.create(_rootDir, "data");
        _dataDir.mkdirs();
        InjectableFile _auxDir = _factFile.create(_rootDir, Param.AUXROOT_PREFIX);
        _revDir = _factFile.create(_auxDir, Param.AuxFolder.REVISION._name);

        // these mocks are used to set up the LinkedStorage but also when
        // committing/ending transactions.
        when(_dbcw.get()).then(RETURNS_MOCKS);
        when(_dr.getFIDAndType(any(String.class))).thenReturn(new FIDAndType(_fid, false));
        when(_cfgAbsRootAnchor.get()).thenReturn(_rootDir.getAbsolutePath());
        when(_cfgAbsAuxRoot.get()).thenReturn(_auxDir.getAbsolutePath());
        when(_ds.getOA_(_soid)).thenReturn(_oa);
        when(_ds.getOANullable_(_soid)).thenReturn(_oa);
        when(_oa.fid()).thenReturn(_fid);

        when(_cfgStoragePolicy.useHistory()).thenAnswer(new Answer<Boolean>(){
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable
            {
                return _useHistory;
            }
        });

        _storage = new LinkedStorage();
        _storage.inject_(
                _factFile, new IFIDMaintainer.Factory(_dr, _ds),
                _cfgAbsRootAnchor, _cfgAbsAuxRoot, _cfgStoragePolicy,
                _il, new LinkedRevProvider(_factFile), null);
        _storage.init_();

        _tm = new TransManager(new Trans.Factory(_dbcw));

        _useHistory = true;
    }

    @After
    public void after() throws Exception
    {
        LogUtil.setLevel(TestLinkedStorage.class, Level.NONE);

        _useHistory = true;
    }

    @Test
    public void shouldUndoMove() throws IOException, SQLException
    {
        int fcount =  _rootDir.listFiles().length;
        IPhysicalFile pre;
        IPhysicalFile post;
        Trans trans;

        // create premove & create the PhysicalFile object. Only create the PhysicalFile
        // for post to have a "to" destination in the move call.
        pre = createNamedFile("premove");
        post = _storage.newFile_(_sokid, new Path("postmove"));

        assert _rootDir.list().length == (fcount + 1) : "Wrong # files";
        trans = _tm.begin_();
        pre.move_(post, PhysicalOp.APPLY, trans);
        trans.end_();
        assert _rootDir.list().length == (fcount + 1) : "Wrong # files";

        // using getLength() as a simple wrapper for throwIfNotFile
        FileUtil.getLength(new File(pre.getAbsPath_()));
        try
        {
            FileUtil.getLength(new File(post.getAbsPath_()));
            assert false : "Should have thrown ExFileIO. postfile exists?";
        } catch (ExFileIO expected) {}
    }

    @Test
    public void shouldMoveToRevOnDelete() throws IOException, SQLException
    {
        int fcount =  _rootDir.listFiles().length;
        IPhysicalFile pfile;
        Trans trans;

        // create a test file on the disk and in LinkedStorage:
        trans = _tm.begin_();
        pfile = createNamedFile("TestLocalStore");
        assert _rootDir.list().length == (fcount + 1) : "Didn't create a new file?!";
        checkRevDirContents(0);
        trans.commit_();
        trans.end_();

        // force move-to-rev and check that the file counts are updated correctly:
        trans = _tm.begin_();
        pfile.delete_(PhysicalOp.APPLY, trans);
        assert _rootDir.list().length == (fcount) : "Move out of root";
        checkRevDirContents(1);
        trans.commit_();
        trans.end_();
    }

    @Test
    public void shouldIncRevOnApply() throws IOException, SQLException
    {
        LinkedPrefix prefix;
        IPhysicalFile pfile;

        // create a test file on the disk and in LinkedStorage:
        Trans txn = _tm.begin_();
        pfile = createNamedFile("updateme");
        txn.commit_();
        txn.end_();

        checkRevDirContents(0);

        // update & check that the revision dir is populated:
        SOCKID sockid = new SOCKID(_sokid,  CID.CONTENT);
        prefix = new LinkedPrefix(_factFile, sockid, _cfgAbsAuxRoot.get());
        FileUtil.createNewFile(new File(prefix._f.getAbsolutePath()));

        txn = _tm.begin_();
        _storage.apply_(prefix, pfile, true, new Date().getTime(), txn);
        txn.commit_();
        txn.end_();

        checkRevDirContents(1);
    }

    @Test
    public void shouldNotIncOnApplyRollback() throws IOException, SQLException
    {
        LinkedPrefix prefix;
        IPhysicalFile pfile;

        Trans txn = _tm.begin_();
        pfile = createNamedFile("rollbackapply");
        txn.commit_();
        txn.end_();

        checkRevDirContents(0);

        SOCKID sockid = new SOCKID(_sokid,  CID.CONTENT);
        prefix = new LinkedPrefix(_factFile, sockid, _cfgAbsAuxRoot.get());
        FileUtil.createNewFile(new File(prefix._f.getAbsolutePath()));

        txn = _tm.begin_();
        _storage.apply_(prefix, pfile, true, 0, txn);
        txn.end_();

        checkRevDirContents(0);
    }

    // test behavior of delete when version history is disabled.
    @Test
    public void shouldNotUseHistoryOnDelete() throws IOException, SQLException
    {
        int fcount =  _rootDir.listFiles().length;
        IPhysicalFile pfile;
        Trans trans;

        _useHistory = false;

        // create a test file on the disk and in LinkedStorage:
        trans = _tm.begin_();
        pfile = createNamedFile("TestLocalStore");
        assert _rootDir.list().length == (fcount + 1) : "Didn't create a new file?!";
        checkRevDirContents(0);
        trans.commit_();
        trans.end_();

        // should be no versions stored after commit
        trans = _tm.begin_();
        pfile.delete_(PhysicalOp.APPLY, trans);
        assert _rootDir.list().length == (fcount) : "Move out of root";
        trans.commit_();
        trans.end_();
        checkRevDirContents(0);

        // delete with rollback should preserve original file:
        trans = _tm.begin_();
        pfile = createNamedFile("TestRollback");
        trans.commit_();
        trans.end_();

        trans = _tm.begin_();
        assert _rootDir.list().length == (fcount + 1) : "invariant";
        pfile.delete_(PhysicalOp.APPLY, trans);
        assert _rootDir.list().length == (fcount) : "delete from root";
        trans.end_();

        checkRevDirContents(0);
        assert _rootDir.list().length == (fcount + 1) : "restored to root";
        FileUtil.getLength(new File(pfile.getAbsPath_()));
    }

    // test behavior of apply_ when version history is disabled
    @Test
    public void shouldNotUseHistoryOnApply() throws IOException, SQLException
    {
        LinkedPrefix prefix;
        IPhysicalFile pfile;

        _useHistory = false;

        // create a test file on the disk and in LinkedStorage:
        Trans txn = _tm.begin_();
        pfile = createNamedFile("update.nohistory");
        txn.commit_();
        txn.end_();

        // apply a few updates, checking that after each commit there is no growth in
        // revisions dir:
        SOCKID sockid = new SOCKID(_sokid,  CID.CONTENT);

        prefix = new LinkedPrefix(_factFile, sockid, _cfgAbsAuxRoot.get());
        FileUtil.createNewFile(new File(prefix._f.getAbsolutePath()));

        txn = _tm.begin_();
        _storage.apply_(prefix, pfile, true, 0, txn);
        txn.commit_();
        txn.end_();

        checkRevDirContents(0);

        // test the original file is replaced on rollback:
        prefix = new LinkedPrefix(_factFile, sockid, _cfgAbsAuxRoot.get());
        FileUtil.createNewFile(new File(prefix._f.getAbsolutePath()));
        txn = _tm.begin_();
        _storage.apply_(prefix, pfile, true, 0, txn);
        txn.end_();

        checkRevDirContents(0);
        assert FileUtil.getLength(new File(pfile.getAbsPath_())) == 0 : "Original file missing";
    }

    @Test
    public void shouldHandleCommitError() throws IOException, SQLException
    {
        LinkedPrefix prefix;
        IPhysicalFile pfile;
        int revs =  _revDir.listFiles().length;

        _useHistory = false;

        Trans txn = _tm.begin_();
        pfile = createNamedFile("updateme");
        txn.commit_();
        txn.end_();

        // delete the rev file to provoke an IO exception in commit handler:
        txn = _tm.begin_();
        pfile.delete_(PhysicalOp.APPLY, txn);

        for (InjectableFile ifile : _revDir.listFiles())
        {
            ifile.delete();
        }
        txn.commit_();
        txn.end_();
        // should commit without rethrowing.
    }

    private IPhysicalFile createNamedFile(String fname) throws IOException
    {
        IPhysicalFile retval = _storage.newFile_(_sokid, new Path(fname));
        FileUtil.createNewFile(new File(retval.getAbsPath_()));
        return retval;
    }

    private void checkRevDirContents(int expected)
    {
        if (_revDir.listFiles().length != expected)
        {
            System.out.println("_revDir " + _revDir.listFiles().length
                    + " , expected " + expected);
            for (InjectableFile file : _revDir.listFiles())
            {
                System.out.println(": " + file.getAbsolutePath());
            }
        }
        assert _revDir.listFiles().length == expected : "Wrong # files in revDir ("
                + _revDir.listFiles().length + ")";
    }
}
