/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Child;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Revision;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.block.BlockStorage.FileAlreadyExistsException;
import com.aerofs.daemon.core.phy.block.BlockStorageDatabase.FileInfo;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend.TokenWrapper;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.CfgAbsDefaultAuxRoot;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.db.ITransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.testlib.InMemorySQLiteDBCW;
import com.aerofs.lib.id.KIndex;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

public class TestBlockStorage extends AbstractBlockTest
{
    @Mock Trans t;
    @Mock Token tk;
    @Mock TokenManager tc;
    @Mock TCB tcb;
    @Mock TransManager tm;
    @Mock CoreScheduler sched;
    @Mock CfgAbsDefaultAuxRoot auxRoot;
    @Mock CfgStoragePolicy storagePolicy;

    class TestableTrans extends Trans
    {
        private TestableTrans(TransManager tm)
        {
            super(new Trans.Factory(mock(IDBCW.class)), tm);
        }
    }

    // use in-memory DB
    InjectableFile.Factory fileFactory = new InjectableFile.Factory();
    InMemorySQLiteDBCW idbcw = new InMemorySQLiteDBCW();
    BlockStorageDatabase bsdb = new BlockStorageDatabase(idbcw);

    @Mock IBlockStorageBackend bsb;

    BlockStorage bs;
    boolean useHistory;

    static final SID rootSID = SID.generate();

    Path mkpath(String path)
    {
        return Path.fromString(rootSID, path);
    }

    ResolvedPath mkpath(String path, final SOID soid)
    {
        List<String> elems = ImmutableList.copyOf(path.split("/"));
        List<SOID> soids = Lists.newArrayList(Lists.transform(elems,
                s -> new SOID(soid.sidx(), OID.generate())));
        soids.set(elems.size() - 1, soid);
        return new ResolvedPath(rootSID, soids, elems);
    }

    @Before
    public void setUp() throws Exception
    {
        idbcw.init_();
        new BlockStorageSchema()
                .create_(idbcw.getConnection().createStatement(), idbcw);

        when(tm.begin_()).thenAnswer(invocation -> new TestableTrans(tm));
        when(tc.acquire_(any(Cat.class), anyString())).thenReturn(tk);
        when(tk.pseudoPause_(anyString())).thenReturn(tcb);
        String testTempDir = testTempDirFactory.getTestTempDir().getAbsolutePath();
        when(auxRoot.get()).thenReturn(testTempDir);
        when(storagePolicy.useHistory()).thenAnswer(invocation -> useHistory);

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ((AbstractEBSelfHandling) args[0]).handle_();
            return null;
        }).when(sched).schedule(any(IEvent.class), anyLong());

        // shame @InjectMocks does not deal with a mix of Mock and real objects...
        bs = new BlockStorage();
        bs.inject_(auxRoot, storagePolicy, tc, tm, sched, fileFactory, bsb, bsdb,
                Collections.<IBlockStorageInitable>emptySet());
        bs.init_();

        useHistory = true;
    }

    @After
    public void tearDown() throws Exception
    {
        idbcw.fini_();
    }

    private SOID newSOID()
    {
        return new SOID(new SIndex(1), new OID(UniqueID.generate()));
    }

    private SOKID newSOKID()
    {
        return new SOKID(newSOID(), KIndex.MASTER);
    }

    private void createFile(String path, SOKID sokid) throws Exception
    {
        IPhysicalFile file = bs.newFile_(mkpath(path, sokid.soid()), sokid.kidx());
        file.create_(PhysicalOp.APPLY, t);
    }

    private void deleteFile(String path, SOKID sokid) throws Exception
    {
        IPhysicalFile file = bs.newFile_(mkpath(path, sokid.soid()), sokid.kidx());
        file.delete_(PhysicalOp.APPLY, t);
    }

    private void moveFile(String pathFrom, SOKID sokidFrom, String pathTo, SOKID sokidTo)
            throws Exception
    {
        bs.newFile_(mkpath(pathFrom, sokidFrom.soid()), sokidFrom.kidx())
                .move_(mkpath(pathTo, sokidTo.soid()), sokidTo.kidx(), PhysicalOp.APPLY, t);
    }

    private boolean exists(String path, SOKID sokid) throws IOException
    {
        IPhysicalFile file = bs.newFile_(mkpath(path, sokid.soid()), sokid.kidx()) ;
        try {
            file.newInputStream().close();
        } catch (FileNotFoundException e) {
            return false;
        }
        return true;
    }

    private void store(
            String path, SOKID sokid,
            byte[] content, boolean wasPresent, long mtime)
            throws Exception {
        store(path, sokid, new ByteArrayInputStream(content), wasPresent, mtime);
    }

    private void store(String path, SOKID sokid, InputStream content,
                       boolean wasPresent, long mtime) throws IOException, SQLException
    {
        IPhysicalPrefix prefix = bs.newPrefix_(sokid, null);
        IPhysicalFile file = bs.newFile_(mkpath(path, sokid.soid()), sokid.kidx()) ;

        try (OutputStream out = prefix.newOutputStream_(false)) {
            ByteStreams.copy(content, out);
        }
        try (Trans t = tm.begin_()) {
            bs.apply_(prefix, file, wasPresent, mtime, t);
            t.commit_();
        }
    }

    private byte[] fetch(String path, SOKID sokid) throws Exception
    {
        IPhysicalFile file = bs.newFile_(mkpath(path, sokid.soid()), sokid.kidx()) ;
        InputStream in = file.newInputStream();
        return ByteStreams.toByteArray(in);
    }


    private boolean revChildrenEquals(String path, Child... children) throws Exception
    {
        Collection<Child> result = bs.getRevProvider().listRevChildren_(mkpath(path));
        return Sets.newHashSet(result).equals(Sets.newHashSet(children));
    }

    static abstract class RevisionMatcher
    {
        abstract boolean matches(Revision r);

        static RevisionMatcher any()
        {
            return new RevisionMatcher()
            {
                @Override
                public boolean matches(Revision r)
                {
                    return true;
                }
            };
        }

        static RevisionMatcher withMtime(final long t)
        {
            return new RevisionMatcher() {
                @Override
                boolean matches(Revision r)
                {
                    return r._mtime == t;
                }
            };
        }
    }

    private boolean revHistoryEquals(String path, RevisionMatcher... revisions) throws Exception
    {
        Collection<Revision> result = bs.getRevProvider().listRevHistory_(mkpath(path));
        int i = 0;
        for (Revision r : result) {
            Assert.assertTrue("r" + i + ":" + r, revisions[i].matches(r));
            ++i;
        }
        return true;
    }

    private byte[] fetchRev(String path, byte[] index) throws Exception
    {
        return ByteStreams.toByteArray(
                bs.getRevProvider().getRevInputStream_(mkpath(path), index)._is);
    }

    private byte[] revIndex(String path, int idx) throws Exception
    {
        Object[] r = bs.getRevProvider().listRevHistory_(mkpath(path)).toArray();
        return ((Revision)r[idx < 0 ? r.length + idx : idx])._index;
    }

    private byte[] fetchRev(String path, int idx) throws Exception
    {
        return fetchRev(path, revIndex(path, idx));
    }

    private void delRev(String path, int idx) throws Exception
    {
        bs.getRevProvider().deleteRevision_(mkpath(path), revIndex(path, idx));
    }

    private void delAllRevUnder(String path) throws Exception
    {
        bs.getRevProvider().deleteAllRevisionsUnder_(mkpath(path));
    }

    @Test
    public void shouldCreateFileAndRetrieveEmptyContent() throws Exception
    {
        SOKID sokid = newSOKID();

        createFile("foo/bar", sokid);
        byte[] result = fetch("foo/bar", sokid);
        verify(bsb, never())
                .putBlock(any(ContentBlockHash.class), any(InputStream.class), anyLong());
        verify(bsb, never()).getBlock(any(ContentBlockHash.class));
        Assert.assertArrayEquals(new byte[0], result);
    }

    @Test
    public void shouldThrowWhenTryingToCreateExistingFile() throws Exception
    {
        SOKID sokid = newSOKID();

        createFile("foo/bar", sokid);
        boolean ok = false;
        try {
            createFile("foo/bar", sokid);
        } catch (FileAlreadyExistsException e) {
            ok = true;
        }
        Assert.assertTrue(ok);
        verify(bsb, never())
                .putBlock(any(ContentBlockHash.class), any(InputStream.class), anyLong());
        verify(bsb, never()).getBlock(any(ContentBlockHash.class));
    }

    @Test
    public void shouldWriteNewFile() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

        store("foo/bar", sokid, content, false, 0L);

        verify(bsb).putBlock(forKey(content), any(InputStream.class), eq((long) content.length));
        verify(bsb, never()).getBlock(any(ContentBlockHash.class));
    }

    @Test
    public void shouldWriteExistingFile() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

        createFile("foo/bar", sokid);
        store("foo/bar", sokid, content, true, 0L);

        verify(bsb).putBlock(forKey(content), any(InputStream.class), eq((long) content.length));
        verify(bsb, never()).getBlock(any(ContentBlockHash.class));
    }

    @Test
    public void shouldThrowWhenWritingToUnexpectedlyNonExistingFile() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

        boolean ok = false;
        try {
            store("foo/bar", sokid, content, true, 0L);
        } catch (FileNotFoundException e) {
            ok = true;
        }
        Assert.assertTrue(ok);
        // interestingly, the exception is only thrown when calling apply_ by which time the prefix
        // has already been copied to persistent storage
        //verify(bsb, never()).putBlock(any(IBlockMetadata.class), any(InputStream.class));
        verify(bsb, never()).getBlock(any(ContentBlockHash.class));
    }

    @Test
    public void shouldThrowWhenWritingToUnexpectedlyExistingFile() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

        createFile("foo/bar", sokid);
        boolean ok = false;
        try {
            store("foo/bar", sokid, content, false, 0L);
        } catch (FileAlreadyExistsException e) {
            ok = true;
        }
        Assert.assertTrue(ok);
        // interestingly, the exception is only thrown when calling apply_ by which time the prefix
        // has already been copied to persistent storage
        //verify(bsb, never()).putBlock(any(IBlockMetadata.class), any(InputStream.class));
        verify(bsb, never()).getBlock(any(ContentBlockHash.class));
    }

    @Test
    public void shouldFetchContents() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

        store("foo/bar", sokid, content, false, 0L);
        verify(bsb).putBlock(forKey(content), any(InputStream.class), eq((long) content.length));

        when(bsb.getBlock(forKey(content))).thenReturn(new ByteArrayInputStream(content));

        byte[] result = fetch("foo/bar", sokid);

        verify(bsb).getBlock(forKey(content));
        Assert.assertArrayEquals(content, result);
    }

    @Test
    public void shouldThrowWhenAccessingNonExistingFile() throws Exception
    {
        SOKID sokid = newSOKID();

        boolean ok = false;
        try {
            fetch("foo/bar", sokid);
        } catch (FileNotFoundException e) {
            ok = true;
        }
        Assert.assertTrue(ok);
        verify(bsb, never())
                .putBlock(any(ContentBlockHash.class), any(InputStream.class), anyLong());
        verify(bsb, never()).getBlock(any(ContentBlockHash.class));
    }

    @Test
    public void shouldDeleteFile() throws Exception
    {
        SOKID sokid = newSOKID();

        createFile("foo/bar", sokid);
        deleteFile("foo/bar", sokid);

        Assert.assertFalse(exists("foo/bar", sokid));

        verify(bsb, never())
                .putBlock(any(ContentBlockHash.class), any(InputStream.class), anyLong());
        verify(bsb, never()).getBlock(any(ContentBlockHash.class));
    }

    @Test
    public void shouldSucceedWhenTryingToDeleteNonExistingFile() throws Exception
    {
        SOKID sokid = newSOKID();

        deleteFile("foo/bar", sokid);

        verify(bsb, never())
                .putBlock(any(ContentBlockHash.class), any(InputStream.class), anyLong());
        verify(bsb, never()).getBlock(any(ContentBlockHash.class));
        verify(bsb, never()).deleteBlock(any(ContentBlockHash.class), any(TokenWrapper.class));
    }

    @Test
    public void shouldMoveFile() throws Exception
    {
        SOKID from = newSOKID();
        SOKID to = newSOKID();

        createFile("foo/bar", from);
        moveFile("foo/bar", from, "foo/baz/bla", to);

        verify(bsb, never())
                .putBlock(any(ContentBlockHash.class), any(InputStream.class), anyLong());
        verify(bsb, never()).getBlock(any(ContentBlockHash.class));
    }

    @Test
    public void shouldThrowWhenTryingToMoveNonExisitingFile() throws Exception
    {
        SOKID from = newSOKID();
        SOKID to = newSOKID();

        boolean ok = false;
        try {
            moveFile("foo/bar", from, "foo/baz/bla", to);
        } catch (FileNotFoundException e) {
            ok = true;
        }
        Assert.assertTrue(ok);

        verify(bsb, never())
                .putBlock(any(ContentBlockHash.class), any(InputStream.class), anyLong());
        verify(bsb, never()).getBlock(any(ContentBlockHash.class));
    }

    @Test
    public void shouldThrowWhenTryingToMoveOverExistingFile() throws Exception
    {
        SOKID from = newSOKID();
        SOKID to = newSOKID();

        createFile("foo/bar", from);
        createFile("foo/baz/bla", to);
        boolean ok = false;
        try {
            moveFile("foo/bar", from, "foo/baz/bla", to);
        } catch (FileAlreadyExistsException e) {
            ok = true;
        }
        Assert.assertTrue(ok);

        verify(bsb, never())
                .putBlock(any(ContentBlockHash.class), any(InputStream.class), anyLong());
        verify(bsb, never()).getBlock(any(ContentBlockHash.class));
    }

    @Test
    public void shouldIgnoreFolderFolderCreationConflict() throws Exception
    {
        bs.newFolder_(mkpath("foo/bar", newSOID())).create_(PhysicalOp.APPLY, t);
        bs.newFolder_(mkpath("foo/bar", newSOID())).create_(PhysicalOp.APPLY, t);
    }

    @Test
    public void shouldIgnoreFolderFileCreationConflict() throws Exception
    {
        bs.newFolder_(mkpath("foo/bar", newSOID())).create_(PhysicalOp.APPLY, t);
        createFile("foo/bar", newSOKID());
    }

    @Test
    public void shouldIgnoreFileFolderCreationConflict() throws Exception
    {
        createFile("foo/bar", newSOKID());
        bs.newFolder_(mkpath("foo/bar", newSOID())).create_(PhysicalOp.APPLY, t);
    }

    @Test
    public void shouldIgnoreFolderDeletion() throws Exception
    {
        bs.newFolder_(mkpath("foo/bar", newSOID())).delete_(PhysicalOp.APPLY, t);
    }

    @Test
    public void shouldIgnoreFolderMovement() throws Exception
    {
        bs.newFolder_(mkpath("foo/bar", newSOID()))
                .move_(mkpath("hellow/world", newSOID()), PhysicalOp.APPLY, t);
    }

    @Test
    public void shouldFetchContentDespitePathMismatch() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

        store("foo/bar", sokid, content, false, 0L);
        verify(bsb).putBlock(forKey(content), any(InputStream.class), eq((long) content.length));

        when(bsb.getBlock(forKey(content))).thenReturn(new ByteArrayInputStream(content));

        byte[] result = fetch("some/garbage", sokid);

        verify(bsb).getBlock(forKey(content));
        Assert.assertArrayEquals(content, result);
    }

    @Test
    public void shouldCreateRevisionHistoryOnCreation() throws Exception
    {
        Assert.assertTrue(revChildrenEquals(""));

        SOKID sokid = newSOKID();
        createFile("foo/bar", sokid);
        store("foo/bar", sokid, new byte[0], true, 0L);

        Assert.assertTrue(revChildrenEquals("", new Child("foo", true)));
        Assert.assertTrue(revChildrenEquals("foo", new Child("bar", false)));
        Assert.assertTrue(revHistoryEquals("foo/bar", RevisionMatcher.any()));
    }

    @Test
    public void shouldCreateRevisionHistoryOnDeletion() throws Exception
    {
        Assert.assertTrue(revChildrenEquals(""));

        SOKID sokid = newSOKID();
        createFile("foo/bar", sokid);
        deleteFile("foo/bar", sokid);

        Assert.assertTrue(revChildrenEquals("", new Child("foo", true)));
        Assert.assertTrue(revChildrenEquals("foo", new Child("bar", false)));
        Assert.assertTrue(revHistoryEquals("foo/bar", RevisionMatcher.any()));
    }

    @Test
    public void shouldNotCreateHistoryOnCreationIfDisabled() throws Exception
    {
        Assert.assertTrue(revChildrenEquals(""));

        useHistory = false;

        SOKID sokid = newSOKID();
        createFile("foo/bar", sokid);
        store("foo/bar", sokid, new byte[0], true, 0L);

        Assert.assertTrue(revChildrenEquals(""));
    }

    @Test
    public void shouldNotCreateHistoryOnDeletionIfDisabled() throws Exception
    {
        Assert.assertTrue(revChildrenEquals(""));

        useHistory = false;

        SOKID sokid = newSOKID();
        createFile("foo/bar", sokid);
        deleteFile("foo/bar", sokid);

        Assert.assertTrue(revChildrenEquals(""));
    }

    @Test
    public void shouldDerefContentsOnReplaceIfHistoryDisabled() throws Exception
    {
        byte[] content0 = new byte[] { 9, 8, 7, 6 };
        byte[] content1 = new byte[] { 1, 1, 2, 3 };
        String testPath = "foo/deref/replace";
        SOKID sokid = newSOKID();

        useHistory = false;

        store(testPath, sokid, content0, false, 0);
        store(testPath, sokid, content1, true, 1);

        Assert.assertEquals(0, bsdb.getBlockCount_(contentHash(content0)));
        Assert.assertNotSame(0, bsdb.getBlockCount_(contentHash(content1)));
    }

    @Test
    public void shouldDerefContentsOnDeleteIfHistoryDisabled() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content = new byte[] { 0, 1, 2, 3 };
        String testPath = "foo/bar/baz";

        useHistory = false;

        store(testPath, sokid, content, false, 0);
        Assert.assertNotSame(0, bsdb.getBlockCount_(contentHash(content)));

        deleteFile(testPath, sokid);

        Assert.assertEquals(0, bsdb.getBlockCount_(contentHash(content)));
    }

    @Test
    public void shouldCleanBlocksOnDeleteIfHistoryDisabled() throws Exception
    {
        final List<ITransListener> listeners = Lists.newArrayList();
        byte[] content = new byte[] { 1, 3, 5, 9 };
        String testPath = "foo/clean/deleteme";
        SOKID sokid = newSOKID();

        doAnswer(invocation -> {
            listeners.add((ITransListener)invocation.getArguments()[0]);
            return null;
        }).when(t).addListener_(any(ITransListener.class));

        useHistory = false;

        store(testPath, sokid, content, false, 0);
        deleteFile(testPath, sokid);

        for (ITransListener l : listeners) l.committed_();

        verify(bsb).deleteBlock(forKey(content), any(TokenWrapper.class));
        Assert.assertEquals(0, bsdb.getBlockCount_(contentHash(content)));
    }

    @Test
    public void shouldCleanBlocksOnReplaceIfHistoryDisabled() throws Exception
    {
        final List<ITransListener> listeners = Lists.newArrayList();
        byte[] content0 = new byte[] { 8, 6, 7, 5 };
        byte[] content1 = new byte[] { 3, 0, 9, 9 };
        String testPath = "foo/clean/replaceme";
        SOKID sokid = newSOKID();

        doAnswer(invocation -> {
            listeners.add((ITransListener)invocation.getArguments()[0]);
            return null;
        }).when(t).addListener_(any(ITransListener.class));

        useHistory = false;

        store(testPath, sokid, content0, false, 0);
        store(testPath, sokid, content1, true, 1);

        for (ITransListener l : listeners) l.committed_();

        Assert.assertEquals(0, bsdb.getBlockCount_(contentHash(content0)));
        verify(bsb, times(1)).deleteBlock(forKey(content0), any(TokenWrapper.class));
        verify(bsb, never()).deleteBlock(forKey(content1), any(TokenWrapper.class));
    }

    @Test
    public void shouldFetchRevisionContents() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content0 = new byte[] {0, 1, 2, 3};
        byte[] content1 = new byte[] {4, 5, 6, 7};

        store("foo/bar", sokid, content0, false, 0L);
        store("foo/bar", sokid, content1, true, 0L);

        when(bsb.getBlock(forKey(content0))).thenReturn(new ByteArrayInputStream(content0));
        when(bsb.getBlock(forKey(content1))).thenReturn(new ByteArrayInputStream(content1));

        byte[] result = fetchRev("foo/bar", -1);
        Assert.assertArrayEquals(content0, result);

        verify(bsb).getBlock(forKey(content0));
        verify(bsb, never()).getBlock(forKey(content1));
    }

    @Test
    public void shouldDeleteRevision() throws Exception
    {
        Assert.assertTrue(revChildrenEquals(""));

        SOKID sokid = newSOKID();
        createFile("foo/bar", sokid);
        store("foo/bar", sokid, new byte[0], true, 0L);
        store("foo/bar", sokid, new byte[0], true, 1L);
        store("foo/bar", sokid, new byte[0], true, 2L);

        Assert.assertTrue(revChildrenEquals("", new Child("foo", true)));
        Assert.assertTrue(revChildrenEquals("foo", new Child("bar", false)));
        Assert.assertTrue(revHistoryEquals("foo/bar",
                RevisionMatcher.any(),
                RevisionMatcher.withMtime(0L),
                RevisionMatcher.withMtime(1L)));

        delRev("foo/bar", 1);

        Assert.assertTrue(revHistoryEquals("foo/bar",
                RevisionMatcher.any(),
                RevisionMatcher.withMtime(1L)));
    }

    @Test
    public void shouldDeleteAllRevisionsUnder() throws Exception
    {
        Assert.assertTrue(revChildrenEquals(""));

        SOKID sokid1 = newSOKID();
        createFile("foo/bar", sokid1);
        store("foo/bar", sokid1, new byte[0], true, 0L);
        store("foo/bar", sokid1, new byte[0], true, 1L);
        store("foo/bar", sokid1, new byte[0], true, 2L);
        SOKID sokid2 = newSOKID();
        createFile("foo/baz", sokid2);
        store("foo/baz", sokid2, new byte[0], true, 0L);

        Assert.assertTrue(revChildrenEquals("", new Child("foo", true)));
        Assert.assertTrue(revChildrenEquals("foo",
                new Child("bar", false),
                new Child("baz", false)));
        Assert.assertTrue(revHistoryEquals("foo/bar",
                RevisionMatcher.any(),
                RevisionMatcher.withMtime(0L),
                RevisionMatcher.withMtime(1L)));
        Assert.assertTrue(revHistoryEquals("foo/baz",
                RevisionMatcher.any()));

        delAllRevUnder("foo");

        Assert.assertTrue(revChildrenEquals(""));
        Assert.assertTrue(revChildrenEquals("foo"));
        Assert.assertTrue(revHistoryEquals("foo/bar"));
        Assert.assertTrue(revHistoryEquals("foo/baz"));
    }

    @Test
    public void shouldNotDeleteFileRevWhenDeletingRevUnder() throws Exception
    {
        Assert.assertTrue(revChildrenEquals(""));

        SOKID sokid1 = newSOKID();
        createFile("foo/bar", sokid1);
        store("foo/bar", sokid1, new byte[0], true, 0L);
        SOKID sokid2 = newSOKID();
        createFile("foo", sokid2);
        store("foo", sokid2, new byte[0], true, 0L);

        Assert.assertTrue(revChildrenEquals("",
                new Child("foo", true),
                new Child("foo", false)));
        Assert.assertTrue(revChildrenEquals("foo", new Child("bar", false)));
        Assert.assertTrue(revHistoryEquals("foo/bar",
                RevisionMatcher.any()));
        Assert.assertTrue(revHistoryEquals("foo",
                RevisionMatcher.any()));

        delAllRevUnder("foo");

        Assert.assertTrue(revChildrenEquals("",
                new Child("foo", false)));
        Assert.assertTrue(revChildrenEquals("foo"));
        Assert.assertTrue(revHistoryEquals("foo/bar"));
        Assert.assertTrue(revHistoryEquals("foo", RevisionMatcher.any()));
    }

    private static class DevCtr extends InputStream
    {
        // Returns blocks of 000...00000
        //                   100...00000
        //                   200...00000
        // .... and so forth, ensuring that each block in the stream is unique.
        // Little-endian is used to avoid caring about block values.
        // Don't generate more than 2 ^ 63 bytes.
        long _size;
        long _idx;
        long _blockctr; // Which block are we in?
        long _blockidx; // Which byte is next in this block?
        long _valueleft; //

        DevCtr(long size)
        {
            _size = size;
            _idx = 0;
            _blockctr = 0;
        }

        @Override
        public int read() throws IOException
        {
            if (_idx >= _size) return -1;
            int retval = (int)(_valueleft % 0x100);
            _valueleft = _valueleft >> 8;
            _idx++;
            _blockidx++;
            if (_blockidx == LibParam.FILE_BLOCK_SIZE) {
                // Block completed, reset for next block
                _blockidx = 0;
                _blockctr++;
                _valueleft = _blockctr;
            }
            return retval;
        }
    }

    private static class DevZero extends InputStream
    {
        long _size;
        DevZero(long size) { _size = size; }

        @Override
        public int read() throws IOException
        {
            if (_size <= 0) return -1;
            --_size;
            return 0;
        }
    }

    @Test
    public void shouldSplitLargeInputIntoBlocks() throws Exception
    {
        SOKID sokid = newSOKID();
        store("foo/bar", sokid, new DevCtr(4 * LibParam.FILE_BLOCK_SIZE + 1), false, 0L);

        verify(bsb, times(4))
                .putBlock(any(ContentBlockHash.class), any(InputStream.class),
                        eq(LibParam.FILE_BLOCK_SIZE));
        verify(bsb).putBlock(any(ContentBlockHash.class), any(InputStream.class), eq(1L));
    }

    @Test
    public void shouldAvoidStoringAlreadyStoredBlocks() throws Exception
    {
        SOKID sokid = newSOKID();
        store("foo/bar", sokid, new DevZero(4 * LibParam.FILE_BLOCK_SIZE + 1), false, 0L);

        // BlockStorageBackend.putBlock() should only be called once per *unique* chunk.
        // So we have four of the same full-size zero-block, and one that's one byte long.
        verify(bsb).putBlock(any(ContentBlockHash.class), any(InputStream.class),
                eq(LibParam.FILE_BLOCK_SIZE));
        verify(bsb).putBlock(any(ContentBlockHash.class), any(InputStream.class), eq(1L));
    }

    @Test
    public void shouldRemoveFilesWhenDeletingStore() throws Exception
    {
        SIndex sidx = new SIndex(1);
        SID sid = SID.generate();
        long id = bsdb.getOrCreateFileIndex_("1-foo", t);

        // setup db: 1 file with 1 chunk
        TestBlock b = newBlock();
        FileInfo info = new FileInfo(id, -1, b._content.length, 0, b._key);
        bsdb.prePutBlock_(b._key, b._content.length, t);
        bsdb.postPutBlock_(b._key, t);
        bsdb.updateFileInfo_(info, t);

        final List<ITransListener> listeners = Lists.newArrayList();
        doAnswer(invocation -> {
            listeners.add((ITransListener)invocation.getArguments()[0]);
            return null;
        }).when(t).addListener_(any(ITransListener.class));

        bs.deleteStore_(sid, sidx, sid, t);

        // check that block was deref'ed
        Assert.assertEquals(0, bsdb.getBlockCount_(b._key));
        // check that file info entry was removed
        Assert.assertNull(bsdb.getFileInfo_(id));

        for (ITransListener l : listeners) l.committed_();

        // check that dead block was removed from backend on commit
        verify(bsb).deleteBlock(eq(b._key), any(TokenWrapper.class));
    }

    @Test
    public void shouldApplyToSyncHistory() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content1 = new byte[] {0, 1, 2, 3, 4};
        String path = "foo/bar";
        store(path, sokid, content1, false, 0L);
        verify(bsb).putBlock(forKey(content1), any(InputStream.class), eq((long) content1.length));
        verify(bsb, never()).getBlock(any(ContentBlockHash.class));

        FileInfo before = bs.getFileInfoNullable_(sokid);
        when(storagePolicy.useHistory()).thenReturn(true);
        byte[] content2 = new byte[] {5, 6, 7, 8};

        // Call applyToSyncHistory directly.
        IPhysicalPrefix prefix = bs.newPrefix_(sokid, null);
        IPhysicalFile file = bs.newFile_(mkpath(path, sokid.soid()), sokid.kidx());

        try (OutputStream out = prefix.newOutputStream_(false)) {
            ByteStreams.copy(new ByteArrayInputStream(content2), out);
        }
        ContentBlockHash expectedHash = ((BlockPrefix)prefix).hash();
        try (Trans t = tm.begin_()) {
            bs.applyToHistory_(prefix, file, 1L, t);
            t.commit_();
        }
        FileInfo after = bs.getFileInfoNullable_(sokid);
        Assert.assertEquals(before._id, after._id);
        Assert.assertEquals(before._length, after._length);
        Assert.assertEquals(before._chunks, after._chunks);
        Assert.assertEquals(before._mtime, after._mtime);
        Assert.assertEquals(before._ver + 1, after._ver);

        FileInfo history = bsdb.getHistFileInfo_(revIndex(path, -1));
        Assert.assertEquals(after._id, history._id);
        Assert.assertEquals(content2.length, history._length);
        Assert.assertEquals(expectedHash, history._chunks);
        Assert.assertEquals(1L, history._mtime);
        Assert.assertEquals(before._ver, history._ver);
    }
}
