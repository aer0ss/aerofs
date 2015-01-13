/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import static com.aerofs.daemon.core.phy.block.BlockStorageDatabase.*;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Child;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Revision;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.block.BlockStorageSchema.BlockState;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend.TokenWrapper;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.cfg.CfgAbsDefaultAuxRoot;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
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
import com.google.common.io.Files;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

/**
 * IPhysicalStorage interface to a block-based storage backend
 *
 * The logic is split between a backend-agnostic database, which keeps track of block usage and a
 * backend which handles the actual storage, either locally or remotely.
 */
class BlockStorage implements IPhysicalStorage
{
    private static final Logger l = Loggers.getLogger(BlockStorage.class);

    private TokenManager _tokenManager;
    private TransManager _tm;
    private CoreScheduler _sched;
    private InjectableFile.Factory _fileFactory;
    private CfgStoragePolicy _storagePolicy;

    private InjectableFile _prefixDir;

    private IBlockStorageBackend _bsb;
    private BlockStorageDatabase _bsdb;

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

        final String prefixDirPath = absDefaultAuxRoot.get();
        _prefixDir = _fileFactory.create(prefixDirPath, LibParam.AuxFolder.PREFIX._name);
    }

    @Override
    public void init_() throws IOException
    {
        ensurePrefixDirExists();
        initializeBlockStorage();

        for (IBlockStorageInitable i : _initables) i.init_(_bsb);
    }

    @Override
    public void start_() {}

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
        String fileName = makeFileName(k) + (scope != null ? "-" + scope : "");
        return new BlockPrefix(this, k, _fileFactory.create(_prefixDir, fileName));
    }

    @Override
    public void deletePrefix_(SOKID sokid) throws SQLException, IOException
    {
        _fileFactory.create(_prefixDir, makeFileName(sokid)).deleteIgnoreError();
    }

    /**
     * Update database after complete prefix download
     *
     * By the time this method is called:
     *      1. the prefix file should be fully downloaded
     *      2. the contents of the prefix file should have been chunked and stored by the backend
     *
     * TODO(jP): if a rollback occurs, check for new chunks in the backend that are not ref'ed
     * by anybody. Currently they are orphaned.
     */
    @Override
    public long apply_(IPhysicalPrefix prefix, IPhysicalFile file, boolean wasPresent, long mtime,
            Trans t) throws IOException, SQLException
    {
        final BlockPrefix from = (BlockPrefix)prefix;
        final BlockFile to = (BlockFile)file;

        checkArgument(from._sokid.equals(to._sokid),
                "tried to move prefix %s to storage loc for %s", from, to);
        assert from._hash != null;
        long length = prefix.getLength_();

        long id = getOrCreateFileId_(to._sokid, t);
        FileInfo oldInfo = _bsdb.getFileInfo_(id);
        if (!wasPresent) {
            if (FileInfo.exists(oldInfo)) throw new FileAlreadyExistsException(file.toString());
        } else {
            if (!FileInfo.exists(oldInfo)) throw new FileNotFoundException(file.toString());
        }

        // no need to explicitly specify version, DB auto-increments it
        updateFileInfo_(to._path, new FileInfo(id, -1, length, mtime, from._hash), t);
        l.debug("inserted {}", from._hash);

        // If transaction succeeds, delete the prefix file
        t.addListener_(new AbstractTransListener() {
            @Override
            public void committed_() {
                from._f.deleteIgnoreError();
            }
        });

        return mtime;
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

        if (_tlUseHistory.get(t)) {
            _bsdb.preserveFileInfo(newPath, oldFile, t);
        } else if (FileInfo.exists(oldFile)) {
            derefBlocks_(oldFile._chunks, t);
            scheduleBlockCleaner_(t);
        }

        _bsdb.updateFileInfo(newPath, newFile, t);
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

        deletePrefixFilesMatching_(prefix);
    }


    private void deletePrefixFilesMatching_(final String prefix) throws IOException
    {
        InjectableFile[] fs = _prefixDir.listFiles((dir, name) -> name.startsWith(prefix));

        if (fs != null) for (InjectableFile f : fs) f.deleteOrThrowIfExist();
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
    public void deleteFolderRecursively_(ResolvedPath path, PhysicalOp op, Trans t)
            throws SQLException, IOException
    {
    }

    @Override
    public boolean shouldScrub_(SID sid) { return true; }

    @Override
    public void scrub_(SOID soid, @Nonnull Path historyPath, Trans t)
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
        deletePrefixFilesMatching_(prefix);
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

    @Nonnull FileInfo getFileInfo_(SOKID sokid) throws SQLException
    {
        FileInfo fi = getFileInfoNullable_(sokid);
        checkNotNull(fi, sokid);
        return fi;
    }


    public InputStream readChunks(ContentBlockHash hash) throws IOException
    {
        return new BlockInputStream(_bsb, hash);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * "Prepare" a fully downloaded prefix before moving it into persistent storage
     *
     * We use this method to perform the actual movement as the backend may use remote storage.
     */
    ContentBlockHash prepare_(BlockPrefix prefix, Token tk) throws IOException
    {
        ContentBlockHash h;
        try {
            l.debug(">>> preparing prefix for {}", prefix._sokid);
            h = new Chunker(prefix._f, tk, _bsb).splitAndStore();
            l.debug("<<< done preparing prefix for {}", prefix._sokid);
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
            super(Files.asByteSource(f.getImplementation()), f.getLengthOrZeroIfNotFile(),
                    bsb);
            _tk = tk;
        }

        public ContentBlockHash splitAndStore() throws IOException, SQLException, ExAborted
        {
            try {
                pseudoPause_();
                return splitAndStore_();
            } finally {
                pseudoResumed_();
            }
        }

        @Override
        protected StorageState prePutBlock_(Block block) throws SQLException
        {
            try {
                try {
                    pseudoResumed_();
                    return prePutBlock(block);
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

        private StorageState prePutBlock(Block block) throws SQLException
        {
            StorageState retval = StorageState.ALREADY_STORED;
            try (Trans t = _tm.begin_()) {
                BlockState bs = _bsdb.getBlockState_(block._hash);
                if (bs != BlockState.STORED && bs != BlockState.REFERENCED) {
                    _bsdb.prePutBlock_(block._hash, block._length, t);
                    retval = StorageState.NEEDS_STORAGE;
                }
                t.commit_();
            }
            return retval;
        }

        private void postPutBlock(Block block) throws SQLException
        {
            try (Trans t = _tm.begin_()) {
                _bsdb.postPutBlock_(block._hash, t);
                t.commit_();
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

        FileInfo toInfo = new FileInfo(toId, -1, fromInfo._length, new Date().getTime(),
                fromInfo._chunks);
        updateFileInfo_(toPath, toInfo, t);

        if (toId != fromInfo._id) {
            delete_(from, t);
        }
    }

    void updateSOID_(SOKID oldId, SOID newId, Trans t) throws SQLException
    {
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
                return new RevInputStream(readChunks(info._chunks), info._length, info._mtime);
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
                _bsb.deleteBlock(h, it);
                _pi.incrementMonotonicProgress();
            }
        } finally {
            it.iterator().close_();
        }
    }
}
