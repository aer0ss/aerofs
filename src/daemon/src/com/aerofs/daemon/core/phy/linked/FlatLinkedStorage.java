/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.daemon.core.phy.linked.linker.IgnoreList;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.S;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsDefaultRoot;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.rocklog.RockLog;
import com.google.inject.Inject;

import java.io.IOException;
import java.sql.SQLException;

/**
 * A "flat" variant of {@code LinkedStorage} designed for multiuser configurations
 */
public class FlatLinkedStorage extends LinkedStorage
{
    private final InjectableFile _usersDir;
    private final InjectableFile _sharedDir;
    private final LocalACL _lacl;
    private final IOSUtil _os;

    @Inject
    public FlatLinkedStorage(InjectableFile.Factory factFile,
            IFIDMaintainer.Factory factFIDMan,
            LinkerRootMap lrm,
            IStores stores,
            RepresentabilityHelper rh,
            IMapSIndex2SID sidx2sid,
            CfgAbsRoots cfgAbsRoots,
            CfgAbsDefaultRoot cfgAbsDefaultRoot,
            CfgStoragePolicy cfgStoragePolicy,
            IgnoreList il,
            SharedFolderTagFileAndIcon sfti,
            LocalACL lacl,
            IOSUtil os,
            LinkedRevProvider revProvider,
            RockLog rl,
            CoreScheduler sched)
    {
        super(factFile, factFIDMan, lrm, rh, stores, sidx2sid, cfgAbsRoots,
                cfgStoragePolicy, il, sfti, revProvider, rl, sched);
        _os = os;
        _lacl = lacl;
        _usersDir = _factFile.create(Util.join(cfgAbsDefaultRoot.get(), S.USERS_DIR));
        _sharedDir = _factFile.create(Util.join(cfgAbsDefaultRoot.get(), S.SHARED_DIR));
    }

    @Override
    public void init_() throws IOException, SQLException
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
    public void createStore_(SIndex sidx, SID sid, String name, Trans t)
            throws IOException, SQLException
    {
        // when the user explictly links an external root (as opposed to implictly when the daemon
        // auto-joins a folder) the linking will already be done by the time this method is called
        // and we couldn't do it here anyway because we wouldn't know the correct full path...
        if (_lrm.absRootAnchor_(sid) != null) {
            l.info("explicit linking {} {}", sidx, sid);
            return;
        }

        l.info("create store {} {} {}", sidx, sid.toStringFormal(), name);

        String absPath = ensureStoreRootExists_(sidx, sid, name);
        _sfti.addTagFileAndIconIn(sid, absPath, t);
        _lrm.link_(sid, absPath, t);
    }

    @Override
    public void deleteStore_(SIndex sidx, SID sid, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        _sfti.deleteTagFileAndIconIn(_lrm.absRootAnchor_(sid));
        _lrm.unlink_(sid, t);
    }

    private String ensureStoreRootExists_(SIndex sidx, SID sid, String name) throws IOException
    {
        InjectableFile d = storeRoot_(sidx, sid, name);
        d.ensureDirExists();
        return d.getAbsolutePath();
    }

    private InjectableFile storeRoot_(SIndex sidx, SID sid, String name) throws IOException
    {
        if (sid.isUserRoot()) {
            // root in $defaultAbsRoot/users
            UserID uid = storeOwner_(sidx, sid);
            return _factFile.create(_usersDir, _os.cleanFileName(uid.getString()));
        } else {
            // root in $defaultAbsRoot/shared

            InjectableFile d = _factFile.create(_sharedDir, name);

            while (d.exists()) {
                // dir already exists, only allow if it contains a valid tag file matching the SID
                if (d.isDirectory() && _sfti.isSharedFolderRoot(d, sid)) return d;
                l.info("conflicting folder");
                d = _factFile.create(_sharedDir, Util.nextFileName(d.getName()));
            }
            return d;
        }
    }


    public UserID storeOwner_(SIndex sidx, SID sid)
    {
        // Loop over ACL entries, find non-self user, make folder with that name
        try {
            for (UserID uid : _lacl.get_(sidx).keySet()) {
                if (!uid.isTeamServerID()) {
                    assert SID.rootSID(uid).equals(sid);
                    return uid;
                }
            }
        } catch (SQLException e) {
            // LocalACL.get_ shouldn't throw here - it shouldn't be possible to receive events
            // about a store for which we have no ACL.
            SystemUtil.fatal("lacl get " + sidx + " " + sid + " " + e);
        }
        throw new AssertionError("store not accessible " + sidx + " " + sid);
    }

    @Override
    void promoteToAnchor_(SID sid, String path, Trans t) throws SQLException, IOException
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
    void demoteToRegularFolder_(SID sid, String path, Trans t) throws SQLException, IOException
    {
        throw new AssertionError("demotion not supported: " + sid + " " + path);
    }
}
