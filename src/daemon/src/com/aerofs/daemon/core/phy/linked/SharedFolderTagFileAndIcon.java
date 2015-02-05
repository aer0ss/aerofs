package com.aerofs.daemon.core.phy.linked;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.sql.SQLException;

import javax.annotation.Nullable;
import javax.inject.Inject;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.UniqueID.ExInvalidID;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.first_launch.FirstLaunch.AccessibleStores;
import com.aerofs.daemon.core.phy.linked.linker.PathCombo;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.base.id.SID;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.Icon;
import org.slf4j.Logger;

/**
 * This class adds and removes tag files and overlay icons for shared folders
 *
 * It is also used by MightCreate to detect shared folders on the first scan upon a reinstall.
 * When valid tag files are found pointing to accessible store, an anchor should be created instead
 * of a regular folder. Invalid tag files should conversely be removed.
 */
public class SharedFolderTagFileAndIcon
{
    private static final Logger l = Loggers.getLogger(SharedFolderTagFileAndIcon.class);

    private final InjectableDriver _dr;
    private final IMetaDatabase _mdb;
    private final IMapSID2SIndex _sid2sidx;
    private final IMapSIndex2SID _sidx2sid;
    private final InjectableFile.Factory _factFile;
    private final AccessibleStores _accessibleStoresOnFirstLaunch;
    private final IOSUtil _osutil;
    private final CfgRootSID _rootSID;
    private final LocalACL _lacl;

    @Inject
    public SharedFolderTagFileAndIcon(InjectableDriver dr, InjectableFile.Factory factFile,
            IMetaDatabase mdb, AccessibleStores accessibleStoresOnFirstLaunch, IOSUtil osutil,
            CfgRootSID rootSID, IMapSID2SIndex sid2sidx, IMapSIndex2SID sidx2sid, LocalACL lacl)
    {
        _dr = dr;
        _mdb = mdb;
        _factFile = factFile;
        _accessibleStoresOnFirstLaunch = accessibleStoresOnFirstLaunch;
        _osutil = osutil;
        _rootSID = rootSID;
        _sid2sidx = sid2sidx;
        _sidx2sid = sidx2sid;
        _lacl = lacl;
    }

    /**
     * Add the tag file and overlay icon for a shared folder, assuming path is the root of the
     * shared store identified by {@code sidx}.
     */
    public void addTagFileAndIconIn(SID sid, final String absPath, Trans t)
            throws IOException, SQLException
    {
        addTagFileAndIconIn(sid, absPath);

        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                try {
                    deleteTagFileAndIconIn(absPath);
                } catch (IOException e) {
                    SystemUtil.fatal("unrecoverable: " + Util.e(e));
                }
            }
        });
    }

    /**
     * Delete the tag file and overlay icon for a shared folder, assuming path is the root of the
     * shared store identified by {@code sidx}.
     */
    public void removeTagFileAndIconIn(final SID sid, final String absPath, Trans t)
            throws IOException
    {
        deleteTagFileAndIconIn(absPath);

        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                try {
                    addTagFileAndIconIn(sid, absPath);
                } catch (Exception e) {
                    SystemUtil.fatal("unrecoverable: " + Util.e(e));
                }
            }
        });
    }

    public void addTagFileAndIconIn(SID sid, String absPath) throws IOException
    {
        l.info("add sf tag for {} in {}", sid, absPath);

        if (!OSUtil.isLinux()) {
            _dr.setFolderIcon(absPath, _osutil.getIconPath(Icon.SharedFolder));
        }

        String absPathTagFile = Util.join(absPath, LibParam.SHARED_FOLDER_TAG);

        // delete the tag file first in case the user created a folder in place of the tag file
        // which would cause writing the file to fail.
        deleteTagFile(absPathTagFile);

        try (PrintStream ps = new PrintStream(absPathTagFile)) {
            // this may be called during store creation, when the store might not be fully
            // initialized locally.
            ps.print(sid.toStringFormal());
        }

        OSUtil.get().markHiddenSystemFile(absPathTagFile);
    }

    public void deleteTagFileAndIconIn(String absPath) throws IOException
    {
        l.info("del sf tag in {}", absPath);
        _dr.setFolderIcon(absPath, "");

        deleteTagFile(Util.join(absPath, LibParam.SHARED_FOLDER_TAG));
    }

    private void deleteTagFile(String absPathTagFile) throws IOException
    {
        // use recursive deletion in case the user created a folder in place of the tag file
        _factFile.create(absPathTagFile).deleteOrThrowIfExistRecursively();
    }

    public @Nullable OID getOIDForAnchor_(SIndex sidx, PathCombo pc, Trans t)
            throws SQLException, IOException
    {
        InjectableFile tag = _factFile.create(pc._absPath, LibParam.SHARED_FOLDER_TAG);
        if (!tag.exists()) return null;

        SID sid = tag.isFile() ? sidFromTagFile(tag.getAbsolutePath()) : null;
        if (sid != null && canRestoreAnchor_(sidx, sid)) {
            l.info("first-launch: valid tag found " + sid + " " + pc._path);
            return SID.storeSID2anchorOID(sid);
        } else {
            l.info("first-launch: invalid tag " + sid + " " + pc._path);
            deleteTagFileAndIconIn(pc._absPath);
            return null;
        }
    }

    public void fixTagFileIfNeeded_(SID sid, String absPath) throws IOException
    {
        if (!sid.equals(_rootSID.get()) && !isSharedFolderRoot(sid, absPath)) {
            addTagFileAndIconIn(sid, absPath);
        }
    }

    // non-static for mocking...
    public boolean isSharedFolderRoot(SID sid, String absPath)
    {
        return isStoreRoot(sid, absPath);
    }

    public boolean isSharedFolderRoot(SID sid, InjectableFile dir)
    {
        return isStoreRoot(sid, dir.getAbsolutePath());
    }

    public static boolean isStoreRoot(SID sid, String absPath)
    {
        File tag = new File(absPath, LibParam.SHARED_FOLDER_TAG);
        return tag.exists() && tag.isFile() && sid.equals(sidFromTagFile(tag.getAbsolutePath()));
    }

    private boolean canRestoreAnchor_(SIndex parent, SID sid) throws SQLException
    {
        if (sid.isUserRoot()) {
            l.warn("cannot create anchor for user root {} {}", parent, sid);
            return false;
        }

        if (!_sidx2sid.get_(parent).isUserRoot()) {
            l.warn("cannot create nested anchor {} {}", parent, sid);
            return false;
        }

        if (!isAccessible_(sid)) {
            l.warn("cannot restore anchor without acl {} {}", parent, sid);
            return false;
        }

        // if the store is already known we should not try to create an anchor for it to avoid
        // conflicts (NB: even expelled store must be taken into account as their anchors are still
        // around under a trash folder)
        return _mdb.getOA_(new SOID(parent, SID.storeSID2anchorOID(sid))) == null;
    }

    private boolean isAccessible_(SID sid) throws SQLException
    {
        if (_accessibleStoresOnFirstLaunch.contains(sid)) return true;
        SIndex sidx = _sid2sidx.getLocalOrAbsentNullable_(sid);
        return sidx != null && !_lacl.get_(sidx).isEmpty();
    }

    /**
     * Retrieve SID from tag file at the root of an existing shared folder
     * @return SID if valid tag file found, null otherwise
     */
    private static @Nullable SID sidFromTagFile(String absPathTagFile)
    {
        try {
            FileInputStream in = new FileInputStream(absPathTagFile);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line = reader.readLine();
                return line != null ? new SID(line, 0, line.length()) : null;
            }
        } catch (IOException e) {
            return null;
        } catch (ExInvalidID e) {
            return null;
        }
    }
}
