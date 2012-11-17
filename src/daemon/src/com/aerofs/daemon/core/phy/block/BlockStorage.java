/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import static com.aerofs.daemon.core.phy.block.BlockStorageDatabase.*;
import static com.aerofs.daemon.core.phy.block.BlockUtil.splitBlocks;

import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.block.BlockStorageDatabase.FileInfo;
import com.aerofs.daemon.core.phy.block.BlockStorageSchema.BlockState;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.C.AuxFolder;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

/**
 * IPhysicalStorage interface to a block-based storage backend
 *
 * The logic is split between a backend-agnostic database, which keeps track of block usage and a
 * backend which handles the actual storage, either locally or remotely.
 */
class BlockStorage implements IPhysicalStorage
{
    private static final Logger l = Util.l(BlockStorage.class);

    private final TransManager _tm;
    private final InjectableFile.Factory _fileFactory;
    private final CfgAbsAuxRoot _absAuxRoot;

    private InjectableFile _prefixDir;

    private final IBlockStorageBackend _bsb;
    private final BlockStorageDatabase _bsdb;

    private final BlockRevProvider _revProvider = new BlockRevProvider();

    public class FileAlreadyExistsException extends IOException
    {
        private static final long serialVersionUID = 0L;
        public FileAlreadyExistsException(String s) { super(s); }
    }

    @Inject
    public BlockStorage(CfgAbsAuxRoot absAuxRoot, TransManager tm,
            InjectableFile.Factory fileFactory, IBlockStorageBackend bsb, BlockStorageDatabase bsdb)
    {
        _tm = tm;
        _fileFactory = fileFactory;
        _absAuxRoot = absAuxRoot;
        _bsb = bsb;
        _bsdb = bsdb;

    }

    @Override
    public void init_() throws IOException
    {
        _prefixDir = _fileFactory.create(_absAuxRoot.get(), AuxFolder.PREFIX._name);
        if (!_prefixDir.isDirectory()) _prefixDir.mkdirs();

        _bsb.init_();
        try {
            _bsdb.init_();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public IPhysicalFile newFile_(SOKID sokid, Path path)
    {
        return new BlockFile(this, sokid, path);
    }

    @Override
    public IPhysicalFolder newFolder_(SOID soid, Path path)
    {
        return new BlockFolder(this, soid, path);
    }

    @Override
    public IPhysicalPrefix newPrefix_(SOCKID k)
    {
        // TODO: take component into account if we ever use components other than META/CONTENT
        return new BlockPrefix(this, k, _fileFactory.create(_prefixDir, makeFileName(k.sokid())));
    }

    /**
     * Update database after complete prefix download
     *
     * By the time this method is called:
     *      1. the prefix file should be fully downloaded
     *      2. the contents of the prefix file should have been chunked and stored by the backend
     */
    @Override
    public long apply_(IPhysicalPrefix prefix, IPhysicalFile file, boolean wasPresent, long mtime,
            Trans t) throws IOException, SQLException
    {
        final BlockPrefix from = (BlockPrefix)prefix;
        BlockFile to = (BlockFile)file;

        Preconditions.checkArgument(from._sockid.sokid().equals(to._sokid));
        assert from._hash != null;
        long length = prefix.getLength_();

        long id = getOrCreateFileId_(to._sokid, t);
        FileInfo oldInfo = _bsdb.getFileInfo_(id);
        if (!wasPresent) {
            if (FileInfo.exists(oldInfo)) throw new FileAlreadyExistsException(file.toString());
        } else {
            if (!FileInfo.exists(oldInfo)) throw new FileNotFoundException(file.toString());
        }

        // no need to explicitely specify version, DB auto-increments it
        updateFileInfo(to._path, new FileInfo(id, -1, length, mtime, from._hash), t);
        if (l.isDebugEnabled()) l.debug("inserted " + from._hash.toHex());

        // delete prefix file if transaction succeeds
        t.addListener_(new AbstractTransListener() {
            @Override
            public void committed_() {
                from._f.deleteIgnoreError();
            }
        });

        return mtime;
    }

    @Override
    public IPhysicalRevProvider getRevProvider()
    {
        return _revProvider;
    }

    @Override
    public void createStore_(SIndex sidx, Path path, Trans t) throws IOException, SQLException
    {
        // TODO: fw to backend?
    }

    @Override
    public void deleteStore_(SIndex sidx, Path path, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        // TODO: fw to backend?
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private String makeFileName(SOKID sokid)
    {
        return sokid.sidx().toString() + '-' +
                sokid.oid().toStringFormal() + '-' +
                sokid.kidx();
    }

    private long getOrCreateFileId_(SOKID sokid, Trans t) throws SQLException
    {
        return _bsdb.getOrCreateFileIndex_(makeFileName(sokid), t);
    }

    FileInfo getFileInfo_(SOKID sokid) throws SQLException
    {
        return _bsdb.getFileInfo_(_bsdb.getFileIndex_(makeFileName(sokid)));
    }

    /**
     * Update file info after successful file update
     *
     * If the current file info is valid, back it up in the history, creating hierarchy as neeeded
     * Increment ref count for chunks used by the new file info
     */
    private void updateFileInfo(Path path, FileInfo info, Trans t) throws SQLException
    {
        // first, back up any current file info
        FileInfo oldInfo = _bsdb.getFileInfo_(info._id);
        if (FileInfo.exists(oldInfo)) {
            long dirId = _bsdb.getOrCreateHistDirByPath_(path.removeLast(), t);
            _bsdb.saveOldFileInfo_(dirId, path.last(), oldInfo, t);
        }

        // update file info
        _bsdb.writeNewFileInfo_(info, t);
        for (ContentHash chunk : splitBlocks(info._chunks)) {
            _bsdb.incBlockCount_(chunk, t);
        }
    }

    public InputStream readChunks(ContentHash hash) throws IOException
    {
        return new BlockInputStream(_bsb, hash);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * "Prepare" a fully downloaded prefix before moving it into persistent storage
     *
     * We use this method to perform the actual movement as the backend may use remote storage.
     */
    ContentHash prepare_(BlockPrefix prefix, Token tk) throws IOException
    {
        ContentHash h;
        try {
            l.debug(">>> preparing prefix for " + prefix._sockid);
            h = new Chunker(prefix._f, tk, _bsb).splitAndStore();
            l.debug("<<< done preparing prefix for " + prefix._sockid);
        } catch (Exception e) {
            throw new IOException(e);
        }
        return h;
    }

    /**
     * Chunker implementation that release the core lock around I/O operation and re-acquires
     * it temporarily around DB operations
     */
    private class Chunker extends AbstractChunker
    {
        private TCB _tcb;
        private final Token _tk;

        Chunker(InjectableFile f, Token tk, IBlockStorageBackend bsb)
        {
            super(Files.newInputStreamSupplier(f.getImplementation()), f.getLengthOrZeroIfNotFile(),
                    bsb);
            _tk = tk;
        }

        public ContentHash splitAndStore() throws IOException, SQLException, ExAborted
        {
            try {
                pseudoPause_();
                return splitAndStore_();
            } finally {
                pseudoResumed_();
            }
        }

        @Override
        protected void prePutBlock_(Block block) throws SQLException
        {
            try {
                try {
                    pseudoResumed_();
                    prePutBlock(block);
                } finally {
                    pseudoPause_();
                }
            } catch (ExAborted e) {
                throw new SQLException(e);
            }
        }

        @Override
        protected void postPutBlock_(Block block) throws SQLException
        {
            try {
                try {
                    pseudoResumed_();
                    postPutBlock(block);
                } finally {
                    pseudoPause_();
                }
            } catch (ExAborted e) {
                throw new SQLException(e);
            }
        }

        private void prePutBlock(Block block) throws SQLException
        {
            Trans t = _tm.begin_();
            try {
                BlockState bs = _bsdb.getBlockState_(block._hash);
                if (bs != BlockState.STORED && bs != BlockState.REFERENCED) {
                    _bsdb.prePutBlock_(block._hash, block._length, t);
                }
                t.commit_();
            } finally {
                t.end_();
            }
        }

        private void postPutBlock(Block block) throws SQLException
        {
            Trans t = _tm.begin_();
            try {
                _bsdb.postPutBlock_(block._hash, t);
                t.commit_();
            } finally {
                t.end_();
            }
        }

        private void pseudoPause_() throws ExAborted
        {
            assert _tcb == null;
            _tcb = _tk.pseudoPause_("still chunking");
        }

        private void pseudoResumed_() throws ExAborted
        {
            TCB tcb = _tcb;
            _tcb = null;
            tcb.pseudoResumed_();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    void create_(BlockFile file, Trans t) throws IOException, SQLException
    {
        long id = getOrCreateFileId_(file._sokid, t);
        if (FileInfo.exists(_bsdb.getFileInfo_(id))) {
            throw new FileAlreadyExistsException(toString());
        }

        FileInfo info = new FileInfo(id, 0, 0, new Date().getTime(),
                EMPTY_FILE_CHUNKS);
        updateFileInfo(file._path, info, t);
    }

    void delete_(BlockFile file, Trans t) throws IOException, SQLException
    {
        FileInfo oldInfo = getFileInfo_(file._sokid);
        if (!FileInfo.exists(oldInfo)) throw new FileNotFoundException(toString());

        FileInfo info = FileInfo.newDeletedFileInfo(oldInfo._id, new Date().getTime());
        updateFileInfo(file._path, info, t);
    }

    void move_(BlockFile from, BlockFile to, Trans t) throws IOException, SQLException
    {
        SOKID fromObjId = from._sokid;
        Path fromPath = from._path;

        SOKID toObjId = to._sokid;
        Path toPath = to._path;

        if (l.isDebugEnabled()) {
            if (!fromObjId.equals(toObjId)) l.debug(fromObjId + " -> " + toObjId);
            if (!fromPath.equals(toPath)) l.debug(fromPath + " -> " + toPath);
        }

        FileInfo fromInfo = getFileInfo_(fromObjId);
        if (!FileInfo.exists(fromInfo)) throw new FileNotFoundException(toString());

        long toId;
        if (fromObjId.equals(toObjId)) {
            toId = fromInfo._id;
        } else {
            toId = getOrCreateFileId_(toObjId, t);
            if (FileInfo.exists(_bsdb.getFileInfo_(toId))) {
                throw new FileAlreadyExistsException(to.toString());
            }
        }

        FileInfo toInfo = new FileInfo(toId, -1, fromInfo._length, new Date().getTime(),
                fromInfo._chunks);
        updateFileInfo(toPath, toInfo, t);

        if (toId != fromInfo._id) {
            delete_(from, t);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class BlockRevProvider implements IPhysicalRevProvider
    {
        @Override
        public Collection<Child> listRevChildren_(Path path) throws Exception
        {
            try {
                long dirId = _bsdb.getHistDirByPath_(path);
                if (dirId == DIR_ID_NOT_FOUND) return Collections.emptyList();
                return _bsdb.getHistDirChildren_(dirId);
            } catch (SQLException e) {
                return Collections.emptyList();
            }
        }

        @Override
        public Collection<Revision> listRevHistory_(Path path) throws Exception
        {
            try {
                long dirId = _bsdb.getHistDirByPath_(path.removeLast());
                if (dirId == DIR_ID_NOT_FOUND) return Collections.emptyList();
                return _bsdb.getHistFileRevisions_(dirId, path.last());
            } catch (SQLException e) {
                return Collections.emptyList();
            }
        }

        @Override
        public RevInputStream getRevInputStream_(Path path, byte[] index) throws Exception
        {
            try {
                FileInfo info = _bsdb.getHistFileInfo_(index);
                if (info == null) throw new InvalidRevisionIndexException();
                return new RevInputStream(readChunks(info._chunks), info._length);
            } catch (SQLException e) {
                return null;
            }
        }
    }
}
