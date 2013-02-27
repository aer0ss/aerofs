/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import static com.aerofs.daemon.core.phy.block.BlockStorageDatabase.*;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.block.BlockStorageDatabase.FileInfo;
import com.aerofs.daemon.core.phy.block.BlockStorageSchema.BlockState;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.Param;
import com.aerofs.lib.Path;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsAutoExportFolder;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
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
    private static final Logger l = Loggers.getLogger(BlockStorage.class);

    private final TC _tc;
    private final TransManager _tm;
    private final CoreScheduler _sched;
    private final InjectableFile.Factory _fileFactory;
    private final CfgAbsAuxRoot _absAuxRoot;

    private InjectableFile _prefixDir;

    private final IBlockStorageBackend _bsb;
    private final BlockStorageDatabase _bsdb;

    private final BlockRevProvider _revProvider = new BlockRevProvider();
    private final CfgAbsAutoExportFolder _exportFolder;
    private final FrequentDefectSender _fds;
    private final LocalACL _lacl;
    private final IMapSIndex2SID _sidx2sid;

    public class FileAlreadyExistsException extends IOException
    {
        private static final long serialVersionUID = 0L;
        public FileAlreadyExistsException(String s) { super(s); }
    }

    @Inject
    public BlockStorage(CfgAbsAuxRoot absAuxRoot, TC tc, TransManager tm, CoreScheduler sched,
            InjectableFile.Factory fileFactory, IBlockStorageBackend bsb, BlockStorageDatabase bsdb,
            CfgAbsAutoExportFolder absExportFolder, FrequentDefectSender fds,
            LocalACL lacl, IMapSIndex2SID sidx2sid)
    {
        _tc = tc;
        _tm = tm;
        _sched = sched;
        _fileFactory = fileFactory;
        _absAuxRoot = absAuxRoot;
        _bsb = bsb;
        _bsdb = bsdb;
        _exportFolder = absExportFolder;
        _fds = fds;
        _lacl = lacl;
        _sidx2sid = sidx2sid;
    }

    @Override
    public void init_() throws IOException
    {
        initPrefixDirAndEnsureItExists();
        initializeBlockStorage();
    }

    private void initializeBlockStorage()
            throws IOException
    {
        _bsb.init_();
        try {
            _bsdb.init_();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void initPrefixDirAndEnsureItExists()
            throws IOException
    {
        final String _prefixDirectoryPath = Objects.firstNonNull(exportRoot(), _absAuxRoot.get());
        _prefixDir = _fileFactory.create(_prefixDirectoryPath, Param.AuxFolder.PREFIX._name);
        _prefixDir.ensureDirExists();
    }

    @Override
    public IPhysicalFile newFile_(SOKID sokid, Path path)
    {
        return new BlockFileAdapter(this, sokid, path, _fileFactory, _fds);
    }

    @Override
    public IPhysicalFolder newFolder_(SOID soid, Path path)
    {
        return new BlockFolderAdapter(this, soid, path, _fileFactory);
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
        final BlockFileAdapter targetAdapter = (BlockFileAdapter)file;
        final BlockFile to = targetAdapter.blockFile;

        Preconditions.checkArgument(from._sockid.sokid().equals(to._sokid),
                "tried to move prefix " + from + " to storage loc for " + to);
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
        _bsdb.updateFileInfo(to._path, new FileInfo(id, -1, length, mtime, from._hash), t);
        if (l.isDebugEnabled()) l.debug("inserted " + from._hash.toHex());

        // We figure out the proper export path here before the transaction commits and capture it
        // in the transaction listener to avoid having to deal with SQLExceptions in the
        // transaction commit hook.  We check if export is enabled to avoid computing the path if
        // it won't be used anyway, since it involves scanning the ACL table.
        // DF: I couldn't come up with a good way to make this pluggable, so I'm leaving it here
        //     for now.
        final InjectableFile exportedFile = exportEnabled() ?
                _fileFactory.create(targetAdapter.exportedAbsPath()) : null;
        // If transaction succeeds:
        //   if export enabled, move prefix file to export folder
        //   otherwise, delete the prefix file
        t.addListener_(new AbstractTransListener() {
            @Override
            public void committed_() {
                targetAdapter.onCommit(from, exportedFile);
            }
        });

        return mtime;
    }

    private boolean exportEnabled()
    {
        return _exportFolder.get() != null;
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
        if (op != PhysicalOp.APPLY) return;

        /**
         * 1. find all indices that correspond to internal names referring to the given sidx
         * 2. remove file info from db and deref blocks accordingly
         * 3. cleanup hist dir info? empty hierarchy isn't great but the space overhead may not be
         * worth the trouble
         * 4. cleanup index? tombstones are annoying but again the space overhead shouldn't be too
         * significant
         * 5. remove dead blocks from the backend (on successful commit)
         */

        IDBIterator<Long> it = _bsdb.getIndicesWithPrefix_(storePrefix(sidx));
        try {
            while (it.next_()) removeFile_(it.get_(), t);
        } finally {
            it.close_();
        }

        // TODO: cleanup hist dir info?
        // TODO: cleanup index?
        // pro: reduce disk usage for deleted stores
        // con: crazy index increase when store goes back and forth...

        // only cleanup backend if the transaction is sucessfully committed
        t.addListener_(new AbstractTransListener() {
            @Override
            public void committed_()
            {
                /**
                 * Removing dead blocks require new DB transactions and releasing core lock around
                 * backend operations so it cannot be done directly
                 */
                _sched.schedule(new AbstractEBSelfHandling() {
                    @Override
                    public void handle_()
                    {
                        try {
                            removeDeadBlocks_();
                            // TODO: DREW ensure removal of export folder for deleted store?
                        } catch (Exception e) {
                            l.warn("Failed to cleanup dead blocks: " + Util.e(e));
                        }
                    }
                }, 0);
            }
        });
    }

    /**
     * Fully remove a file from the DB:
     *   * deref all blocks used by the file
     *   * remove current file info entry
     */
    private void removeFile_(long id, Trans t) throws SQLException
    {
        FileInfo info = _bsdb.getFileInfo_(id);
        for (ContentHash b : BlockUtil.splitBlocks(info._chunks)) _bsdb.decBlockCount_(b, t);
        _bsdb.deleteFileInfo_(id, t);
        // NB: for consistency with LinkedStorage we don't delete history when deleting stores
        //_bsdb.deleteHistFileInfo_(id, t);
    }

    /**
     * Remove dead blocks
     *
     * NB: must be called *outside* of any transaction:
     *   1. we should not remove dead blocks before the transaction that deref them is committed
     *   2. some backends may create a transaction on their own (e.g. CacheBackend)
     *   3. some backends may release the core lock around file/io operation
     */
    private void removeDeadBlocks_() throws SQLException, IOException
    {
        Token tk = _tc.acquire_(Cat.UNLIMITED, "bs-rmd");
        IDBIterator<ContentHash> it = _bsdb.getDeadBlocks_();
        try {
            while (it.next_()) {
                ContentHash h = it.get_();
                Trans t = _tm.begin_();
                try {
                    _bsdb.deleteBlock_(h, t);
                    t.commit_();
                } finally {
                    t.end_();
                }
                _bsb.deleteBlock(h, tk);
            }
        } finally {
            it.close_();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    @Nullable public String exportRoot()
    {
        return _exportFolder.get();
    }

    @Nullable public String storeExportFolder(SIndex sidx)
    {
        // For root stores, the exported path is:
        // <Export root>/read-only-export/<user-email>/<path components>
        // For non-root stores (shared folders), the exported path is:
        // <Export root>/read-only-export/<sid>/<path components>

        // TODO (DF): symlink (or make a "Where are my files.txt" file) for anchors
        // Note that BlockPrefix is given by:
        // <Export root>/p/<prefix file>
        return Util.join(exportRoot(), "read-only-export", storeFullName(sidx));
    }

    public String storeFullName(SIndex sidx)
    {
        SID sid = _sidx2sid.get_(sidx);
        String storeTitle = sid.toStringFormal();
        if (sid.isRoot()) {
            // Loop over ACL entries, find non-self user, make folder with that name
            try {
                for (UserID uid : _lacl.get_(sidx).keySet()) {
                    if (!uid.isTeamServerID()) {
                        assert SID.rootSID(uid).equals(sid);
                        storeTitle = purifyEmail(uid.getString());
                        break;
                    }
                }
            } catch (SQLException e) {
                // LocalACL.get_ shouldn't throw here - it shouldn't be possible to receive events
                // about a store for which we have no ACL.
                SystemUtil.fatal("lacl get " + sidx + " " + sid + " " + e);
            }
        } else {
            // Shared folders look like:
            // shared-folder-c12d379fed050c36bfd3496675a4fe47
            storeTitle = "shared-folder-" + storeTitle;
        }
        return storeTitle;
    }

    private String purifyEmail(String email)
    {
        // Email addresses can have characters that are forbidden in file names.  Here, we strip
        // out the characters listed at
        // http://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx
        // (which conveniently also covers all the characters forbidden on Unix systems)
        // I'm not going to deal with NFC/NFD here because that's over-the-top and the autoexport
        // folder is write-only, so it shouldn't matter.

        // Note: regex replacement is 3x as fast as chaining String.replace()
        // Note: the backslash is double-escaped: once for the compiler, and once for the regex.
        return email.replaceAll("[<>:\"/\\\\|?*]", "_");
    }

    private String storePrefix(SIndex sidx)
    {
        return sidx.toString() + '-';
    }

    private String makeFileName(SOKID sokid)
    {
        return storePrefix(sokid.sidx()) + sokid.oid().toStringFormal() + '-' + sokid.kidx();
    }

    private long getOrCreateFileId_(SOKID sokid, Trans t) throws SQLException
    {
        return _bsdb.getOrCreateFileIndex_(makeFileName(sokid), t);
    }

    FileInfo getFileInfo_(SOKID sokid) throws SQLException
    {
        return _bsdb.getFileInfo_(_bsdb.getFileIndex_(makeFileName(sokid)));
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
        _bsdb.updateFileInfo(file._path, info, t);
    }

    void delete_(BlockFile file, Trans t) throws IOException, SQLException
    {
        FileInfo oldInfo = getFileInfo_(file._sokid);
        if (!FileInfo.exists(oldInfo)) throw new FileNotFoundException(toString());

        FileInfo info = FileInfo.newDeletedFileInfo(oldInfo._id, new Date().getTime());
        _bsdb.updateFileInfo(file._path, info, t);
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
        _bsdb.updateFileInfo(toPath, toInfo, t);

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
                return new RevInputStream(readChunks(info._chunks), info._length, info._mtime);
            } catch (SQLException e) {
                return null;
            }
        }
    }
}
