package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
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
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.Path;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.ReplaceFileException;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil.OSFamily;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.aerofs.defects.Defects.newDefect;

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
    private final IStores _stores;
    protected final IMapSIndex2SID _sidx2sid;
    private final LinkedStagingArea _sa;
    private final LinkedRevProvider _revProvider;
    final RepresentabilityHelper _rh;
    private final CoreScheduler _sched;

    private final TransLocal<Boolean> _tlUseHistory = new TransLocal<Boolean>() {
        @Override
        protected Boolean initialValue(Trans t)
        {
            return _cfgStoragePolicy.useHistory();
        }
    };

    @Inject
    public LinkedStorage(InjectableFile.Factory factFile,
            IFIDMaintainer.Factory factFIDMan,
            LinkerRootMap lrm,
            IOSUtil osutil,
            InjectableDriver dr,
            RepresentabilityHelper rh,
            IStores stores,
            IMapSIndex2SID sidx2sid,
            CfgAbsRoots cfgAbsRoots,
            CfgStoragePolicy cfgStoragePolicy,
            IgnoreList il,
            SharedFolderTagFileAndIcon sfti,
            LinkedStagingArea sa,
            LinkedRevProvider revProvider,
            CoreScheduler sched)
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
        _sched = sched;
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
    public IPhysicalPrefix newPrefix_(SOCKID k, @Nullable String scope) throws SQLException
    {
        String filePath = auxFilePath(k.sokid(), AuxFolder.PREFIX)
                + (scope != null ? "-" + scope : "");
        return new LinkedPrefix(this, k.sokid(), LinkedPath.auxiliary(null, filePath));
    }

    @Override
    public void deletePrefix_(SOKID sokid) throws SQLException, IOException
    {
        _factFile.create(auxFilePath(sokid, AuxFolder.PREFIX)).deleteIgnoreError();
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
        deleteFiles_(absAuxRoot, AuxFolder.PREFIX, prefix);

        // Unlink external store/root.
        LinkerRoot slr = _lrm.get_(sid);
        if (slr != null) {
            checkArgument(physicalRoot.equals(sid));
            _lrm.unlink_(sid, t);
            l.info("Unlinked {} {}", sid, slr.absRootAnchor());
        }
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
        InjectableFile[] fs = folder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name)
            {
                return name.startsWith(prefix);
            }
        });

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
            _rh.forNonRepresentableObjects_(r.sid(), new Function<SOID, Void>() {
                @Override
                public @Nullable Void apply(@Nullable SOID soid)
                {
                    try {
                        bd.add(new NonRepresentableObject(soid, _rh.getConflict_(soid)));
                    } catch (SQLException e) {
                        l.error("could not determine conflict status {}", soid, e);
                    }
                    return null;
                }
            });
        }

        return bd.build();
    }

    @Override
    public void deleteFolderRecursively_(ResolvedPath path, PhysicalOp op, Trans t)
            throws SQLException, IOException
    {
        if (op == PhysicalOp.APPLY) {
            String absPath = _rh.getPhysicalPath_(path, PathType.SOURCE).physical;
            if (path.isEmpty()) {
                absPath = stagePhysicalRoot_(path.sid(), t);
            }
            _sa.stageDeletion_(absPath, _tlUseHistory.get(t) ? path : Path.root(path.sid()), t);
        }
        if (!path.isEmpty()) {
            onDeletion_(newFolder_(path, PathType.SOURCE), op, t);
        }
    }

    /**
     * We cannot stage a physical root directly because the GUI would throw a fit if the folder
     * goes missing before the conf db is updated and we can't update the conf db until the
     * cleanup is finalized.
     *
     * Hence this contorted approach which creates a randomly-named directory in the staging area,
     * moves all children of the physical root into that directory and stages the tmeporary dir.
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
        if (children != null) {
            for (String child : children) {
                _factFile.create(root, child)
                        .moveInSameFileSystem(_factFile.create(staging, child));
            }
        }

        return staging.getAbsolutePath();
    }


    @Override
    public void scrub_(SOID soid,  @Nonnull Path historyPath, Trans t)
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
                _sa.stageDeletion_(nro, historyPath, t);
            } else if (!historyPath.isEmpty()) {
                _revProvider.newLocalRevFile(historyPath, nro, KIndex.MASTER).save_();
            } else {
                f.delete();
            }
            _rh._nrodb.setRepresentable_(soid, t);
        }

        // TODO: reorg storage for more efficient scrubbing
        deleteFiles_(auxRoot, AuxFolder.CONFLICT, prefix);
        deleteFiles_(auxRoot, AuxFolder.PREFIX, prefix);
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
            applyPreservingStreamsAndACLs_(wasPresent, t, f, p);
        } else {
            if (wasPresent) moveToRev_(f, t);
            move_(p, f, t);
        }
        f._f.setLastModified(mtime);
        f.created_(t);

        l.info("applied {} {} {} {}", f._sokid, mtime, f._f.lastModified(), f.getLength_());

        return f._f.lastModified();
    }

    private void applyPreservingStreamsAndACLs_(boolean wasPresent, Trans t, final LinkedFile f,
            final LinkedPrefix p)
            throws SQLException, IOException
    {
        // We use ReplaceFile to ensure preservation of DACL but that only works if the file is
        // already present. To make sure DACL are correctly inherited when the file is first
        // downloaded, we first create an empty file in the target location, use ReplaceFile
        // and then delete the dummy file from revision history
        if (!wasPresent) {
            _rh.try_(f, t,  new IPhysicalOperation() {
                @Override
                public void run_() throws IOException
                {
                    f._f.createNewFile();
                }
            });
        }

        // behold the magic incantation that will preserve ACLs, stream and other Windows stuff
        final String revPath =  _revProvider.newRevPath(f._path.virtual, f._f.getAbsolutePath(),
                f._sokid.kidx());
        _factFile.create(revPath).getParentFile().ensureDirExists();
        try {
            _dr.replaceFile(f.getAbsPath_(), p._f.getAbsolutePath(), revPath);
        } catch (ReplaceFileException e) {
            newDefect("linked.replace")
                    .setException(e)
                    .sendAsync();
            if (e.replacedMovedToBackup) {
                try {
                    _factFile.create(revPath).moveInSameFileSystem(f._f);
                } catch (IOException re) {
                    l.error("fs rollback failed", re);
                    // FIXME: reset CA to prevent a spurious deletion?
                }
            }
            throw e;
        }

        final boolean deleteRev = !_tlUseHistory.get(t);
        if (wasPresent) {
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_()
                {
                    if (deleteRev) _factFile.create(revPath).deleteIgnoreError();
                }

                @Override
                public void aborted_()
                {
                    try {
                        f._f.moveInSameFileSystem(p._f);
                        _factFile.create(revPath).moveInSameFileSystem(f._f);
                    } catch (IOException e) {
                        l.error("fs rollback failed {}", f._f, e);
                        // FIXME: reset CA to prevent a spurious deletion?
                        SystemUtil.fatal("fs rollback failed " + f._f);
                    }
                }
            });
        } else {
            // delete dummy file immediately
            _factFile.create(revPath).deleteIgnoreError();
        }
    }

    /**
     * Move the file to the revision history storage area.
     */
    void moveToRev_(LinkedFile f, Trans t) throws SQLException, IOException
    {
        final LinkedRevFile rev = _revProvider.newLocalRevFile(f._path.virtual,
                f._f.getAbsolutePath(), f._sokid.kidx());
        rev.save_();

        TransUtil.onRollback_(f._f, t, new IPhysicalOperation() {
            @Override
            public void run_() throws IOException
            {
                rev.rollback_();
            }
        });

        // wait until commit in case we need to put this file back (as in a delete operation
        // that rolls back). This is an unsubtle limit - more nuanced storage policies will
        // be implemented by the history cleaner.
        if (!_tlUseHistory.get(t)) {
            _tlDel.get(t).add(rev);
        }
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

    void move_(final AbstractLinkedObject from, final AbstractLinkedObject to, final Trans t)
        throws IOException, SQLException
    {
        // if the source and destination path are physically equivalent we need to bypass the
        // default retry logic
        if (isPhysicallyEquivalent(from._path, to._path)) {
            TransUtil.moveWithRollback_(from._f, to._f, t);
        } else {
            _rh.try_(to, t, new IPhysicalOperation() {
                    @Override
                    public void run_() throws IOException
                    {
                        TransUtil.moveWithRollback_(from._f, to._f, t);
                    }
                });
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
                newDefect("linked.mtime")
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
}
