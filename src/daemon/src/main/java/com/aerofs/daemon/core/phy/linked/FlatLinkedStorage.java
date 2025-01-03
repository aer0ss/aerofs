/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.phy.linked.db.HistoryDatabase;
import com.aerofs.daemon.core.phy.linked.linker.HashQueue;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.linked.FileSystemProber.ProbeException;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.daemon.core.phy.linked.linker.IgnoreList;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.*;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.cfg.*;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.IOSUtil;
import com.google.inject.Inject;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * A "flat" variant of {@code LinkedStorage} designed for multiuser configurations.
 *
 * TODO (WW) Inheriting a concrete class is generally a dangerous practice. It can easily cause
 * violation of the Liskov substitution principle. Use composition instead.
 */
public class FlatLinkedStorage extends LinkedStorage
{
    private final InjectableFile _usersDir;
    private final InjectableFile _sharedDir;
    private final LocalACL _lacl;
    private final IOSUtil _os;

    @Inject
    public FlatLinkedStorage(InjectableFile.Factory factFile, IFIDMaintainer.Factory factFIDMan,
                             LinkerRootMap lrm, InjectableDriver dr, StoreHierarchy stores,
                             RepresentabilityHelper rh, IMapSIndex2SID sidx2sid, IgnoreList il,
                             CfgAbsRoots cfgAbsRoots, CfgAbsDefaultRoot cfgAbsDefaultRoot,
                             CfgStoragePolicy cfgStoragePolicy, SharedFolderTagFileAndIcon sfti,
                             LocalACL lacl, IOSUtil os, LinkedStagingArea sa, HashQueue hq,
                             LinkedRevProvider revProvider, HistoryDatabase hdb, CoreScheduler sched)
    {
        super(factFile, factFIDMan, lrm, os, dr, rh, stores, sidx2sid, cfgAbsRoots,
                cfgStoragePolicy, il, sfti, sa, hq, revProvider, hdb, sched);
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
        // this is to avoid conflicts with SanityPoller in the GUI
        if (_usersDir.getParentFile().exists()) {
            _usersDir.ensureDirExists();
            _sharedDir.ensureDirExists();

            // Probe the default abs root under which stores will be auto-joined.
            // If we don't enforce a restriction here, then auto-join will just silently fail,
            // causing an apparent no-sync that the user will blame us for.
            try {
                new FileSystemProber(_factFile, _os)
                        .probe(Util.join(_usersDir.getParent(), ClientParam.AUXROOT_NAME + ".ts"));
            } catch (ProbeException e) {
                ExitCode.FILESYSTEM_PROBE_FAILED.exit();
            }
        }
    }

    @Override
    public IPhysicalFile newFile_(ResolvedPath path, KIndex kidx) throws SQLException
    {
        return super.newFile_(shortest_(path), kidx);
    }

    @Override
    public IPhysicalFolder newFolder_(ResolvedPath path) throws SQLException
    {
        return super.newFolder_(shortest_(path));
    }

    /**
     * ResolvedPath that start from a user root and reaches into a shared folder
     * need to be shortened to only include the fragment starting at the innermost
     * root
     */
    private ResolvedPath shortest_(ResolvedPath path)
    {
        if (path.isEmpty()) return path;

        SIndex sidx = path.soid().sidx();
        SID sid = _sidx2sid.get_(sidx);
        if (sid.equals(path.sid())) return path;

        final int n = path.soids.size();
        int i = n;
        while (--i >= 0) {
            if (!path.soids.get(i).sidx().equals(sidx)) break;
        }
        ResolvedPath r = new ResolvedPath(sid,
                path.soids.subList(i + 1, n),
                Arrays.asList(path.elements()).subList(i + 1, n));
        l.debug("shortened {} -> {}", path, r);
        return r;
    }

    @Override
    public void createStore_(SIndex sidx, SID sid, String name, Trans t)
            throws IOException, SQLException
    {
        // when the user explicitly links an external root (as opposed to implicitly when the daemon
        // auto-joins a folder) the linking will already be done by the time this method is called
        // and we couldn't do it here anyway because we wouldn't know the correct full path...
        if (_lrm.absRootAnchor_(sid) != null) {
            l.info("explicit linking {} {}", sidx, sid);
            return;
        }

        l.info("create store {} {} {}", sidx, sid.toStringFormal(), name);

        String absPath = ensureStoreRootExists_(sidx, sid, name, t);
        _sfti.addTagFileAndIconIn(sid, absPath, t);
        _lrm.link_(sid, absPath, t);
    }

    @Override
    public void deleteStore_(SID physicalRoot, SIndex sidx, SID sid, Trans t)
            throws IOException, SQLException
    {
        _lrm.unlink_(sid, t);
    }

    private String ensureStoreRootExists_(SIndex sidx, SID sid, String name, Trans t)
            throws IOException
    {
        final InjectableFile d = storeRoot_(sidx, sid, name);
        if (!d.exists()) {
            d.mkdirs();
            t.addListener_(new AbstractTransListener() {
                @Override
                public void aborted_()
                {
                    d.deleteIgnoreError();
                }
            });
        }
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

            InjectableFile d = _factFile.create(_sharedDir, _os.cleanFileName(name));

            while (d.exists()) {
                // dir already exists, only allow if either:
                //   - it contains a valid tag file matching the SID
                //   - it doesn't contain any tag file
                // relaxing the tag file requirement is useful to prevent unexpected duplication
                // in some corner cases
                if (d.isDirectory() && _sfti.isUsableSharedFolderRoot(sid, d)) return d;
                l.info("conflicting folder");
                d = _factFile.create(_sharedDir, FileUtil.nextFileName(d.getName()));
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
        super.promoteToAnchor_(sid, path, t);
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
