/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.block.ExportHelper;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.daemon.core.phy.linked.linker.IgnoreList;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsDefaultRoot;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;

import java.io.IOException;
import java.sql.SQLException;

/**
 * A "flat" variant of {@code LinkedStorage} designed for multiuser configurations
 */
public class FlatLinkedStorage extends LinkedStorage
{
    private static final String USERS_DIR = "users";
    private static final String SHARED_DIR = "shared";
    private final InjectableFile _usersDir;
    private final InjectableFile _sharedDir;
    private final ExportHelper _eh;

    @Inject
    public FlatLinkedStorage(InjectableFile.Factory factFile,
            IFIDMaintainer.Factory factFIDMan,
            LinkerRootMap lrm,
            IStores stores,
            IMapSIndex2SID sidx2sid,
            CfgAbsRoots cfgAbsRoots,
            CfgAbsDefaultRoot cfgAbsDefaultRoot,
            CfgStoragePolicy cfgStoragePolicy,
            IgnoreList il,
            SharedFolderTagFileAndIcon sfti,
            ExportHelper eh)
    {
        super(factFile, factFIDMan, lrm, stores, sidx2sid, cfgAbsRoots, cfgStoragePolicy, il, sfti);
        _eh = eh;
        _usersDir = _factFile.create(Util.join(cfgAbsDefaultRoot.get(), USERS_DIR));
        _sharedDir = _factFile.create(Util.join(cfgAbsDefaultRoot.get(), SHARED_DIR));
    }

    @Override
    public void init_() throws IOException
    {
        super.init_();

        // only recreate users/shared dirs if the default root anchor exists
        // this is to avoid conflicts with RootAnchorPoller in the GUI
        if (_usersDir.getParentFile().exists()) {
            _usersDir.ensureDirExists();
            _sharedDir.ensureDirExists();
        }
    }

    @Override
    public void createStore_(SIndex sidx, SID sid, Trans t) throws IOException, SQLException
    {
        l.info("create store {} {}", sidx, sid.toStringFormal());

        // create root
        InjectableFile d = storeRoot_(sidx, sid);
        d.ensureDirExists();

        String absPath = d.getAbsolutePath();
        ensureSaneAuxRoot_(sid, absPath);

        _sfti.addTagFileAndIconIn(sid, absPath, t);
        _lrm.link_(sid, absPath, t);
    }

    @Override
    public void deleteStore_(SIndex sidx, SID sid, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        _lrm.unlink_(sid, t);
    }

    @Override
    protected SIndex rootSIndex_(SIndex sidx) throws SQLException
    {
        return sidx;
    }

    private InjectableFile storeRoot_(SIndex sidx, SID sid)
    {
        if (sid.isUserRoot()) {
            // root in $defaultAbsRoot/users
            UserID uid = _eh.storeOwner_(sidx, sid);
            return _factFile.create(_usersDir, _eh.purifyEmail(uid.getString()));
        } else {
            // root in $defaultAbsRoot/shared
            return _factFile.create(_sharedDir, sid.toStringFormal());
        }
    }

    @Override
    void promoteToAnchor_(SOID soid, Path path, Trans t) throws SQLException, IOException
    {
        // TODO:
        /**
         * It'd be nice if we could do any of the following:
         *   1. set permissions in such a way that the folder can be renamed but no children can
         *   be created under it (unfortunately I don't think that's possible)
         *   2. turn the folder into a symlink to the actual root (needs java 7)
         *   3. set a special icon for the folder to indicate that no content should be added under
         *   it (and any such content will be ignored...)
         *
         * In addition to that we probably should remove watches on that folder (NB: this only
         * matters on Linux where recursive watches need to be explicitly managed)
         */

        // // symlinking sample code
        // SID sid = SID.anchorOID2storeSID(soid.oid());
        // String absAnchorPath = path.toAbsoluteString(_lrm.absRootAnchor_(path.sid()));
        // FileUtil.delete(absAnchorPath);
        // Files.createSymbolicLink(
        //      Paths.get(absPath),
        //      Paths.get(storeRoot_(_sid2sidx.get_(sid), sid).getAbsolutePath()));

    }

    @Override
    void demoteToRegularFolder_(SOID soid, Path path, Trans t) throws SQLException, IOException
    {
        throw new AssertionError("demotion not supported: " + soid + " " + path);
    }
}
