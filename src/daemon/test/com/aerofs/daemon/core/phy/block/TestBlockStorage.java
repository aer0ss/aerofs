/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Child;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Revision;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.block.BlockStorage.FileAlreadyExistsException;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend.EncoderWrapping;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Param;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestBlockStorage extends AbstractBlockTest
{
    @Mock Trans t;
    @Mock Token tk;
    @Mock TCB tcb;
    @Mock TransManager tm;
    @Mock CfgAbsAuxRoot auxRoot;

    // use in-memory DB
    InjectableFile.Factory fileFactory = new InjectableFile.Factory();
    InMemorySQLiteDBCW idbcw = new InMemorySQLiteDBCW();
    BlockStorageDatabase bsdb = new BlockStorageDatabase(idbcw.getCoreDBCW().get());

    @Mock IBlockStorageBackend bsb;

    BlockStorage bs;

    @Before
    public void setUp() throws Exception
    {
        idbcw.init_();
        new BlockStorageSchema(idbcw.getCoreDBCW())
                .create_(idbcw.getConnection().createStatement());

        when(tm.begin_()).thenReturn(t);
        when(tk.pseudoPause_(anyString())).thenReturn(tcb);
        when(auxRoot.get()).thenReturn(testTempDirFactory.getTestTempDir().getAbsolutePath());

        // no encoding is performed by the mock backend
        when(bsb.wrapForEncoding(any(OutputStream.class))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Object[] args = invocation.getArguments();
                return new EncoderWrapping((OutputStream)args[0], null);
            }
        });

        // shame @InjectMocks does not deal with a mix of Mock and real objects...
        bs = new BlockStorage(auxRoot, tm, fileFactory, bsb, bsdb);
        bs.init_();
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

    private void create(String path, SOKID sokid) throws Exception
    {
        IPhysicalFile file = bs.newFile_(sokid, Path.fromString(path));
        file.create_(PhysicalOp.APPLY, t);
    }

    private void delete(String path, SOKID sokid) throws Exception
    {
        IPhysicalFile file = bs.newFile_(sokid, Path.fromString(path));
        file.delete_(PhysicalOp.APPLY, t);
    }

    private void move(String pathFrom, SOKID sokidFrom, String pathTo, SOKID sokidTo)
            throws Exception
    {
        IPhysicalFile from = bs.newFile_(sokidFrom, Path.fromString(pathFrom));
        IPhysicalFile to = bs.newFile_(sokidTo, Path.fromString(pathTo));
        from.move_(to, PhysicalOp.APPLY, t);
    }

    private boolean exists(String path, SOKID sokid) throws IOException
    {
        IPhysicalFile file = bs.newFile_(sokid, Path.fromString(path));
        try {
            file.newInputStream_().close();
        } catch (FileNotFoundException e) {
            return false;
        }
        return true;
    }

    private void store(String path, SOKID sokid, byte[] content, boolean wasPresent, long mtime)
            throws Exception
    {
        SOCKID sockid = new SOCKID(sokid, CID.CONTENT);
        IPhysicalPrefix prefix = bs.newPrefix_(sockid);
        IPhysicalFile file = bs.newFile_(sokid, Path.fromString(path));

        OutputStream out = prefix.newOutputStream_(false);
        out.write(content);
        out.close();

        prefix.prepare_(tk);
        bs.apply_(prefix, file, wasPresent, mtime, t);
    }

    private byte[] fetch(String path, SOKID sokid) throws Exception
    {
        IPhysicalFile file = bs.newFile_(sokid, Path.fromString(path));
        InputStream in = file.newInputStream_();
        return ByteStreams.toByteArray(in);
    }


    private boolean revChildrenEquals(String path, Child... children) throws Exception
    {
        Collection<Child> result = bs.getRevProvider().listRevChildren_(Path.fromString(path));
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
    }

    private boolean revHistoryEquals(String path, RevisionMatcher... revisions) throws Exception
    {
        Collection<Revision> result = bs.getRevProvider().listRevHistory_(Path.fromString(path));
        int i = 0;
        for (Revision r : result) {
            if (!revisions[i].matches(r)) return false;
            ++i;
        }
        return true;
    }

    private byte[] fetchRev(String path, byte[] index) throws Exception
    {
        return ByteStreams.toByteArray(
                bs.getRevProvider().getRevInputStream_(Path.fromString(path), index)._is);
    }

    private byte[] fetchRev(String path, int idx) throws Exception
    {
        Object[] r = bs.getRevProvider().listRevHistory_(Path.fromString(path)).toArray();
        return fetchRev(path, ((Revision)r[idx < 0 ? r.length + idx : idx])._index);
    }

    @Test
    public void shouldCreateFileAndRetrieveEmptyContent() throws Exception
    {
        SOKID sokid = newSOKID();

        create("foo/bar", sokid);
        byte[] result = fetch("foo/bar", sokid);
        verify(bsb, never())
                .putBlock(any(ContentHash.class), any(InputStream.class), anyLong(), anyObject());
        verify(bsb, never()).getBlock(any(ContentHash.class));
        Assert.assertArrayEquals(new byte[0], result);
    }

    @Test
    public void shouldThrowWhenTryingToCreateExistingFile() throws Exception
    {
        SOKID sokid = newSOKID();

        create("foo/bar", sokid);
        boolean ok = false;
        try {
            create("foo/bar", sokid);
        } catch (FileAlreadyExistsException e) {
            ok = true;
        }
        Assert.assertTrue(ok);
        verify(bsb, never())
                .putBlock(any(ContentHash.class), any(InputStream.class), anyLong(), anyObject());
        verify(bsb, never()).getBlock(any(ContentHash.class));
    }

    @Test
    public void shouldWriteNewFile() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

        store("foo/bar", sokid, content, false, 0L);

        verify(bsb).putBlock(forKey(content), any(InputStream.class), eq((long)content.length),
                isNull());
        verify(bsb, never()).getBlock(any(ContentHash.class));
    }

    @Test
    public void shouldWriteExistingFile() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

        create("foo/bar", sokid);
        store("foo/bar", sokid, content, true, 0L);

        verify(bsb).putBlock(forKey(content), any(InputStream.class), eq((long)content.length),
                isNull());
        verify(bsb, never()).getBlock(any(ContentHash.class));
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
        verify(bsb, never()).getBlock(any(ContentHash.class));
    }

    @Test
    public void shouldThrowWhenWritingToUnexpectedlyExistingFile() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

        create("foo/bar", sokid);
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
        verify(bsb, never()).getBlock(any(ContentHash.class));
    }

    @Test
    public void shouldFetchContents() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

        store("foo/bar", sokid, content, false, 0L);
        verify(bsb).putBlock(forKey(content), any(InputStream.class), eq((long)content.length),
                isNull());

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
                .putBlock(any(ContentHash.class), any(InputStream.class), anyLong(), anyObject());
        verify(bsb, never()).getBlock(any(ContentHash.class));
    }

    @Test
    public void shouldDeleteFile() throws Exception
    {
        SOKID sokid = newSOKID();

        create("foo/bar", sokid);
        delete("foo/bar", sokid);

        Assert.assertFalse(exists("foo/bar", sokid));

        verify(bsb, never())
                .putBlock(any(ContentHash.class), any(InputStream.class), anyLong(), anyObject());
        verify(bsb, never()).getBlock(any(ContentHash.class));
    }

    @Test
    public void shouldThrowWhenTryingToDeleteNonExistingFile() throws Exception
    {
        SOKID sokid = newSOKID();

        boolean ok = false;
        try {
            delete("foo/bar", sokid);
        } catch (FileNotFoundException e) {
            ok = true;
        }
        Assert.assertTrue(ok);
        verify(bsb, never())
                .putBlock(any(ContentHash.class), any(InputStream.class), anyLong(), anyObject());
        verify(bsb, never()).getBlock(any(ContentHash.class));
    }

    @Test
    public void shouldMoveFile() throws Exception
    {
        SOKID from = newSOKID();
        SOKID to = newSOKID();

        create("foo/bar", from);
        move("foo/bar", from, "foo/baz/bla", to);

        verify(bsb, never())
                .putBlock(any(ContentHash.class), any(InputStream.class), anyLong(), anyObject());
        verify(bsb, never()).getBlock(any(ContentHash.class));
    }

    @Test
    public void shouldThrowWhenTryingToMoveNonExisitingFile() throws Exception
    {
        SOKID from = newSOKID();
        SOKID to = newSOKID();

        boolean ok = false;
        try {
            move("foo/bar", from, "foo/baz/bla", to);
        } catch (FileNotFoundException e) {
            ok = true;
        }
        Assert.assertTrue(ok);

        verify(bsb, never())
                .putBlock(any(ContentHash.class), any(InputStream.class), anyLong(), anyObject());
        verify(bsb, never()).getBlock(any(ContentHash.class));
    }

    @Test
    public void shouldThrowWhenTryingToMoveOverExistingFile() throws Exception
    {
        SOKID from = newSOKID();
        SOKID to = newSOKID();

        create("foo/bar", from);
        create("foo/baz/bla", to);
        boolean ok = false;
        try {
            move("foo/bar", from, "foo/baz/bla", to);
        } catch (FileAlreadyExistsException e) {
            ok = true;
        }
        Assert.assertTrue(ok);

        verify(bsb, never())
                .putBlock(any(ContentHash.class), any(InputStream.class), anyLong(), anyObject());
        verify(bsb, never()).getBlock(any(ContentHash.class));
    }

    @Test
    public void shouldIgnoreFolderFolderCreationConflict() throws Exception
    {
        bs.newFolder_(newSOID(), Path.fromString("foo/bar")).create_(PhysicalOp.APPLY, t);
        bs.newFolder_(newSOID(), Path.fromString("foo/bar")).create_(PhysicalOp.APPLY, t);
    }

    @Test
    public void shouldIgnoreFolderFileCreationConflict() throws Exception
    {
        bs.newFolder_(newSOID(), Path.fromString("foo/bar")).create_(PhysicalOp.APPLY, t);
        create("foo/bar", newSOKID());
    }

    @Test
    public void shouldIgnoreFileFolderCreationConflict() throws Exception
    {
        create("foo/bar", newSOKID());
        bs.newFolder_(newSOID(), Path.fromString("foo/bar")).create_(PhysicalOp.APPLY, t);
    }

    @Test
    public void shouldIgnoreFolderDeletion() throws Exception
    {
        bs.newFolder_(newSOID(), Path.fromString("foo/bar")).delete_(PhysicalOp.APPLY, t);
    }

    @Test
    public void shouldIgnoreFolderMovement() throws Exception
    {
        IPhysicalFolder to = bs.newFolder_(newSOID(), Path.fromString("hellow/world"));
        bs.newFolder_(newSOID(), Path.fromString("foo/bar")).move_(to, PhysicalOp.APPLY, t);
    }

    @Test
    public void shouldFetchContentDespitePathMismatch() throws Exception
    {
        SOKID sokid = newSOKID();
        byte[] content = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

        store("foo/bar", sokid, content, false, 0L);
        verify(bsb).putBlock(forKey(content), any(InputStream.class), eq((long)content.length),
                isNull());

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
        create("foo/bar", sokid);
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
        create("foo/bar", sokid);
        delete("foo/bar", sokid);

        Assert.assertTrue(revChildrenEquals("", new Child("foo", true)));
        Assert.assertTrue(revChildrenEquals("foo", new Child("bar", false)));
        Assert.assertTrue(revHistoryEquals("foo/bar", RevisionMatcher.any()));
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
        IPhysicalPrefix prefix = bs.newPrefix_(new SOCKID(sokid, CID.CONTENT));
        OutputStream out = prefix.newOutputStream_(false);

        ByteStreams.copy(new DevZero(4 * Param.FILE_BLOCK_SIZE + 1), out);
        prefix.prepare_(tk);

        verify(bsb, times(5))
                .putBlock(any(ContentHash.class), any(InputStream.class), anyLong(), isNull());
    }
}
