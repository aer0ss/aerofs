package com.aerofs.daemon.core.phy.linked;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap.IListener;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.os.OSUtil;
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
import com.aerofs.lib.Path;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;

public class LinkedStorage implements IPhysicalStorage, IListener
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

    @Inject
    public LinkedStorage(InjectableFile.Factory factFile,
            IFIDMaintainer.Factory factFIDMan,
            LinkerRootMap lrm,
            IStores stores,
            IMapSIndex2SID sidx2sid,
            CfgAbsRoots cfgAbsRoots,
            CfgStoragePolicy cfgStoragePolicy,
            IgnoreList il,
            SharedFolderTagFileAndIcon sfti)
    {
        _il = il;
        _lrm = lrm;
        _factFile = factFile;
        _factFIDMan = factFIDMan;
        _cfgStoragePolicy = cfgStoragePolicy;
        _sfti = sfti;
        _stores = stores;
        _sidx2sid = sidx2sid;
        _cfgAbsRoots = cfgAbsRoots;
        _revProvider = new LinkedRevProvider(this, factFile);

        _lrm.addListener_(this);
    }

    @Override
    public void init_() throws IOException
    {
        for (Entry<SID, String> e : _cfgAbsRoots.get().entrySet()) {
            ensureSaneAuxRoot_(e.getKey(), e.getValue());
        }

        _revProvider.startCleaner_();
    }

    @Override
    public void addingRoot_(LinkerRoot root) throws IOException
    {
        ensureSaneAuxRoot_(root.sid(), root.absRootAnchor());
    }

    @Override
    public void removingRoot_(LinkerRoot root) throws IOException
    {
    }

    protected void ensureSaneAuxRoot_(SID sid, String absRoot) throws IOException
    {
        ensureSaneAuxRoot_(Cfg.absAuxRootForPath(absRoot, sid));
    }

    private void ensureSaneAuxRoot_(String absAuxRoot) throws IOException
    {
        l.info("aux root {}", absAuxRoot);

        // create aux folders. other codes assume these folders already exist.
        for (AuxFolder af : LibParam.AuxFolder.values()) {
            _factFile.create(Util.join(absAuxRoot, af._name)).ensureDirExists();
        }

        OSUtil.get().markHiddenSystemFile(absAuxRoot);
    }


    @Override
    public IPhysicalFile newFile_(SOKID sokid, Path path)
    {
        return new LinkedFile(this, sokid, path);
    }

    @Override
    public IPhysicalFolder newFolder_(SOID soid, Path path)
    {
        return new LinkedFolder(this, soid, path);
    }

    @Override
    public IPhysicalPrefix newPrefix_(SOCKID k) throws SQLException
    {
        return new LinkedPrefix(_factFile, k, auxRootForStore_(k.sidx()));
    }

    @Override
    public void deletePrefix_(SOKID sokid) throws SQLException, IOException
    {
        _factFile.create(Util.join(auxRootForStore_(sokid.sidx()), LibParam.AuxFolder.PREFIX._name,
                LinkedStorage.makeAuxFileName(sokid))).deleteIgnoreError();
    }

    private SID rootSID_(SIndex sidx) throws SQLException
    {
        return _sidx2sid.get_(_stores.getPhysicalRoot_(sidx));
    }

    private String auxRootForStore_(SIndex sidx) throws SQLException
    {
        return auxRootForStore_(rootSID_(sidx));
    }

    String auxRootForStore_(SID root)
    {
        return Cfg.absAuxRootForPath(_lrm.absRootAnchor_(root), root);
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
        String prefix = makeAuxFilePrefix(sidx);
        String absAuxRoot = auxRootForStore_(sidx);
        deleteFiles_(absAuxRoot, LibParam.AuxFolder.CONFLICT, prefix);
        deleteFiles_(absAuxRoot, LibParam.AuxFolder.PREFIX, prefix);
    }

    TransLocal<Boolean> _tlUseHistory = new TransLocal<Boolean>() {
        @Override
        protected Boolean initialValue(Trans t)
        {
            return _cfgStoragePolicy.useHistory();
        }
    };

    @Override
    public void discardRevForTrans_(Trans t)
    {
        _tlUseHistory.set(t, false);
    }

    void promoteToAnchor_(SOID soid, Path path, Trans t) throws SQLException, IOException
    {
        _sfti.addTagFileAndIconIn(SID.anchorOID2storeSID(soid.oid()),
                path.toAbsoluteString(_lrm.absRootAnchor_(path.sid())), t);
    }

    void demoteToRegularFolder_(SOID soid, Path path, Trans t) throws SQLException, IOException
    {
        _sfti.removeTagFileAndIconIn(SID.anchorOID2storeSID(soid.oid()),
                path.toAbsoluteString(_lrm.absRootAnchor_(path.sid())), t);
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

    /**
     * Move the file to the revision history storage area.
     */
    void moveToRev_(LinkedFile f, Trans t) throws IOException
    {
        final LinkedRevFile rev = _revProvider.newLocalRevFile_(f._path, f._f.getAbsolutePath(),
                f._sokid.kidx());
        rev.save_();

        onRollback_(f._f, t, new IRollbackHandler() {
            @Override
            public void rollback_() throws IOException
            {
                if (rev != null) rev.rollback_();
            }
        });

        // wait until commit in case we need to put this file back (as in a delete operation
        // that rolls back). This is an unsubtle limit - more nuanced storage policies will
        // be implemented by the history cleaner.
        if (!_tlUseHistory.get(t)) {
            _tlDel.get(t).add(rev);
        }
    }

    @Override
    public long apply_(IPhysicalPrefix prefix, IPhysicalFile file, boolean wasPresent, long mtime,
            Trans t) throws IOException, SQLException
    {
        if (l.isDebugEnabled()) l.debug("apply " + prefix + "->" + file);

        final LinkedFile f = (LinkedFile) file;
        final LinkedPrefix p = (LinkedPrefix) prefix;

        if (wasPresent) moveToRev_(f, t);

        LinkedStorage.moveWithRollback_(p._f, f._f, t);
        f._f.setLastModified(mtime);
        f.created_(t);

        return f._f.lastModified();
    }

    static String makeAuxFilePrefix(SIndex sidx)
    {
        return Integer.toString(sidx.getInt()) + '-';
    }

    static String makeAuxFileName(SOKID sokid)
    {
        return makeAuxFilePrefix(sokid.sidx()) + sokid.oid().toStringFormal() + '-' +
                sokid.kidx();
    }

    public static interface IRollbackHandler
    {
        void rollback_() throws IOException;
    }

    /**
     * N.B. this must be called *after* the actual file operation is done
     */
    public static void onRollback_(final InjectableFile f, Trans t, final IRollbackHandler rh)
    {
        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                try {
                    rh.rollback_();
                } catch (IOException e) {
                    SystemUtil.fatal(
                            "db/fs inconsistent on " + (f.isDirectory() ? "dir " : "file ") +
                                    f.getAbsolutePath() + ": " + Util.e(e));
                }
            }
        });
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

    public static void moveWithRollback_(
            final InjectableFile from, final InjectableFile to, Trans t)
            throws IOException
    {
        from.moveInSameFileSystem(to);

        LinkedStorage.onRollback_(from, t, new IRollbackHandler() {
            @Override
            public void rollback_() throws IOException
            {
                to.moveInSameFileSystem(from);
            }
        });
    }
}
