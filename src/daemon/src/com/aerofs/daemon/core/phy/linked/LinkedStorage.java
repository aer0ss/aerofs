package com.aerofs.daemon.core.phy.linked;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Param;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.linker.IgnoreList;
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
import com.aerofs.lib.Param.AuxFolder;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;

public class LinkedStorage implements IPhysicalStorage
{
    private static Logger l = Util.l(LinkedStorage.class);

    private InjectableFile.Factory _factFile;
    private IFIDMaintainer.Factory _factFIDMan;
    private CfgAbsRootAnchor _cfgAbsRootAnchor;
    private CfgAbsAuxRoot _cfgAbsAuxRoot;
    private IgnoreList _il;
    private LinkedRevProvider _revProvider;
    private SharedFolderTagFileAndIcon _sfti;

    @Inject
    public void inject_(InjectableFile.Factory factFile,
            IFIDMaintainer.Factory factFIDMan,
            CfgAbsRootAnchor cfgAbsRootAnchor,
            CfgAbsAuxRoot cfgAbsAuxRoot,
            IgnoreList il,
            LinkedRevProvider revProvider,
            SharedFolderTagFileAndIcon sfti)
    {
        _factFile = factFile;
        _factFIDMan = factFIDMan;
        _cfgAbsRootAnchor = cfgAbsRootAnchor;
        _cfgAbsAuxRoot = cfgAbsAuxRoot;
        _il = il;
        _revProvider = revProvider;
        _sfti = sfti;
    }

    @Override
    public void init_() throws IOException
    {
        // create aux folders. other codes assume these folders already exist.
        for (AuxFolder af : Param.AuxFolder.values()) {
            InjectableFile f = _factFile.create(Util.join(_cfgAbsAuxRoot.get(), af._name));
            if (!f.exists()) f.mkdirs();
        }

        _revProvider.init_(_cfgAbsAuxRoot.get());
    }

    @Override
    public IPhysicalFile newFile_(SOKID sokid, Path path)
    {
        return new LinkedFile(_cfgAbsRootAnchor, _factFile, _factFIDMan, this, sokid, path,
                _cfgAbsAuxRoot.get());
    }

    @Override
    public IPhysicalFolder newFolder_(SOID soid, Path path)
    {
        return new LinkedFolder(_cfgAbsRootAnchor, _factFile, _factFIDMan, _il, soid, path);
    }

    @Override
    public IPhysicalPrefix newPrefix_(SOCKID k)
    {
        return new LinkedPrefix(_factFile, k, _cfgAbsAuxRoot.get());
    }

    @Override
    public IPhysicalRevProvider getRevProvider()
    {
        return _revProvider;
    }

    @Override
    public void createStore_(SIndex sidx, Path path, Trans t) throws IOException, SQLException
    {
        // root store doesn't need tag files and overlay icons.
        if (!path.isEmpty()) _sfti.addTagFileAndIcon(sidx, path, t);
    }

    @Override
    public void deleteStore_(SIndex sidx, Path path, PhysicalOp op, Trans t) throws IOException
    {
        // delete tag and icon only if actual physical operations are required for the deletion.
        // the test is not needed for store creation because the tag and icon have to be created
        // in all the cases.
        if (op == PhysicalOp.APPLY) _sfti.deleteTagFileAndIcon(sidx, path, t);

        // delete aux files other than revision files. no need to register for deletion rollback
        // since these files are not important.
        String prefix = makeAuxFilePrefix(sidx);
        deleteFiles_(Param.AuxFolder.CONFLICT, prefix);
        deleteFiles_(Param.AuxFolder.PREFIX, prefix);
    }

    private void deleteFiles_(AuxFolder af, final String prefix) throws IOException
    {
        InjectableFile folder = _factFile.create(Util.join(_cfgAbsAuxRoot.get(), af._name));
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

    public static void moveWithRollback_(final InjectableFile from, final InjectableFile to, Trans t)
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
