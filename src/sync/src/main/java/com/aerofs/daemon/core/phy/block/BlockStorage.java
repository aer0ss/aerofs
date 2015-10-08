/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import static com.aerofs.daemon.core.phy.block.BlockStorageDatabase.*;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.*;
import com.aerofs.daemon.lib.CleanupScheduler;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Child;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Revision;
import com.aerofs.daemon.core.phy.block.BlockStorageSchema.BlockState;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend.TokenWrapper;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.*;
import com.aerofs.lib.cfg.CfgAbsDefaultAuxRoot;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.sql.SQLException;
import java.util.*;

/**
 * IPhysicalStorage interface to a block-based storage backend
 *
 * The logic is split between a backend-agnostic database, which keeps track of block usage and a
 * backend which handles the actual storage, either locally or remotely.
 */
class BlockStorage implements IPhysicalStorage, CleanupScheduler.CleanupHandler
{
    static final Logger l = Loggers.getLogger(BlockStorage.class);

    private TokenManager _tokenManager;
    private TransManager _tm;
    private CoreScheduler _sched;
    private InjectableFile.Factory _fileFactory;
    private CfgStoragePolicy _storagePolicy;

    private InjectableFile _prefixDir;
    private InjectableFile _uploadDir;

    private IBlockStorageBackend _bsb;
    private BlockStorageDatabase _bsdb;

    private CleanupScheduler _uploadScheduler;

    private final BlockRevProvider _revProvider = new BlockRevProvider();
    private Set<IBlockStorageInitable> _initables;

    private final ProgressIndicators _pi = ProgressIndicators.get();

    public class FileAlreadyExistsException extends IOException
    {
        private static final long serialVersionUID = 0L;
        public FileAlreadyExistsException(String s) { super(s); }
    }

    @Inject
    public void inject_(CfgAbsDefaultAuxRoot absDefaultAuxRoot, CfgStoragePolicy storagePolicy,
            TokenManager tokenManager, TransManager tm, CoreScheduler sched,
            InjectableFile.Factory fileFactory, IBlockStorageBackend bsb, BlockStorageDatabase bsdb,
            Set<IBlockStorageInitable> initables)
    {
        _tokenManager = tokenManager;
        _tm = tm;
        _sched = sched;
        _fileFactory = fileFactory;
        _storagePolicy = storagePolicy;
        _bsb = bsb;
        _bsdb = bsdb;
        _initables = initables;

        final String auxPath = absDefaultAuxRoot.get();
        _prefixDir = _fileFactory.create(auxPath, LibParam.AuxFolder.PREFIX._name);
        _uploadDir = _fileFactory.create(auxPath, "up");

        _uploadScheduler = new CleanupScheduler(this, _sched);
    }

    @Override
    public void init_() throws IOException
    {
        ensurePrefixDirExists();
        initializeBlockStorage();

        for (IBlockStorageInitable i : _initables) i.init_(_bsb);
    }

    @Override
    public void start_()
    {
        _uploadScheduler.schedule_();
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

    private void ensurePrefixDirExists()
            throws IOException
    {
        _prefixDir.ensureDirExists();
        _uploadDir.ensureDirExists();
    }

    @Override
    public IPhysicalFile newFile_(ResolvedPath path, KIndex kidx)
    {
        return new BlockFile(this, new SOKID(path.soid(), kidx), path);
    }

    @Override
    public IPhysicalFolder newFolder_(ResolvedPath path)
    {
        return new BlockFolder(path.soid(), path);
    }

    @Override
    public IPhysicalPrefix newPrefix_(SOKID k, @Nullable String scope)
    {
        String fileName = prefixFilePath(k) + (scope != null ? "-" + scope : "");
        return new BlockPrefix(k, _prefixDir.newChild(fileName));
    }

    private String prefixFilePath(SOID soid)
    {
        return Util.join(soid.sidx().toString(), soid.oid().toStringFormal());
    }

    private String prefixFilePath(SOKID sokid)
    {
        return Util.join(prefixFilePath(sokid.soid()), sokid.kidx().toString());
    }

    /**
     * Update database after complete prefix download
     */
    @Override
    public long apply_(IPhysicalPrefix prefix, IPhysicalFile file, boolean wasPresent, long mtime,
            Trans t) throws IOException, SQLException
    {
        final BlockPrefix from = (BlockPrefix)prefix;
        final BlockFile to = (BlockFile)file;

        long id = getOrCreateFileId_(to._sokid, t);
        checkSanity(from, to, id, wasPresent);
        long length = from.length();
        ContentBlockHash hash = from.hash();

        consumePrefixBlocksToUploadDir(from, t);
        // no need to explicitly specify version, DB auto-increments it
        updateFileInfo_(to._path, new FileInfo(id, -1, length, mtime, hash), t);
        l.debug("inserted {}", hash);
        return mtime;
    }

    // NB: MUST happen before updateFileInfo (DB update dependency)
    private void consumePrefixBlocksToUploadDir(BlockPrefix from, Trans t)
            throws SQLException, IOException
    {
        from.consumeChunks_((key, f) -> {
            if (!prePutBlock(key, f.length(), t)) {
                return;
            }
            InjectableFile dst = _uploadDir.newChild(key.toHex());
            if (!dst.exists()) TransUtil.moveWithRollback_(f, dst, t);
        });

        // If transaction succeeds, delete the prefix file
        t.addListener_(new AbstractTransListener() {
            @Override
            public void committed_() {
                from.cleanup_();
                _uploadScheduler.schedule_();
            }
        });
    }

    private void checkSanity(BlockPrefix from, BlockFile to, long id, boolean wasPresent)
            throws SQLException, FileAlreadyExistsException, FileNotFoundException
    {
        checkArgument(from._sokid.equals(to._sokid),
                "tried to move prefix %s to storage loc for %s", from, to);

        FileInfo oldInfo = _bsdb.getFileInfo_(id);
        if (!wasPresent) {
            if (FileInfo.exists(oldInfo)) throw new FileAlreadyExistsException(to.toString());
        } else {
            if (!FileInfo.exists(oldInfo)) throw new FileNotFoundException(to.toString());
        }
    }

    boolean prePutBlock(ContentBlockHash hash, long length, Trans t)
            throws SQLException
    {
        BlockState bs = _bsdb.getBlockState_(hash);
        if (bs == BlockState.STORED || bs == BlockState.REFERENCED) {
            long storedLen = _bsdb.getBlockLength_(hash);
            if (storedLen != length) {
                throw new SQLException("hash collision " + hash.toHex()
                        + " " + storedLen + " " + length);
            }
            return false;
        }
        _bsdb.prePutBlock_(hash, length, t);
        return true;
    }

    /**
     * Update file information in the block storage database.
     *
     * If history storage is enabled, and the FileInfo describes an existing
     * resource, the existing resource will be moved to history.
     *
     * The new file info is written to the internal db, and ref count is incremented
     * for all chunks used by the new file.
     *
     * If history storage is disabled, this will decrement refs for the chunks used
     * in the outgoing version.
     */
    private void updateFileInfo_(Path newPath, FileInfo newFile, Trans t) throws SQLException
    {
        FileInfo oldFile = _bsdb.getFileInfo_(newFile._id);

        if (FileInfo.exists(oldFile)) {
            if (_tlUseHistory.get(t)) {
                _bsdb.preserveFileInfo(newPath, oldFile, t);
            } else {
                derefBlocks_(oldFile._chunks, t);
                scheduleBlockCleaner_(t);
            }
        } else if (oldFile == null) {
            _bsdb.insertEmptyFileInfo(newFile._id, t);
        }

        _bsdb.updateFileInfo_(newFile, t);
    }

    @Override
    public IPhysicalRevProvider getRevProvider()
    {
        return _revProvider;
    }

    @Override
    public void createStore_(SIndex sidx, SID sid, String name, Trans t)
            throws IOException, SQLException
    {
        // TODO: fw to backend?
    }

    @Override
    public void deleteStore_(SID physicalRoot, SIndex sidx, SID sid, Trans t)
            throws IOException, SQLException
    {
        /**
         * 1. find all indices that correspond to internal names referring to the given sidx
         * 2. remove file info from db and deref blocks accordingly
         * 3. cleanup hist dir info? empty hierarchy isn't great but the space overhead may not be
         * worth the trouble
         * 4. cleanup index? tombstones are annoying but again the space overhead shouldn't be too
         * significant
         * 5. remove dead blocks from the backend (on successful commit)
         */

        String prefix = storePrefix(sidx);

        IDBIterator<Long> it = _bsdb.getIndicesWithPrefix_(prefix);
        try {
            while (it.next_()) removeFile_(it.get_(), t);
        } finally {
            it.close_();
        }

        scheduleBlockCleaner_(t);

        _prefixDir.newChild(sidx.toString()).deleteOrThrowIfExistRecursively();
    }

    @Override
    public void discardRevForTrans_(Trans t)
    {
        _tlUseHistory.set(t, false);
    }

    private final TransLocal<Boolean> _tlUseHistory = new TransLocal<Boolean>() {
        @Override
        protected Boolean initialValue(Trans t)
        {
            return _storagePolicy.useHistory();
        }
    };

    @Override
    public boolean isDiscardingRevForTrans_(Trans t)
    {
        return !_tlUseHistory.get(t);
    }

    @Override
    public ImmutableCollection<NonRepresentableObject> listNonRepresentableObjects_() throws IOException, SQLException
    {
        l.info("BlockStorage doesn't have NROs");
        return ImmutableList.<NonRepresentableObject>builder().build();
    }

    @Override
    public @Nullable String deleteFolderRecursively_(ResolvedPath path, PhysicalOp op, Trans t)
            throws SQLException, IOException
    {
        return null;
    }

    @Override
    public boolean shouldScrub_(SID sid) { return true; }

    @Override
    public void scrub_(SOID soid, @Nonnull Path historyPath, @Nullable String rev, Trans t)
            throws SQLException, IOException
    {
        String prefix = objectPrefix(soid);
        IDBIterator<Long> it = _bsdb.getIndicesWithPrefix_(prefix);
        try {
            while (it.next_()) {
                long id = it.get_();
                if (historyPath.isEmpty()) {
                    removeFile_(id, t);
                } else {
                    delete_(id, historyPath, t);
                }
            }
        } finally {
            it.close_();
        }
        _prefixDir.newChild(prefixFilePath(soid)).deleteOrThrowIfExistRecursively();
    }

    @Override
    public void applyToHistory_(IPhysicalPrefix prefix, IPhysicalFile file, long mtime, Trans t)
            throws IOException, SQLException
    {
        if (!_tlUseHistory.get(t)) return;

        final BlockPrefix from = (BlockPrefix) prefix;
        final BlockFile to = (BlockFile) file;
        long id = getOrCreateFileId_(to._sokid, t);

        checkSanity(from, to, id, true);
        long length = from.length();
        ContentBlockHash hash = from.hash();
        consumePrefixBlocksToUploadDir(from, t);
        FileInfo currFile = _bsdb.getFileInfo_(id);
        // Since we are applying file directly to sync history, current version in File Info
        // table is incremented by 1 and File history table gets File Info with version of current
        // file.
        _bsdb.updateFileInfo_(new FileInfo(currFile._id, currFile._ver + 1,
                currFile._length, currFile._mtime, currFile._chunks), t);
        _bsdb.preserveFileInfo(to._path, new FileInfo(id, currFile._ver, length, mtime,
                hash), t);
    }

    @Override
    public boolean restore_(SOID soid, OID deletedRoot, List<String> deletedPath, IPhysicalFile pf,
                            Trans t) throws SQLException, IOException {
        FileInfo info = getFileInfoNullable_(new SOKID(soid, KIndex.MASTER));
        if (info == null) return false;

        if (info._length != DELETED_FILE_LEN || info._mtime != DELETED_FILE_DATE) {
            l.warn("restore non-deleted file {}", pf.sokid(), info._length, info._mtime);
        } else {
            info = _bsdb.getHistFileInfo_(info._id, info._ver - 1);
            if (info == null) return false;
        }

        long id = getOrCreateFileId_(pf.sokid(), t);
        _bsdb.insertEmptyFileInfo(id, t);
        _bsdb.updateFileInfo_(new FileInfo(id, -1, info._length, info._mtime, info._chunks), t);
        return true;
    }

    private final TransLocal<Boolean> _tlScheduleCleaner = new TransLocal<Boolean>()  {
        @Override
        protected Boolean initialValue(Trans t)
        {
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_()
                {
                    scheduleBlockCleaner();
                }
            });
            return false;
        }
    };


    /**
     * TODO: cleaning blocks is currently ad-hoc - improve this model.
     *
     * We get here from deleteStore as well as from apply/delete with version-history
     * disabled.
     *
     * Each invocation calls removeDeadBlocks, which starts by iterating all the dead blocks.
     *
     * Two possible improvements:
     *  - when deref'ing blocks, build a list of cleanable blocks in transaction-local memory
     *    and pass it to the cleaner. Avoids iteration, but may be a large chunk of memory
     *    for updates to big files.
     *  - treat the block cleaner as a garbage-collector. Any work that deref's blocks just
     *    pings the block cleaner, and when he has built enough of a backlog he runs.
     *    As long as the dead-block iterator is cheap enough, this model is more tunable.
     *
     */
    private void scheduleBlockCleaner_(Trans t)
    {
        // TODO: ensure a single listener is added per trans
        if (!_tlScheduleCleaner.get(t)) {
            _tlScheduleCleaner.set(t, true);
        }
    }

    private void scheduleBlockCleaner()
    {
        // TODO: cleanup hist dir info?
        // TODO: cleanup index?
        // pro: reduce disk usage for deleted stores
        // con: crazy index increase when store goes back and forth...

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
                } catch (Exception e) {
                    l.warn("Failed to cleanup dead blocks", e);
                }
            }
        }, 0);
    }


    ///////////////////////////////////// CleanupHandler ///////////////////////////////////////////

    @Override
    public String name() {
        return "block-upload";
    }

    @Override
    public boolean process_() throws Exception {
        // take snapshot of upload folder w/ core lock held to avoid races
        String[] blocks = _uploadDir.list();

        if (blocks == null || blocks.length == 0) return false;

        try (Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "block-upload")) {
            TCB tcb = tk.pseudoPause_("block-upload");
            try {
                for (String c : blocks) {
                    ContentBlockHash k;
                    try {
                        k = new ContentBlockHash(BaseUtil.hexDecode(c));
                        checkState(BlockUtil.isOneBlock(k));
                    } catch (Exception e) {
                        l.warn("invalid block in upload dir: {}", c);
                        continue;
                    }
                    l.info("uploading block {}", c);
                    InjectableFile f = _uploadDir.newChild(c);
                    try (InputStream in = f.newInputStream()) {
                        _bsb.putBlock(k, in, f.length());
                    }
                    // FIXME: delay removal until last reader is closed
                    _uploadDir.newChild(c).deleteIgnoreError();
                }
            } finally {
                tcb.pseudoResumed_();
            }
        }

        blocks = _uploadDir.list();
        return blocks != null && blocks.length > 0;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static String storePrefix(SIndex sidx)
    {
        return sidx.toString() + '-';
    }

    private static String objectPrefix(SOID soid)
    {
        return storePrefix(soid.sidx()) + soid.oid().toStringFormal();
    }

    private static String makeFileName(SOKID sokid)
    {
        return objectPrefix(sokid.soid()) + '-' + sokid.kidx();
    }

    private long getOrCreateFileId_(SOKID sokid, Trans t) throws SQLException
    {
        return _bsdb.getOrCreateFileIndex_(makeFileName(sokid), t);
    }

    @Nullable FileInfo getFileInfoNullable_(SOKID sokid) throws SQLException
    {
        return _bsdb.getFileInfo_(_bsdb.getFileIndex_(makeFileName(sokid)));
    }

    private final IBlockStorageBackend _overlay = new IBlockStorageBackend() {
        @Override
        public void init_() {}

        @Override
        public InputStream getBlock(ContentBlockHash key) throws IOException {
            // FIXME: ensure that the file does not get removed while being read
            InjectableFile f = _uploadDir.newChild(key.toHex());
            return f.exists() ? f.newInputStream() : _bsb.getBlock(key);
        }

        @Override
        public void putBlock(ContentBlockHash key, InputStream input, long l) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteBlock(ContentBlockHash key, TokenWrapper tk) {
            throw new UnsupportedOperationException();
        }
    };

    public InputStream readChunks(ContentBlockHash hash, long length) throws IOException
    {
        // TODO: in-memory refcount overlay to avoid overzealous block cleanup?
        // TODO: check that all non-last chunks are full-size?
        return new BlockInputStream(_overlay, hash, length);
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
        updateFileInfo_(file._path, info, t);
    }

    void delete_(BlockFile file, Trans t) throws IOException, SQLException
    {
        delete_(_bsdb.getFileIndex_(makeFileName(file._sokid)), file._path, t);
    }

    void delete_(long id, Path historyPath, Trans t) throws SQLException
    {
        FileInfo oldInfo = _bsdb.getFileInfo_(id);
        if (!FileInfo.exists(oldInfo)) {
            // It's possible that we never got content for that file, which would explain
            // why we can't find it.
            // It's also possible, though less likely, that due to a bug in aliasing we forgot
            // to move content from alias to target and end up with no content
            // Unfortunately we cannot easily distinguish between these two cases. Anyway, in both
            // case, the correct behavior is simply to return with no error. In the worst case we
            // may end up with some blocks of content associated with a "ghost" object (i.e. the
            // alias OID) but that's unlikely to be a problem.
            return;
        }

        FileInfo info = FileInfo.newDeletedFileInfo(id, new Date().getTime());
        updateFileInfo_(historyPath, info, t);
    }

    void move_(BlockFile from, ResolvedPath toPath, KIndex kidx, Trans t)
            throws IOException, SQLException
    {
        SOKID fromObjId = from._sokid;
        Path fromPath = from._path;

        SOKID toObjId = new SOKID(toPath.soid(), kidx);

        if (!fromObjId.equals(toObjId)) l.debug("{} -> {}",fromObjId, toObjId);
        if (!fromPath.equals(toPath)) l.debug("{} -> {}",fromPath, toPath);

        FileInfo fromInfo = getFileInfoNullable_(fromObjId);
        if (!FileInfo.exists(fromInfo)) throw new FileNotFoundException(toString());

        long toId;
        if (fromObjId.equals(toObjId)) {
            toId = fromInfo._id;
        } else {
            toId = getOrCreateFileId_(toObjId, t);
            if (FileInfo.exists(_bsdb.getFileInfo_(toId))) {
                throw new FileAlreadyExistsException(toPath.toString());
            }
        }

        FileInfo toInfo = new FileInfo(toId, -1, fromInfo._length, fromInfo._mtime,
                fromInfo._chunks);
        updateFileInfo_(toPath, toInfo, t);

        if (toId != fromInfo._id) {
            delete_(fromInfo._id, fromPath, t);
        }
    }

    void updateSOID_(SOKID oldId, SOID newId, Trans t) throws SQLException
    {
        // FIXME: this is probably OK for aliasing but most likely not OK for migration
        if (!oldId.sidx().equals(newId.sidx())) l.warn("block file migration {} {}", oldId, newId);

        int n = _bsdb.updateInternalName_(makeFileName(oldId),
                makeFileName(new SOKID(newId.sidx(), newId.oid(), oldId.kidx())), t);
        checkState(n == 0 || n == 1);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class BlockRevProvider implements IPhysicalRevProvider
    {
        @Override
        public Collection<Child> listRevChildren_(Path path) throws IOException, SQLException
        {
            try {
                long dirId = _bsdb.getHistDirByPath_(path);
                if (dirId == DIR_ID_NOT_FOUND) return Collections.emptyList();
                return _bsdb.getHistDirChildren_(dirId);
            } catch (SQLException e) {
                l.warn("list rev children fail", e);
                throw e;
            }
        }

        @Override
        public Collection<Revision> listRevHistory_(Path path) throws IOException, SQLException
        {
            if (path.isEmpty()) return Collections.emptyList();

            try {
                long dirId = _bsdb.getHistDirByPath_(path.removeLast());
                if (dirId == DIR_ID_NOT_FOUND) return Collections.emptyList();
                return _bsdb.getHistFileRevisions_(dirId, path.last());
            } catch (SQLException e) {
                l.warn("list rev hist fail", e);
                throw e;
            }
        }

        @Override
        public RevInputStream getRevInputStream_(Path path, byte[] index)
                throws IOException, SQLException, ExInvalidRevisionIndex
        {
            try {
                FileInfo info = _bsdb.getHistFileInfo_(index);
                if (info == null) throw new ExInvalidRevisionIndex();
                return new RevInputStream(readChunks(info._chunks, info._length), info._length, info._mtime);
            } catch (SQLException e) {
                l.warn("get rev stream fail", e);
                throw e;
            }
        }

        @Override
        public void deleteRevision_(Path path, byte[] index)
                throws IOException, SQLException, ExInvalidRevisionIndex
        {
            try {
                FileInfo info = _bsdb.getHistFileInfo_(index);
                if (info == null) throw new ExInvalidRevisionIndex();

                try (Trans t = _tm.begin_()) {
                    removeHistFile_(info, t);
                    t.commit_();
                }

                // TODO: schedule instead?
                removeDeadBlocks_();
            } catch (SQLException e) {
                l.warn("del rev fail", e);
                throw e;
            }
        }

        @Override
        public void deleteAllRevisionsUnder_(Path path) throws IOException, SQLException
        {
            try {
                long dirId = _bsdb.getHistDirByPath_(path);
                if (dirId == DIR_ID_NOT_FOUND) return;

                try (Trans t = _tm.begin_()) {
                    removeHistDirRecursively_(dirId, t);
                    t.commit_();
                }

                // TODO: schedule instead?
                removeDeadBlocks_();
            } catch (SQLException e) {
                l.warn("del all rev fail", e);
                throw e;
            }
        }
    }


    /**
     * Fully remove a file from the DB:
     *   * deref all blocks used by the file
     *   * remove current file info entry
     */
    private void removeFile_(long id, Trans t) throws SQLException
    {
        FileInfo info = _bsdb.getFileInfo_(id);
        // it's possible for a file index to not have an associated file info entry
        if (info == null) return;
        derefBlocks_(info._chunks, t);
        _bsdb.deleteFileInfo_(id, t);
    }

    /**
     * Remove an old version of a file from the db:
     *   * deref all blocks used
     *   * remove hist file info entry
     */
    private void removeHistFile_(FileInfo info, Trans t) throws SQLException
    {
        derefBlocks_(info._chunks, t);
        _bsdb.deleteHistFileInfo_(info._id, info._ver, t);
    }

    private void derefBlocks_(ContentBlockHash h, Trans t) throws SQLException
    {
        for (ContentBlockHash b : BlockUtil.splitBlocks(h)) _bsdb.decBlockCount_(b, t);
    }

    /**
     * Recursively remove all old file versions under a given directory and deletes
     * the directory entry when done
     */
    private void removeHistDirRecursively_(long dirId, Trans t) throws SQLException
    {
        for (Child c : _bsdb.getHistDirChildren_(dirId)) {
            if (c._dir) {
                removeHistDirRecursively_(_bsdb.getChildHistDir_(dirId, c._name), t);
            } else {
                removeAllHistFiles_(dirId, c._name, t);
            }
        }

        _bsdb.deleteHistDir_(dirId, t);
        _pi.incrementMonotonicProgress();
    }

    /**
     * Remove all old versions of a given file
     */
    private void removeAllHistFiles_(long dirId, String name, Trans t) throws SQLException
    {
        for (Revision r : _bsdb.getHistFileRevisions_(dirId, name)) {
            removeHistFile_(_bsdb.getHistFileInfo_(r._index), t);
            _pi.incrementMonotonicProgress();
        }
    }

    /**
     * Small wrapper that ensures the DBIterator is reset whenever the backend releases the core
     * lock to avoid running into assertions (and potentially worse problems if assertions are
     * disabled).
     */
    private class DeadBlocksIterator implements TokenWrapper
    {
        private final Token _tk;
        private IDBIterator<ContentBlockHash> _it;
        private TCB _tcb;

        DeadBlocksIterator()
        {
            _tk = Preconditions.checkNotNull(_tokenManager.acquire_(Cat.UNLIMITED, "bs-rmd"));
        }

        @Override
        public void pseudoPause(String reason) throws ExAborted
        {
            Preconditions.checkState(_tcb == null);
            try {
                _it.close_();
            } catch (SQLException e) {
                throw new ExAborted(e);
            }
            _it = null;
            _tcb = _tk.pseudoPause_(reason);
        }

        @Override
        public void pseudoResumed() throws ExAborted
        {
            Preconditions.checkNotNull(_tcb);
            _tcb.pseudoResumed_();
        }

        IDBIterator<ContentBlockHash> iterator() throws SQLException
        {
            if (_it == null) _it = _bsdb.getDeadBlocks_();
            return _it;
        }
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
        DeadBlocksIterator it = new DeadBlocksIterator();
        try {
            while (it.iterator().next_()) {
                ContentBlockHash h = it.iterator().get_();
                try (Trans t = _tm.begin_()) {
                    _bsdb.deleteBlock_(h, t);
                    t.commit_();
                }
                // TODO: abort in-progress chunk upload?
                _uploadDir.newChild(h.toHex()).deleteIgnoreError();
                _bsb.deleteBlock(h, it);
                _pi.incrementMonotonicProgress();
            }
        } finally {
            it.iterator().close_();
        }
    }
}
