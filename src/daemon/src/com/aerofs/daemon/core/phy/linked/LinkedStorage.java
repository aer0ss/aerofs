package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.linked.db.HistoryDatabase;
import com.aerofs.daemon.core.phy.linked.db.HistoryDatabase.DeletedFile;
import com.aerofs.daemon.core.phy.linked.linker.HashQueue;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.TransUtil;
import com.aerofs.daemon.core.phy.TransUtil.IPhysicalOperation;
import com.aerofs.daemon.core.phy.linked.LinkedRevProvider.LinkedRevFile;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper.PathType;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.daemon.core.phy.linked.linker.IgnoreList;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.ReplaceFileException;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil.OSFamily;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.AclFileAttributeView;
import java.sql.SQLException;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.aerofs.defects.Defects.newMetric;

public class LinkedStorage implements IPhysicalStorage
{
    protected static Logger l = Loggers.getLogger(LinkedStorage.class);

    final IgnoreList _il;
    final LinkerRootMap _lrm;
    final InjectableFile.Factory _factFile;
    final IFIDMaintainer.Factory _factFIDMan;
    final SharedFolderTagFileAndIcon _sfti;
    private final IOSUtil _osutil;
    private final InjectableDriver _dr;
    private final CfgStoragePolicy _cfgStoragePolicy;
    protected final CfgAbsRoots _cfgAbsRoots;
    private final StoreHierarchy _stores;
    protected final IMapSIndex2SID _sidx2sid;
    private final LinkedStagingArea _sa;
    private final LinkedRevProvider _revProvider;
    final RepresentabilityHelper _rh;
    private final CoreScheduler _sched;
    private final HashQueue _hq;
    private final HistoryDatabase _hdb;

    private final TransLocal<Boolean> _tlUseHistory = new TransLocal<Boolean>() {
        @Override
        protected Boolean initialValue(Trans t)
        {
            return _cfgStoragePolicy.useHistory();
        }
    };

    @Inject
    public LinkedStorage(InjectableFile.Factory factFile, IFIDMaintainer.Factory factFIDMan,
                         LinkerRootMap lrm, IOSUtil osutil, InjectableDriver dr,
                         RepresentabilityHelper rh, StoreHierarchy stores, IMapSIndex2SID sidx2sid,
                         CfgAbsRoots cfgAbsRoots, CfgStoragePolicy cfgStoragePolicy, IgnoreList il,
                         SharedFolderTagFileAndIcon sfti, LinkedStagingArea sa, HashQueue hq,
                         LinkedRevProvider revProvider, HistoryDatabase hdb, CoreScheduler sched)
    {
        _il = il;
        _rh = rh;
        _lrm = lrm;
        _dr = dr;
        _osutil = osutil;
        _factFile = factFile;
        _factFIDMan = factFIDMan;
        _cfgStoragePolicy = cfgStoragePolicy;
        _sfti = sfti;
        _stores = stores;
        _sidx2sid = sidx2sid;
        _cfgAbsRoots = cfgAbsRoots;
        _sa = sa;
        _revProvider = revProvider;
        _hq = hq;
        _sched = sched;
        _hdb = hdb;
    }

    @Override
    public void init_() throws IOException, SQLException
    {
        _revProvider.startCleaner_();
    }

    @Override
    public void start_()
    {
        _sa.start_();
    }

    @Override
    public IPhysicalFile newFile_(ResolvedPath path, KIndex kidx) throws SQLException
    {
        return newFile_(path, kidx, PathType.SOURCE);
    }

    LinkedFile newFile_(ResolvedPath path, KIndex kidx, PathType type) throws SQLException
    {
        SOKID sokid = new SOKID(path.soid(), kidx);
        return new LinkedFile(this, sokid, kidx.equals(KIndex.MASTER)
                ? _rh.getPhysicalPath_(path, type)
                : LinkedPath.auxiliary(path, _lrm.auxFilePath_(path.sid(), sokid, AuxFolder.CONFLICT)));
    }

    @Override
    public IPhysicalFolder newFolder_(ResolvedPath path) throws SQLException
    {
        return newFolder_(path, PathType.SOURCE);
    }

    LinkedFolder newFolder_(ResolvedPath path, PathType type) throws SQLException
    {
        return new LinkedFolder(this, path.soid(), _rh.getPhysicalPath_(path, type));
    }

    @Override
    public IPhysicalPrefix newPrefix_(SOKID k, @Nullable String scope) throws SQLException
    {
        String filePath = prefixFilePath(k) + (scope != null ? "-" + scope : "");
        return new LinkedPrefix(this, k, LinkedPath.auxiliary(null, filePath));
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
    }

    @Override
    public void deleteStore_(SID physicalRoot, SIndex sidx, SID sid, Trans t)
            throws IOException, SQLException
    {
        // delete aux files other than revision files. no need to register for deletion rollback
        // since these files are not important.
        String prefix = LinkedPath.makeAuxFilePrefix(sidx);
        LinkerRoot plr = _lrm.get_(physicalRoot);
        if (plr == null) {
            l.warn("cannot del {}, phy root {} missing", sid, physicalRoot);
            return;
        }
        String absAuxRoot = plr.absAuxRoot();
        deleteFiles_(absAuxRoot, AuxFolder.CONFLICT, prefix);
        _factFile.create(Util.join(absAuxRoot, AuxFolder.PREFIX._name, sidx.toString()))
                .deleteOrThrowIfExistRecursively();

        // Unlink external store/root.
        LinkerRoot slr = _lrm.get_(sid);
        if (slr != null) {
            checkArgument(physicalRoot.equals(sid));
            _lrm.unlink_(sid, t);
            l.info("Unlinked {} {}", sid, slr.absRootAnchor());
        }
    }

    private String prefixFolderPath(String auxRoot, SOID soid) throws SQLException
    {
        return Util.join(auxRoot, AuxFolder.PREFIX._name,
                soid.sidx().toString(), soid.oid().toStringFormal());
    }

    private String prefixFilePath(SOKID sokid) throws SQLException
    {
        return Util.join(prefixFolderPath(auxRootForStore_(sokid.sidx()), sokid.soid()),
                sokid.kidx().toString());
    }

    String auxFilePath(SOKID sokid, AuxFolder folder) throws SQLException
    {
        return Util.join(auxRootForStore_(sokid.sidx()), folder._name,
                LinkedPath.makeAuxFileName(sokid));
    }

    private String auxRootForStore_(SIndex sidx) throws SQLException
    {
        return _lrm.auxRoot_(rootSID_(sidx));
    }

    private SID rootSID_(SIndex sidx) throws SQLException
    {
        return _sidx2sid.get_(_stores.getPhysicalRoot_(sidx));
    }

    private void deleteFiles_(String absAuxRoot, AuxFolder af, final String prefix)
            throws IOException
    {
        InjectableFile folder = _factFile.create(Util.join(absAuxRoot, af._name));
        InjectableFile[] fs = folder.listFiles((dir, name) -> name.startsWith(prefix));

        if (fs != null) for (InjectableFile f : fs) f.deleteOrThrowIfExist();
    }

    @Override
    public void discardRevForTrans_(Trans t)
    {
        _tlUseHistory.set(t, false);
    }

    @Override
    public boolean isDiscardingRevForTrans_(Trans t)
    {
        return _tlUseHistory.get(t);
    }

    @Override
    public ImmutableCollection<NonRepresentableObject> listNonRepresentableObjects_()
            throws IOException, SQLException
    {
        final ImmutableCollection.Builder<NonRepresentableObject> bd = ImmutableList.builder();

        for (LinkerRoot r : _lrm.getAllRoots_()) {
            _rh.forNonRepresentableObjects_(r.sid(), soid -> {
                try {
                    bd.add(new NonRepresentableObject(soid, _rh.getConflict_(soid)));
                } catch (SQLException e) {
                    l.error("could not determine conflict status {}", soid, e);
                }
                return null;
            });
        }

        return bd.build();
    }

    @Override
    public @Nullable String deleteFolderRecursively_(ResolvedPath path, PhysicalOp op, Trans t)
            throws SQLException, IOException
    {
        String rev = null;
        if (op == PhysicalOp.APPLY) {
            String absPath = _rh.getPhysicalPath_(path, PathType.SOURCE).physical;
            if (path.isEmpty()) {
                absPath = stagePhysicalRoot_(path.sid(), t);
            }
            rev = _sa.stageDeletion_(absPath, _tlUseHistory.get(t) ? path : Path.root(path.sid()),
                    null, t);

            if (rev != null && _tlUseHistory.get(t)) {
                l.info("record folder deletion {} {}:{}", path.soid(), rev, path);
                long id = _hdb.createHistoryPath_(path, t);
                _hdb.insertDeletedFile_(path.soid(), id, rev, t);
            }
        }
        if (!path.isEmpty()) {
            onDeletion_(newFolder_(path, PathType.SOURCE), op, t);
        }
        return rev;
    }

    /**
     * We cannot stage a physical root directly because the GUI would throw a fit if the folder
     * goes missing before the conf db is updated and we can't update the conf db until the
     * cleanup is finalized.
     *
     * Hence this contorted approach which creates a randomly-named directory in the staging area,
     * moves all children of the physical root into that directory and stages the temporary dir.
     */
    private String stagePhysicalRoot_(SID sid, Trans t) throws IOException
    {
        LinkerRoot lr = _lrm.get_(sid);
        lr.stageDeletion(t);

        InjectableFile root = _factFile.create(lr.absRootAnchor());

        String absStaging = Util.join(lr.absAuxRoot(), AuxFolder.STAGING_AREA._name,
                UniqueID.generate().toStringFormal());
        InjectableFile staging = _factFile.create(absStaging);
        staging.mkdir();

        l.info("staging phy root {} -> {}", root, staging);

        String[] children = root.list();
        if (children != null && children.length > 0) {
            for (String child : children) {
                _factFile.create(root, child)
                        .moveInSameFileSystem(_factFile.create(staging, child));
            }

            t.addListener_(new AbstractTransListener() {
                @Override
                public void aborted_() {
                    for (String child : children) {
                        try {
                            _factFile.create(staging, child)
                                    .moveInSameFileSystem(_factFile.create(root, child));
                        } catch (IOException e) {
                            l.error("db/fs inconsistent: failed to rollback move", e);
                            newMetric("linked.rollback")
                                    .setException(e)
                                    .sendAsync();
                        }
                    }
                }
            });
        }

        return staging.getAbsolutePath();
    }

    @Override
    public boolean shouldScrub_(SID sid)
    {
        // no point scrubbing external roots as the aux root is discarded anyway...
        return _lrm.get_(sid) == null;
    }

    @Override
    public void scrub_(SOID soid,  @Nonnull Path historyPath, @Nullable String rev, Trans t)
            throws SQLException, IOException
    {
        //l.info("scrub {} {}", soid, historyPath);
        SID sid = historyPath.sid();
        String auxRoot = _lrm.auxRoot_(sid);
        String prefix = LinkedPath.makeAuxFileName(soid);

        // scrub NRO if needed
        String nro = Util.join(auxRoot, AuxFolder.NON_REPRESENTABLE._name, prefix);
        InjectableFile f = _factFile.create(nro);
        if (f.exists()) {
            if (f.isDirectory()) {
                _sa.stageDeletion_(nro, historyPath, rev, t);
            } else if (!historyPath.isEmpty()) {
                LinkedRevFile rf = rev != null
                        ? _revProvider.localRevFile(historyPath, nro, rev)
                        : _revProvider.newLocalRevFile(historyPath, nro, KIndex.MASTER);
                rf.save_();
                TransUtil.onRollback_(f, t, rf::rollback_);
            } else {
                f.delete();
            }
            _rh._nrodb.setRepresentable_(soid, t);
        }

        // TODO: use hierarchical storage scheme for more efficient scrubbing
        deleteFiles_(auxRoot, AuxFolder.CONFLICT, prefix);

        _factFile.create(prefixFolderPath(auxRoot, soid)).deleteOrThrowIfExistRecursively();
    }

    @Override
    public void applyToHistory_(IPhysicalPrefix prefix, IPhysicalFile file, long mtime, Trans t) {
        throw new UnsupportedOperationException("Applying file directly to history for Linked Storage");
    }

    @Override
    public boolean restore_(SOID soid, OID deletedRoot, List<String> deletedPath, IPhysicalFile pf,
                            Trans t) throws SQLException, IOException {
        // TODO: only throw if at least one of the remaining staging folders covers the deleted file
        if (_sa.hasEntries_()) {
            throw new IOException("wait for staged folders to be processed");
        }
        LinkedFile f = (LinkedFile)pf;
        DeletedFile df = _hdb.getDeletedFile_(soid.sidx(), deletedRoot);
        if (df == null) return false;
        Path p = new Path(rootSID_(soid.sidx()), _hdb.getHistoryPath_(df.path))
                .append(deletedPath.toArray(new String[deletedPath.size()]), 0, deletedPath.size());
        LinkedRevFile rev = _revProvider.localRevFile(p, f._f.getAbsolutePath(), df.rev);
        l.info("attempting to restore {} {}:{}", soid, df.rev, p);
        // revision file may have been removed by the cleaner (or by the user)
        if (!rev.exists_()) {
            l.info("no such rev: {}", _revProvider.listRevHistory_(p));
            return false;
        }
        rev.rollback_();
        TransUtil.onRollback_(f._f, t, rev::save_);
        return true;
    }

    void promoteToAnchor_(SID sid, String path, Trans t) throws SQLException, IOException
    {
        _sfti.addTagFileAndIconIn(sid, path, t);
    }

    void demoteToRegularFolder_(SID sid, String path, Trans t) throws SQLException, IOException
    {
        _sfti.removeTagFileAndIconIn(sid, path, t);
    }

    @Override
    public long apply_(IPhysicalPrefix prefix, IPhysicalFile file, boolean wasPresent, long mtime,
            Trans t) throws IOException, SQLException
    {
        l.debug("apply {} -> {}", prefix, file);

        final LinkedFile f = (LinkedFile) file;
        final LinkedPrefix p = (LinkedPrefix) prefix;

        if (_osutil.getOSFamily() == OSFamily.WINDOWS) {
            if (wasPresent) {
                applyPreservingStreamsAndACLs_(p, f, t);
            } else {
                move_(p, f, t);
                // copy DACL from parent folder to mimic inheritance
                copyACL(_factFile.create(f.getAbsPath_()).getParent(), f.getAbsPath_());
            }
        } else {
            if (wasPresent) moveToRev_(f, false, t);
            move_(p, f, t);
        }
        f._f.setLastModified(mtime);
        f.created_(t);

        l.info("applied {} {} {} {}", f._sokid, mtime, f._f.lastModified(), f.lengthOrZeroIfNotFile());

        t.addListener_(new AbstractTransListener() {
            @Override
            public void committed_() {
                p.cleanup_();
            }
        });
        return f._f.lastModified();
    }

    private static void copyACL(String from, String to) throws IOException
    {
        try {
            Files.getFileAttributeView(Paths.get(to), AclFileAttributeView.class)
                    .setAcl(Files.getFileAttributeView(Paths.get(from), AclFileAttributeView.class).getAcl());
        } catch (Throwable t) {
            // 1. ACL preservation is nice to have but not as important as file sync
            // 2. the new Path API is a piece of shit and doesn't allow explicit use of the magic
            //    prefix to bypass win32 limitations
            // 3. the old API offers no way of manipulating ACLs
            // 4. patching the JDK is non-trivial
            // 5. writing native code for ACL preservation would be even harder
            // 6. most people who care about windows ACL preservation probably aren't using any
            //    filenames that require a magic prefix anyway...
            // => try and ignore errors
            l.info("failed to preserve ACL {}", to, t);
        }
    }

    private void applyPreservingStreamsAndACLs_(final LinkedPrefix p, final LinkedFile f, Trans t)
            throws SQLException, IOException
    {
        final String revPath = _revProvider.newRevPath(f._path.virtual, f._f.getAbsolutePath(),
                f._sokid.kidx());
        InjectableFile rf = _factFile.create(revPath);
        rf.getParentFile().ensureDirExists();

        InjectableFile src = p._f;
        InjectableFile dst = f._f;

        // OMG WTF !?!??!
        //
        // Windows never ceases to amaze
        //
        // Supposedly, ReplaceFile should preserve DACLs. The doc mentions that it could fail to do
        // so if the caller does not have WRITE_DAC for the replacement file. However, it has been
        // observed that ReplaceFile reliably drops ACLs for SMB shares when the replacement file is
        // not in the same SMB share as the replaced file.
        //
        // It has further been observed that while the JRE can reliably read ACL entries for the
        // destination file, trying to write them to the prefix file fails as reliably and silently
        // as ReplaceFile.
        //
        // It has finally been observed that both manual ACL copy and ReplaceFile succeed if the
        // prefix file is first moved to the destination folder. This suggests that Windows, in its
        // infinite wisdom is silently filtering ACEs and that nobody at Microsoft deigned document
        // this behavior.
        //
        // To placate those of our customers that are stuck with antiquated Windows machines and
        // baroque SMB setups, we jump through all the hoops required to get these damn permissions
        // to be preserved, at the (small but non-zero) risk of leaving a cryptically-named prefix
        // lying around in case the daemon dies at just the wrong time.
        //
        // NB: The ".aerofs" prefix in the name of the temporary file ensures that notifications for
        // this file will be ignored by the linker and the random UUID reduces the likelihood of a
        // conflict with a leftover file.
        InjectableFile tmp = dst.getParentFile()
                .newChild(".aerofs.dl." + UniqueID.generate().toStringFormal());
        src.moveInSameFileSystem(tmp);

        // behold the magic incantation that will preserve ACLs, stream and other Windows stuff
        try {
            _dr.replaceFile(f.getAbsPath_(), tmp.getAbsolutePath(), revPath);
        } catch (ReplaceFileException e) {
            newMetric("linked.replace")
                    .setException(e)
                    .addData("replacedMovedToBackup", e.replacedMovedToBackup)
                    .sendAsync();
            if (e.replacedMovedToBackup) {
                l.info("replace failed. move fallback", BaseLogUtil.suppress(e));
                // copy ACLs manually
                copyACL(f.getAbsPath_(), tmp.getAbsolutePath());
                tmp.moveInSameFileSystem(src);
                // try a regular move w/ NRO fallback
                move_(p, f, t);
                return;
            }
            tmp.moveInSameFileSystem(src);
            l.warn("replace failed", BaseLogUtil.suppress(e));
            throw e;
        } catch (IOException|RuntimeException e) {
            tmp.moveInSameFileSystem(src);
            l.warn("replace failed", BaseLogUtil.suppress(e));
            throw e;
        }

        final boolean deleteRev = !_tlUseHistory.get(t);
        t.addListener_(new AbstractTransListener() {
            @Override
            public void committed_()
            {
                if (deleteRev) rf.deleteIgnoreError();
            }

            @Override
            public void aborted_()
            {
                try {
                    dst.moveInSameFileSystem(src);
                    _factFile.create(revPath).moveInSameFileSystem(dst);
                } catch (IOException e) {
                    l.error("fs rollback failed {}", dst, e);
                    throw new Error("fs rollback failed " + dst);
                }
            }
        });
    }

    /**
     * Move the file to the revision history storage area.
     */
    String moveToRev_(LinkedFile f, boolean delete, Trans t)
            throws SQLException, IOException
    {
        final LinkedRevFile rev = _revProvider.newLocalRevFile(f._path.virtual,
                f._f.getAbsolutePath(), f._sokid.kidx());
        rev.save_();

        TransUtil.onRollback_(f._f, t, rev::rollback_);

        // wait until commit in case we need to put this file back (as in a delete operation
        // that rolls back). This is an unsubtle limit - more nuanced storage policies will
        // be implemented by the history cleaner.
        if (!_tlUseHistory.get(t)) {
            _tlDel.get(t).add(rev);
        } else if (delete && f._sokid.kidx().isMaster()) {
            l.info("record folder deletion {} {}:{}", f.sokid().soid(), rev.index(), f._path.virtual);
            long id = _hdb.createHistoryPath_(f._path.virtual, t);
            _hdb.insertDeletedFile_(f._sokid.soid(), id, rev.index(), t);
        }

        return rev.index();
    }

    /**
     * Install a committed_ handler to remove a LinkedRevFile instance.
     * Not used outside of LinkedStorage, so no need to generalize this yet.
     *
     * NOTE: we don't throw from here - an error at transaction cleanup shouldn't kill the world
     */
    TransLocal<List<LinkedRevFile>> _tlDel = new TransLocal<List<LinkedRevFile>>() {
        @Override
        protected List<LinkedRevFile> initialValue(Trans t)
        {
            final List<LinkedRevFile> list = Lists.newArrayList();
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_()
                {
                    for (LinkedRevFile rf : list) {
                        try {
                            rf.delete_();
                        } catch (IOException ioe) {
                            l.warn(Util.e(ioe));
                        }
                    }
                }
            });
            return list;
        }
    };

    static class MoveToRepresentableException extends IOException
    {
        private static final long serialVersionUID = 0L;
        MoveToRepresentableException(Throwable cause) { super(cause); }
    }

    void move_(final AbstractLinkedObject from, final AbstractLinkedObject to, final Trans t)
        throws IOException, SQLException
    {
        // if the source and destination path are physically equivalent we need to bypass the
        // default retry logic
        IPhysicalOperation op = () -> TransUtil.moveWithRollback_(from._f, to._f, t);

        if (isPhysicallyEquivalent(from._path, to._path)) {
            op.run_();
        } else if (from.soid().equals(to.soid())
                && from._path.isNonRepresentable()
                && to._path.isRepresentable()) {
            // When attempting to move an NRO back into the realm of representable objects
            // we cannot simply use RepresentabilityHelper#try_ because it would try to
            // mark the object as non-representable which would fail in the DB layer because
            // the object *already* is non-representable.
            // In some cases, e.g. if the linker is trying to rename a file to avoid name
            // conflicts, the exception simply tells us that the object is still a CNRO and can
            // safely be ignored.
            try {
                op.run_();
            } catch (IOException e) {
                throw new MoveToRepresentableException(e);
            }
        } else {
            _rh.try_(to, t, op);
        }
    }

    private boolean isPhysicallyEquivalent(LinkedPath from, LinkedPath to)
    {
        return from.virtual != null && to.virtual != null
                && _lrm.isPhysicallyEquivalent_(from.virtual, to.virtual);
    }

    void onDeletion_(AbstractLinkedObject o, PhysicalOp op, Trans t)
            throws SQLException, IOException
    {
        _rh.updateNonRepresentableObjectsOnDeletion_(o, op, t);
    }

    void onUnexpectedModification_(IPhysicalFile pf, long expected) throws IOException
    {
        final LinkedFile f = (LinkedFile)pf;
        long actual = f._f.lastModified();
        final LinkerRoot r = _lrm.get_(f._path.virtual.sid());
        if (r == null) {
            l.warn("no linker root");
        } else if (f._path.isInAuxRoot()) {
            // NROs created before mandatory hashing may not have been hashed, which will prevent
            // uploads from proceeding and result in this method being called, at which point we
            // should trigger a re-hashing
            onContentHashMismatch_(f);
        } else {
            /**
             * Some users appear to not get filesystem notifications in some cases which breaks a
             * number of assumptions in the core and usually results in no-sync/very slow sync
             *
             * This stupid behavior can fairly consistently be reproduced on OSX by keeping
             * a file handle open over the entire lifetime of a program. In that case, FSEvents
             * only sends two notifications (first and last), regardless of the actual modification
             * pattern of the file.
             */
            l.warn("modified: {} {} {}", f, actual, expected);

            // only send defect if the mismatch is large enough that it is unlikely to
            // arise from a race condition
            if (actual - expected > 2 * C.SEC) {
                newMetric("linked.mtime")
                        .addData("actual", actual)
                        .addData("expected", expected)
                        .sendAsync();
            }

            // NB: this can result in duplicate notifications in case of race conditions but
            // MightCreate can safely handle that. Better safe than sorry.
            _sched.schedule(new AbstractEBSelfHandling() {
                @Override
                public void handle_()
                {
                    l.info("attempt to fix mtime lag on {}", f);
                    r.mightCreate_(f._path.physical);
                }
            }, 0);
        }
    }

    public void onContentHashMismatch_(LinkedFile f) throws IOException
    {
        checkArgument(f._sokid.kidx().isMaster(), "corrupted conflict branch %s", f._sokid);

        final long length = f._f.length();
        final long mtime = f.lastModified();
        if (_hq.requestHash_(f.soid(), f._f, length, mtime, null)) {
            l.info("hashing {} {} {} [fix mismatch]", f.soid(), mtime, length);
        }
    }
}
