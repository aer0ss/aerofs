package com.aerofs.daemon.core.phy.s3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalObject;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.linked.LinkedStorage;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.C.AuxFolder;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Param;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.aws.s3.S3InitException;
import com.aerofs.lib.aws.s3.chunks.S3Cache;
import com.aerofs.lib.aws.s3.chunks.S3ChunkAccessor;
import com.aerofs.lib.aws.s3.chunks.S3ChunkAccessor.FileUpload;
import com.aerofs.lib.aws.s3.db.S3Database;
import com.aerofs.lib.aws.s3.db.S3Database.S3DirInfo;
import com.aerofs.lib.aws.s3.db.S3Database.S3FileInfo;
import com.aerofs.lib.db.S3Schema.ChunkState;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.s3.S3Config.S3DirConfig;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class S3Storage implements IPhysicalStorage
{
    static final Logger l = Util.l(S3Storage.class);

    static final String DATE_FORMAT = "yyyyMMdd_HHmmss_SSS";

    static final long FILE_CHUNK_SIZE = Param.FILE_CHUNK_SIZE;


    private final S3Database _s3db;
    private final S3ChunkAccessor _s3ChunkAccessor;
    private final S3Cache _s3Cache;
    private final TransManager _transManager;
    private final InjectableFile.Factory _fileFactory;

    private final InjectableFile _prefixDir;
    private final InjectableFile _tempDir;
    private final InjectableFile _storageDir;
    private final InjectableFile _revisionDir;

    private final S3RevProvider _revProvider = new S3RevProvider();

    @Inject
    public S3Storage(
            TransManager transManager,
            CoreScheduler coreScheduler,
            InjectableFile.Factory fileFactory,
            S3Database s3db,
            S3DirConfig s3DirConfig,
            S3ChunkAccessor s3ChunkAccessor,
            S3Cache s3Cache)
    {
        _s3db = s3db;
        _s3Cache = s3Cache;
        _s3ChunkAccessor = s3ChunkAccessor;
        _transManager = transManager;
        _fileFactory = fileFactory;

        InjectableFile s3Dir = _fileFactory.create(s3DirConfig.getS3Dir().getPath());
        if (l.isInfoEnabled()) l.info("s3 dir: " + s3Dir);
        _prefixDir = _fileFactory.create(s3Dir, "prefix");
        _tempDir = _fileFactory.create(s3Dir, "tmp");
        _storageDir = _fileFactory.create(s3Dir, "storage");
        _revisionDir = _fileFactory.create(s3Dir, AuxFolder.REVISION._name);
    }

    @Override
    public void init_() throws IOException, S3InitException
    {
        prepareDir(_prefixDir);
        prepareDir(_tempDir);
        prepareDir(_storageDir);
        prepareDir(_revisionDir);
        try {
            _s3db.init_();
        } catch (SQLException e) {
            throw newIOException(e);
        }

        _s3Cache.init_();
    }

    private void prepareDir(InjectableFile dir) throws IOException
    {
        if (!dir.isDirectory()) {
            try {
                dir.mkdirs();
            } catch (IOException e) {
                if (!dir.isDirectory()) throw e;
            }
        }
    }

    @Override
    public IPhysicalFile newFile_(SOKID sokid, Path path)
    {
        return new S3File(sokid, path);
    }

    @Override
    public IPhysicalFolder newFolder_(SOID soid, Path path)
    {
        return new S3Folder(soid, path);
    }

    @Override
    public IPhysicalPrefix newPrefix_(SOCKID sockid)
    {
        return new S3Prefix(sockid);
    }

    @Override
    public IPhysicalRevProvider getRevProvider()
    {
        return _revProvider;
    }

    @Override
    public void createStore_(SIndex sidx, Path path, Trans t)
    {
    }

    @Override
    public void deleteStore_(SIndex sidx, Path path, PhysicalOp op, Trans t)
    {
        l.warn("deleteStore_");
        // throw new UnsupportedOperationException("delete all files and prefixes");
    }

    @Override
    public long apply_(IPhysicalPrefix iPrefix, IPhysicalFile iFile, boolean wasPresent,
            long mtime, Trans t) throws IOException
    {
        S3Prefix prefix = (S3Prefix)iPrefix;
        S3File to = (S3File)iFile;
        return prefix.apply_(to, wasPresent, mtime, t);
    }



    private long getOrCreateFileId_(SOKID sokid, Trans t) throws SQLException
    {
        return _s3db.getOrCreateFileIndex_(sokid.sidx(), makeFileName(sokid), t);
    }

    private long getFileId_(SOKID id) throws SQLException {
        String iname = makeFileName(id);
        return _s3db.getFileIndex_(id.sidx(), iname);
    }

    private S3FileInfo getFileInfo_(long id) throws SQLException
    {
        return _s3db.getFileInfo_(id);
    }

    private S3FileInfo getFileInfo_(SOKID id) throws SQLException {
        long fileId = getFileId_(id);
        if (l.isTraceEnabled()) l.trace(id + " -> " + fileId);
        if (fileId == S3Database.FILE_ID_NOT_FOUND) return null;
        return getFileInfo_(fileId);
    }


    private long getDirByPath_(Path path) throws SQLException
    {
        long dirId = S3Database.DIR_ID_ROOT;
        for (String name : path.elements()) {
            long child = _s3db.getChildDir_(dirId, name);
            if (child == S3Database.DIR_ID_NOT_FOUND) return child;
            dirId = child;
        }
        return dirId;
    }


    private long getHistDirByPath_(Path path) throws SQLException
    {
        long dirId = S3Database.DIR_ID_ROOT;
        for (String name : path.elements()) {
            long child = _s3db.getChildHistDir_(dirId, name);
            if (child == S3Database.DIR_ID_NOT_FOUND) return child;
            dirId = child;
        }
        return dirId;
    }

    private long getExistingParent_(Path path) throws SQLException, PathNotFoundException
    {
        if (l.isTraceEnabled()) l.trace("getting parent of " + path);
        Path parentPath = path.removeLast();
        long parentId = getDirByPath_(parentPath);
        if (parentId == S3Database.DIR_ID_NOT_FOUND) throw new PathNotFoundException(parentPath);
        return parentId;
    }

    private long prepareNewFilePath_(Path path) throws SQLException, IOException
    {
        long parentId = getExistingParent_(path);
        long pathId = _s3db.getChildFile_(parentId, path.last());
        if (pathId != S3Database.FILE_ID_NOT_FOUND) {
            throw new IOException("File already exists: " + path);
        }
        return parentId;
    }


    @SuppressWarnings("unused")
    private long getOrCreateDir_(Path path, Trans t) throws SQLException, ExAlreadyExist
    {
        long dirId = S3Database.DIR_ID_ROOT;
        boolean found = true;
        for (String name : path.elements()) {
            long child = S3Database.DIR_ID_NOT_FOUND;
            if (found) {
                child = _s3db.getChildDir_(dirId, name);
                if (child == S3Database.DIR_ID_NOT_FOUND) found = false;
            }
            if (child == S3Database.DIR_ID_NOT_FOUND) {
                child = _s3db.createChildDir_(dirId, name, t);
            }
            dirId = child;
        }
        return dirId;
    }

    @SuppressWarnings("unused")
    private long getFileIdByPath_(Path path) throws SQLException, PathNotFoundException
    {
        long parentId = getExistingParent_(path);
        long fileId = _s3db.getChildFile_(parentId, path.last());
        return fileId;
    }

    private Path getFilePath_(S3FileInfo info) throws SQLException, IOException
    {
        List<String> names = Lists.newArrayList();
        names.add(info._name);
        long dirId = info._parent;
        while (dirId != S3Database.DIR_ID_ROOT) {
            S3DirInfo dir = _s3db.getDirInfo_(dirId);
            if (dir == null) throw new IOException("Parent not found: " + dirId);
            names.add(dir._name);
            dirId = dir._parent;
        }
        Collections.reverse(names);
        return new Path(names);
    }

    private void checkPath_(SOKID id, Path expected, S3FileInfo info) throws IOException, SQLException
    {
        Path actual = getFilePath_(info);
        checkPath_(id, expected, actual);
    }

    private void checkPath_(SOKID id, Path expected, Path actual) throws IOException
    {
        if (!expected.equals(actual)) {
            throw new IOException(
                    "Expected " + id + " had path " + expected + ", found " + actual);
        }
    }



    private void writeFileInfo(S3FileInfo info, Trans t) throws SQLException
    {
//        S3FileInfo oldInfo = _s3db.getFileInfo_(info._id);
        _s3db.updateFileInfo(info, t);
        for (ContentHash chunk : S3ChunkAccessor.splitChunks(info._chunks)) {
            _s3db.incChunkCount_(chunk, t);
        }
//        for (HashAttrib chunk : S3ChunkAccessor.splitChunks(oldInfo._chunks)) {
//            _s3db.decChunkCount_(chunk, t);
//        }
    }



    private IOException newIOException(SQLException e)
    {
        return new IOException(e);
    }

    private Date currentDate()
    {
        return new Date();
    }

    private String makeFileName(SOKID sokid)
    {
        return sokid.sidx().toString() + '-' +
                sokid.oid().toStringFormal() + '-' +
                sokid.kidx();
    }

    private String makeFileName(SOCKID sockid)
    {
        return makeFileName(sockid.sokid());
    }


    private static int MAX_UNIQUE_FILE_ATTEMPTS = 10000;

    private static InjectableFile createUniqueFile(InjectableFile.Factory fileFactory, InjectableFile dir,
            String prefix, Date date, String suffix) throws IOException
    {
        if (prefix == null) prefix = "";
        if (suffix == null) suffix = "";
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        Date adjustedDate = new Date(date.getTime());
        for (int i = 0; i < MAX_UNIQUE_FILE_ATTEMPTS; ++i) {
            adjustedDate.setTime(date.getTime() + i);
            String name = prefix + dateFormat.format(adjustedDate) + suffix;
            InjectableFile file = fileFactory.create(dir, name);
            if (file.createNewFileIgnoreError()) {
                return file;
            }
        }
        throw new IOException("Could not create unique file in " + dir);
    }


    class S3Prefix implements IPhysicalPrefix
    {
        private final SOCKID _sockid;
        private ContentHash _hash;

        S3Prefix(SOCKID sockid)
        {
            _sockid = sockid;
        }

        private InjectableFile getFile()
        {
            return _fileFactory.create(_prefixDir, makeFileName(_sockid));
        }

        @Override
        public long getLength_()
        {
            return getFile().getLengthOrZeroIfNotFile();
        }

        @Override
        public void moveTo_(IPhysicalPrefix pf, Trans t) throws IOException
        {
            S3Prefix to = (S3Prefix)pf;
            LinkedStorage.moveWithRollback_(getFile(), to.getFile(), t);
        }

        // for computing hash
        @Override
        public InputStream newInputStream_() throws IOException
        {
            return new FileInputStream(getFile().getImplementation());
        }

        @Override
        public OutputStream newOutputStream_(boolean append) throws IOException
        {
            return new FileOutputStream(getFile().getImplementation(), append);
        }

        @Override
        public ContentHash prepare_(final Token tk) throws IOException
        {
            try {
                if (_hash != null) return _hash;
                l.info(">>> preparing prefix for " + _sockid);

//                FileUpload upload = new PrefixChunker(getFile().getImplementation(), tk);
                FileUpload upload = new SimpleUpload(getFile().getImplementation(), tk);
                _hash = upload.uploadChunks();
                Trans t = _transManager.begin_();
                try {
                    for (ContentHash chunk : S3ChunkAccessor.splitChunks(_hash)) {
                        _s3db.finishChunkUpload_(chunk, t);
                    }
                    t.commit_();
                } finally {
                    t.end_();
                }

                l.info("<<< done preparing prefix for " + _sockid);
            } catch (SQLException e) {
                throw newIOException(e);
            }
            return _hash;
        }

        long apply_(S3File to, boolean wasPresent,
                long mtime, Trans t) throws IOException
        {
            try {
                final S3Prefix prefix = this;

                Preconditions.checkArgument(prefix._sockid.sokid().equals(to._sokid));
                assert prefix._hash != null;
                long length = prefix.getLength_();

                long id = getOrCreateFileId_(to._sokid, t);
                if (!wasPresent) {
                    S3FileInfo oldInfo = getFileInfo_(id);
                    if (S3FileInfo.exists(oldInfo)) {
                        throw new IOException("File exists: " + this);
                    }
                }

                long parentId = getExistingParent_(to._path);

                if (!wasPresent) {
                    long pathId = _s3db.getChildFile_(parentId, to._path.last());
                    if (pathId != S3Database.FILE_ID_NOT_FOUND) {
                        throw new IOException("File already exists: " + to._path);
                    }
                }

                Date date = new Date(mtime);

                String chunkString = prefix._hash.toHex();

                S3FileInfo info = new S3FileInfo(id, to._sokid.sidx(), parentId, to._path.last(),
                        length, date, prefix._hash);
                writeFileInfo(info, t);
                if (l.isDebugEnabled()) l.debug("inserted " + chunkString);

                if (wasPresent) to.moveToRev_(t);

                boolean storeFile = false;
                if (storeFile) {
                    InjectableFile toFile = to.getFile();
                    LinkedStorage.moveWithRollback_(prefix.getFile(), toFile, t);
                    toFile.setLastModified(mtime);
                } else {
//                    final PhysicalFile toFile = _fileFactory.create(_tempDir, prefix.getFile().getName());
//                    LinkedStorage.moveWithRollback_(prefix.getFile(), toFile, t);
                    t.addListener_(new AbstractTransListener() {
                        @Override
                        public void committed_() {
                            prefix.getFile().deleteIgnoreError();
                        }
                    });
                }

                return mtime;
            } catch (SQLException e) {
                throw newIOException(e);
            }
        }
    }


    class S3File implements IPhysicalFile
    {
        private final SOKID _sokid;
        private final Path _path;

        private S3File(SOKID sokid, Path path)
        {
            _sokid = sokid;
            _path = path;
            if (l.isDebugEnabled()) l.debug(this);
        }

        @Override
        public String toString()
        {
            return "S3File(id=" + _sokid + ",path=" + _path + ")";
        }

        private String getInternalName()
        {
            return makeFileName(_sokid);
        }

        private InjectableFile getFile()
        {
            return _fileFactory.create(_storageDir, getInternalName());
        }

        private void moveToRev_(Trans t) throws IOException
        {
            InjectableFile fromFile = getFile();
            if (fromFile.isFile()) {
                Date date = currentDate();
                InjectableFile revFile = createUniqueFile(_fileFactory, _revisionDir,
                        getInternalName() + "-", date, "");
                LinkedStorage.moveWithRollback_(fromFile, revFile, t);
            }
        }

        @Override
        public void create_(PhysicalOp op, Trans t) throws IOException
        {
            if (l.isDebugEnabled()) {
                l.debug(this + ".create_(" + op + ")");
            }
            try {
                long id = getOrCreateFileId_(_sokid, t);
                if (S3FileInfo.exists(getFileInfo_(id))) {
                    throw new IOException("File exists: " + this);
                }

                long parentId = prepareNewFilePath_(_path);

                Date date = currentDate();

                S3FileInfo info = new S3FileInfo(id, _sokid.sidx(), parentId, _path.last(),
                        0, date, S3Database.EMPTY_FILE_CHUNKS);
                writeFileInfo(info, t);
            } catch (SQLException e) {
                throw newIOException(e);
            }
        }

        @Override
        public void delete_(PhysicalOp op, Trans t) throws IOException
        {
            if (l.isDebugEnabled()) {
                l.debug(this + ".delete_(" + op + ")");
            }
            try {
                S3FileInfo oldInfo = getFileInfo_(_sokid);
                if (!S3FileInfo.exists(oldInfo)) throw new FileNotFoundException(toString());
                checkPath_(_sokid, _path, oldInfo);

                Date date = currentDate();

                S3FileInfo info = S3FileInfo.newDeletedFileInfo(oldInfo._id, oldInfo._sidx, date);
                writeFileInfo(info, t);

                moveToRev_(t);
            } catch (SQLException e) {
                throw newIOException(e);
            }
        }

        @Override
        public void move_(IPhysicalObject toObj, PhysicalOp op, Trans t) throws IOException
        {
            S3File from = this;
            S3File to = (S3File)toObj;
            if (l.isDebugEnabled()) {
                l.debug(this + ".move_(" + to + ", " + op + ")");
            }
            try {
                SOKID fromObjId = from._sokid;
                Path fromPath = from._path;

                SOKID toObjId = to._sokid;
                Path toPath = to._path;

                if (l.isInfoEnabled()) {
                    if (!fromObjId.equals(toObjId)) l.info(fromObjId + " -> " + toObjId);
                    if (!fromPath.equals(toPath)) l.info(fromPath + " -> " + toPath);
                }

                S3FileInfo fromInfo = getFileInfo_(fromObjId);
                if (!S3FileInfo.exists(fromInfo)) throw new FileNotFoundException(toString());
                checkPath_(fromObjId, fromPath, fromInfo);

                long toParentId;
                if (fromPath.equals(toPath)) {
                    toParentId = fromInfo._parent;
                } else {
                    toParentId = prepareNewFilePath_(toPath);
                }

                long toId;
                if (fromObjId.equals(toObjId)) {
                    toId = fromInfo._id;
                } else {
                    toId = getOrCreateFileId_(toObjId, t);
                    if (S3FileInfo.exists(getFileInfo_(toId))) {
                        throw new IOException("File exists: " + to);
                    }
                }

                Date date = currentDate();

                S3FileInfo toInfo = new S3FileInfo(toId, toObjId.sidx(), toParentId, toPath.last(),
                        fromInfo._length, date, fromInfo._chunks);
                writeFileInfo(toInfo, t);

                if (toId != fromInfo._id) {
                    from.delete_(op, t);
                }

                if (op == PhysicalOp.APPLY) {
                    if (from.getFile().isFile()) {
                        LinkedStorage.moveWithRollback_(from.getFile(), to.getFile(), t);
                    }
                }
            } catch (SQLException e) {
                throw newIOException(e);
            }
        }

        @Override
        public long getLength_()
        {
            try {
                S3FileInfo info = getFileInfo_(_sokid);
                if (!S3FileInfo.exists(info)) return 0;
                return info._length;
            } catch (SQLException e) {
                l.warn(e);
                return 0;
            }
        }

        @Override
        public ContentHash getHash_()
        {
            return null;
        }

        @Override
        public long getLastModificationOrCurrentTime_() throws IOException
        {
            return currentDate().getTime();
        }

        @Override
        public boolean wasModifiedSince(long mtime, long len) throws IOException
        {
            return false;
        }

        @Override
        public String getAbsPath_()
        {
            return null;
        }

        @Override
        public InputStream newInputStream_() throws IOException
        {
            try {
                S3FileInfo info = getFileInfo_(_sokid);
                if (!S3FileInfo.exists(info)) throw new FileNotFoundException(toString());
                ContentHash hash = info._chunks;
                return _s3Cache.readChunks(hash);
            } catch (SQLException e) {
                throw new IOException(e);
            }
        }
    }

    class S3Folder implements IPhysicalFolder
    {
        final SOKID _sokid;
        final Path _path;

        private S3Folder(SOID soid, Path path)
        {
            _sokid = new SOKID(soid, KIndex.MASTER);
            _path = path;
            if (l.isDebugEnabled()) l.debug(this);
        }

        @Override
        public String toString()
        {
            return "S3Folder(id=" + _sokid + ",path=" + _path + ")";
        }

        @Override
        public void create_(PhysicalOp op, Trans t) throws IOException
        {
            if (l.isDebugEnabled()) {
                l.debug(this + ".create_(" + op + ")");
            }
            try {
                long parentId = getExistingParent_(_path);
                if (l.isTraceEnabled()) l.trace("parentId=" + parentId);
                long dirId = _s3db.getChildDir_(parentId, _path.last());
                if (dirId != S3Database.DIR_ID_NOT_FOUND) {
                    // throw new IOException("Dir exists: " + this + ": " + dirId);
                    l.debug(new IOException("Dir exists: " + this + ": " + dirId));
                } else {
                    try {
                        dirId = _s3db.createChildDir_(parentId, _path.last(), t);
                    } catch (ExAlreadyExist e) {
                        // throw new IOException("Dir exists: " + this, e);
                        l.debug(new IOException("Dir exists: " + this, e));
                    }
                }
                if (l.isTraceEnabled()) l.trace("dirId=" + dirId);
            } catch (SQLException e) {
                throw newIOException(e);
            }
        }

        @Override
        public void delete_(PhysicalOp op, Trans t) throws IOException
        {
            if (l.isDebugEnabled()) {
                l.debug(this + ".delete_(" + op + ")");
            }
            try {
                long dirId = getDirByPath_(_path);
                if (dirId == S3Database.DIR_ID_NOT_FOUND) {
                    // throw new PathNotFoundException(_path);
                    l.debug(new PathNotFoundException(_path));
                } else {
                    _s3db.deleteDir_(dirId, t);
                }
            } catch (SQLException e) {
                throw newIOException(e);
            }
        }

        @Override
        public void move_(IPhysicalObject toObj, PhysicalOp op, Trans t) throws IOException
        {
            S3Folder from = this;
            S3Folder to = (S3Folder)toObj;
            if (l.isDebugEnabled()) {
                l.debug(this + ".move_(" + to + ", " + op + ")");
            }
            try {
//                SOKID fromObjId = from._sokid;
                Path fromPath = from._path;

//                SOKID toObjId = to._sokid;
                Path toPath = to._path;

                if (l.isDebugEnabled()) {
                    l.debug(fromPath + "/ -> " + toPath + '/');
                }

                long toParentId = getExistingParent_(toPath);
//                l.debug("toParentId=" + toParentId);
                long oldDirId = _s3db.getChildDir_(toParentId, toPath.last());
//                l.debug("oldDirId=" + oldDirId);
                if (oldDirId != S3Database.DIR_ID_NOT_FOUND) {
                    throw new IOException("Dir exists: " + to + ": " + oldDirId);
                }

                long dirId = getDirByPath_(fromPath);
                _s3db.setDirParentAndName_(dirId, toParentId, toPath.last(), t);
            } catch (SQLException e) {
                throw newIOException(e);
            }
        }
    }

    class S3RevProvider implements IPhysicalRevProvider
    {
        @Override
        public Collection<Child> listRevChildren_(Path path) throws Exception
        {
            try {
                return _s3db.getHistDirChildren_(getHistDirByPath_(path));
            } catch (SQLException e) {
                return Collections.emptyList();
            }
        }

        @Override
        public Collection<Revision> listRevHistory_(Path path) throws Exception
        {
            try {
                return _s3db.getHistFileRevisions_(getHistDirByPath_(path.removeLast()),
                                                   path.last());
            } catch (SQLException e) {
                return Collections.emptyList();
            }
        }

        @Override
        public RevInputStream getRevInputStream_(Path path, byte[] index) throws Exception
        {
            try {
                S3FileInfo info = _s3db.getHistFileInfo_(getHistDirByPath_(path.removeLast()),
                                                         path.last(),
                                                         index);
                if (info == null) {
                    throw new ExNotFound("Invalid revision index");
                }
                return new RevInputStream(_s3Cache.readChunks(info._chunks), info._length);
            } catch (SQLException e) {
                return null;
            }
        }
    }

    class SimpleUpload extends FileUpload
    {
        private final Token _tk;
        private TCB _tcb;

        public SimpleUpload(File file, Token tk)
        {
            super(_s3ChunkAccessor, MoreExecutors.sameThreadExecutor(),
                    S3Storage.this._tempDir.getImplementation(), file);
            _tk = tk;
        }

        @Override
        public ContentHash uploadChunks() throws IOException
        {
            try {
                try {
                    pseudoPause_();
                    return super.uploadChunks();
                } finally {
                    pseudoResumed_();
                }
            } catch (ExAborted e) {
                throw new IOException(e);
            }
        }

        private ChunkState startChunkUpload(ChunkUpload chunk, File file) throws SQLException
        {
            Trans t = _transManager.begin_();
            try {
                ChunkState cs = _s3db.getChunkState_(chunk.getHash());
                if (cs != ChunkState.UPLOADED && cs != ChunkState.REFERENCED) {
                    _s3db.startChunkUpload_(chunk.getHash(), chunk.getEncodedLength(), t);
                    cs = ChunkState.UPLOADING;
                }
                t.commit_();
                return cs;
            } finally {
                t.end_();
            }
        }

        @Override
        protected void uploadChunk(final ChunkUpload chunk, final File file)
                throws IOException
        {
            ChunkState cs;
            try {
                try {
                    pseudoResumed_();
                    cs = startChunkUpload(chunk, file);
                } finally {
                    pseudoPause_();
                }
            } catch (ExAborted e) {
                throw new IOException(e);
            } catch (SQLException e) {
                throw new IOException(e);
            }
            if (cs == ChunkState.UPLOADING) {
                // XXX: used to only upload if state was uploading, but always doing it is
                // safer now that the upload code checks for duplicates itself
            }
            super.uploadChunk(chunk, file);
        }

        private void pseudoPause_() throws ExAborted
        {
            assert _tcb == null;
            _tcb = _tk.pseudoPause_("still uploading to S3");
        }

        private void pseudoResumed_() throws ExAborted
        {
            TCB tcb = _tcb;
            _tcb = null;
            tcb.pseudoResumed_();
        }

    }

    class PrefixChunker extends FileUpload
    {
        /*
         * I can't believe I'm doing this. All the work of
         * compressing, encrypting, hashing, and uploading is
         * done in other threads, but those threads need to
         * access the database to update chunk status rows. The
         * worker threads forward operations to the core thread
         * by enqueueing Runnable objects; the core thread loops
         * waiting for tasks from workers until all the workers
         * are done.
         */

        private final Token _tk;
        private final BlockingQueue<Runnable> _queue =
                new LinkedBlockingQueue<Runnable>();
        private int _remainingChunks;
        private TCB _tcb;

        public PrefixChunker(File file, Token tk)
        {
            // TODO: send executor
            super(_s3ChunkAccessor, null, S3Storage.this._tempDir.getImplementation(), file);
            _tk = tk;
        }

        @Override
        public ContentHash uploadChunks() throws IOException
        {
            try {
                try {
                    pseudoPause_();
                    return super.uploadChunks();
                } finally {
                    pseudoResumed_();
                }
            } catch (ExAborted e) {
                throw new IOException(e);
            }
        }

        @Override
        protected List<Future<ChunkUpload>> invokeAll(List<Callable<ChunkUpload>> callables)
                throws InterruptedException
        {
            _remainingChunks = callables.size();
            List<Future<ChunkUpload>> futures =
                    new ArrayList<Future<ChunkUpload>>(callables.size());
            for (Callable<ChunkUpload> c : callables) {
                FutureTask<ChunkUpload> f = new FutureTask<ChunkUpload>(c) {
                    @Override
                    protected void done()
                    {
                        _queue.add(new Runnable() {
                            @Override
                            public void run()
                            {
                                --_remainingChunks;
                            }
                        });
                    }
                };
                futures.add(f);
                _executor.execute(f);
            }
            while (_remainingChunks > 0) {
                Runnable r = _queue.take();
                r.run();
            }

            return futures;
        }

        @Override
        protected void uploadChunk(final ChunkUpload chunk, final File file)
                throws IOException
        {
            ChunkState cs = runTask(new Callable<ChunkState>() {
                @Override
                public ChunkState call() throws SQLException, ExAborted
                {
                    try {
                        pseudoResumed_();
                        Trans t = _transManager.begin_();
                        try {
                            ChunkState cs = _s3db.getChunkState_(chunk.getHash());
                            if (cs != ChunkState.UPLOADED && cs != ChunkState.REFERENCED) {
                                _s3db.startChunkUpload_(chunk.getHash(), chunk.getEncodedLength(), t);
                                cs = ChunkState.UPLOADING;
                            }
                            t.commit_();
                            return cs;
                        } finally {
                            t.end_();
                        }
                    } finally {
                        pseudoPause_();
                    }
                }
            });
            if (cs == ChunkState.UPLOADING) {
                // XXX: used to only upload if state was uploading, but always doing it is
                // safer now that the upload code checks for duplicates itself
            }
            super.uploadChunk(chunk, file);
        }

        private void pseudoPause_() throws ExAborted
        {
            assert _tcb == null;
            _tcb = _tk.pseudoPause_("still uploading to S3");
        }

        private void pseudoResumed_() throws ExAborted
        {
            TCB tcb = _tcb;
            _tcb = null;
            tcb.pseudoResumed_();
        }

        private <T> T runTask(Callable<T> callable) throws IOException
        {
            FutureTask<T> task = new FutureTask<T>(callable);
            _queue.add(task);
            T result;
            try {
                result = task.get();
            } catch (InterruptedException e) {
                throw new IOException(e);
            } catch (ExecutionException e) {
                throw new IOException(e);
            }
            return result;
        }

    }
}

class PathNotFoundException extends FileNotFoundException
{
    private static final long serialVersionUID = 1L;

    PathNotFoundException(Path path)
    {
        super("Path not found: " + path);
    }
}
