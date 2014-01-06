package com.aerofs.daemon.core.phy.linked;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.TransUtil;
import com.aerofs.daemon.core.phy.TransUtil.IPhysicalOperation;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper.PathType;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableCollection.Builder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import com.aerofs.daemon.core.phy.linked.linker.IgnoreList;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.linked.LinkedRevProvider.LinkedRevFile;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.lib.Util;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;

public class LinkedStorage implements IPhysicalStorage
{
    protected static Logger l = Loggers.getLogger(LinkedStorage.class);

    final IgnoreList _il;
    final LinkerRootMap _lrm;
    final InjectableFile.Factory _factFile;
    final IFIDMaintainer.Factory _factFIDMan;
    final SharedFolderTagFileAndIcon _sfti;
    private final CfgStoragePolicy _cfgStoragePolicy;
    protected final CfgAbsRoots _cfgAbsRoots;
    private final IStores _stores;
    private final IMapSIndex2SID _sidx2sid;
    private final LinkedRevProvider _revProvider;
    final RepresentabilityHelper _rh;

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
            RepresentabilityHelper rh,
            IStores stores,
            IMapSIndex2SID sidx2sid,
            CfgAbsRoots cfgAbsRoots,
            CfgStoragePolicy cfgStoragePolicy,
            IgnoreList il,
            SharedFolderTagFileAndIcon sfti,
            LinkedRevProvider revProvider)
    {
        _il = il;
        _rh = rh;
        _lrm = lrm;
        _factFile = factFile;
        _factFIDMan = factFIDMan;
        _cfgStoragePolicy = cfgStoragePolicy;
        _sfti = sfti;
        _stores = stores;
        _sidx2sid = sidx2sid;
        _cfgAbsRoots = cfgAbsRoots;
        _revProvider = revProvider;
    }

    @Override
    public void init_() throws IOException, SQLException
    {
        _revProvider.startCleaner_();
    }

    @Override
    public IPhysicalFile newFile_(ResolvedPath path, KIndex kidx) throws SQLException
    {
        return newFile_(path, kidx, PathType.Source);
    }

    LinkedFile newFile_(ResolvedPath path, KIndex kidx, PathType type) throws SQLException
    {
        SOKID sokid = new SOKID(path.soid(), kidx);
        return new LinkedFile(this, sokid, kidx.equals(KIndex.MASTER)
                ? _rh.physicalPath(path, type)
                : LinkedPath.auxiliary(path, _lrm.auxFilePath_(path.sid(), sokid, AuxFolder.CONFLICT)));
    }

    @Override
    public IPhysicalFolder newFolder_(ResolvedPath path) throws SQLException
    {
        return newFolder_(path, PathType.Source);
    }

    LinkedFolder newFolder_(ResolvedPath path, PathType type) throws SQLException
    {
        return new LinkedFolder(this, path.soid(), _rh.physicalPath(path, type));
    }


    @Override
    public IPhysicalPrefix newPrefix_(SOCKID k) throws SQLException
    {
        return new LinkedPrefix(this, k.sokid(),
                LinkedPath.auxiliary(null, auxFilePath(k.sokid(), AuxFolder.PREFIX)));
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
    public void deleteStore_(SIndex sidx, SID sid, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        if (op != PhysicalOp.APPLY) return;

        // delete aux files other than revision files. no need to register for deletion rollback
        // since these files are not important.
        String prefix = LinkedPath.makeAuxFilePrefix(sidx);
        String absAuxRoot = auxRootForStore_(sidx);
        deleteFiles_(absAuxRoot, LibParam.AuxFolder.CONFLICT, prefix);
        deleteFiles_(absAuxRoot, LibParam.AuxFolder.PREFIX, prefix);
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
    public ImmutableCollection<NonRepresentableObject> listNonRepresentableObjects_()
            throws IOException, SQLException
    {
        ImmutableCollection.Builder<NonRepresentableObject> bd = ImmutableList.builder();

        for (LinkerRoot r : _lrm.getAllRoots_()) {
            String nro = Util.join(r.absAuxRoot(), AuxFolder.NON_REPRESENTABLE._name);
            listNonRepresentableObjects_(nro, bd);
        }

        return bd.build();
    }

    private void listNonRepresentableObjects_(String absPath, Builder<NonRepresentableObject> bd)
            throws IOException, SQLException
    {
        String[] children = new File(absPath).list();
        for (String child : children) {
            SOID soid = LinkedPath.soidFromFileNameNullable(child);
            if (soid != null) {
                bd.add(new NonRepresentableObject(soid, _rh.conflict(soid)));
            }
        }
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
        if (l.isDebugEnabled()) l.debug("apply " + prefix + "->" + file);

        final LinkedFile f = (LinkedFile) file;
        final LinkedPrefix p = (LinkedPrefix) prefix;

        if (wasPresent) moveToRev_(f, t);

        move_(p, f, t);
        f._f.setLastModified(mtime);
        f.created_(t);

        return f._f.lastModified();
    }

    /**
     * Move the file to the revision history storage area.
     */
    void moveToRev_(LinkedFile f, Trans t) throws SQLException, IOException
    {
        final LinkedRevFile rev = _revProvider.newLocalRevFile_(
                f._path.virtual, f._f.getAbsolutePath(), f._sokid.kidx());
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

    void onDeletion_(AbstractLinkedObject o, Trans t) throws SQLException, IOException
    {
        _rh.updateNonRepresentableObjectsOnDeletion_(o, t);
    }
}
