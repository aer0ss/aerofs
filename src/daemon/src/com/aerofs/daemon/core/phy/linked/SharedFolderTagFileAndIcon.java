package com.aerofs.daemon.core.phy.linked;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.sql.SQLException;

import javax.annotation.Nullable;
import javax.inject.Inject;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.UniqueID.ExInvalidID;
import com.aerofs.daemon.core.first.FirstLaunch.AccessibleStores;
import com.aerofs.daemon.core.phy.linked.linker.PathCombo;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.base.id.SID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
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
    private final IMapSID2SIndex _sid2sidx;
    private final InjectableFile.Factory _factFile;
    private final AccessibleStores _accessibleStoresOnFirstLaunch;

    @Inject
    public SharedFolderTagFileAndIcon(InjectableDriver dr, InjectableFile.Factory factFile,
            IMapSID2SIndex sid2sidx, AccessibleStores accessibleStoresOnFirstLaunch)
    {
        _dr = dr;
        _sid2sidx = sid2sidx;
        _factFile = factFile;
        _accessibleStoresOnFirstLaunch = accessibleStoresOnFirstLaunch;
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

    private void addTagFileAndIconIn(SID sid, String absPath) throws IOException
    {
        l.info("add sf tag for {} in {}", sid, absPath);

        if (!OSUtil.isLinux()) {
            _dr.setFolderIcon(absPath, OSUtil.getIconPath(Icon.SharedFolder));
        }

        String absPathTagFile = Util.join(absPath, LibParam.SHARED_FOLDER_TAG);

        // delete the tag file first in case the user created a folder in place of the tag file
        // which would cause writing the file to fail.
        deleteTagFile(absPathTagFile);

        PrintStream ps = new PrintStream(absPathTagFile);
        try {
            // this may be called during store creation, when the store might not be fully
            // initialized locally.
            ps.print(sid.toStringFormal());
        } finally {
            ps.close();
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

    public @Nullable OID getOIDForAnchor_(PathCombo pc, Trans t) throws SQLException, IOException
    {
        InjectableFile tag = _factFile.create(pc._absPath, LibParam.SHARED_FOLDER_TAG);
        if (!tag.exists()) return null;

        SID sid = tag.isFile() ? sidFromTagFile(tag.getAbsolutePath()) : null;
        if (sid != null && isAccessibleAndAbsent_(sid)) {
            l.info("first-launch: valid tag found " + sid + " " + pc._path);
            return SID.storeSID2anchorOID(sid);
        } else {
            l.info("first-launch: invalid tag " + sid + " " + pc._path);
            deleteTagFileAndIconIn(pc._absPath);
            return null;
        }
    }

    public boolean isSharedFolderRoot(InjectableFile dir, SID sid) throws IOException
    {
        InjectableFile tag = _factFile.create(dir, LibParam.SHARED_FOLDER_TAG);
        return tag.exists() && tag.isFile() && sid.equals(sidFromTagFile(tag.getAbsolutePath()));
    }

    private boolean isAccessibleAndAbsent_(SID sid) throws SQLException
    {
        // if the store is already known we should not try to create an anchor for it to avoid
        // conflicts (NB: even expelled store must be taken into account as their anchors are still
        // around under a trash folder)
        if (_sid2sidx.getLocalOrAbsentNullable_(sid) != null) return false;

        // the set of accessible stores will be empty outside of the first launch
        return _accessibleStoresOnFirstLaunch.contains(sid);
    }

    /**
     * Retrieve SID from tag file at the root of an existing shared folder
     * @return SID if valid tag file found, null otherwise
     */
    private static @Nullable SID sidFromTagFile(String absPathTagFile)
    {
        try {
            FileInputStream in = new FileInputStream(absPathTagFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            try {
                String line = reader.readLine();
                return line != null ? new SID(line, 0, line.length()) : null;
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            return null;
        } catch (ExFormatError e) {
            return null;
        } catch (ExInvalidID e) {
            return null;
        }
    }
}
