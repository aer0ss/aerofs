package com.aerofs.daemon.core.phy.linked;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;

import javax.inject.Inject;

import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.C;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.Icon;

/**
 * This class adds and removes tag files and overlay icons for shared folders
 */
public class SharedFolderTagFileAndIcon
{
    private final InjectableDriver _dr;
    private final CfgAbsRootAnchor _cfgAbsRootAnchor;
    private final IMapSIndex2SID _sidx2sid;
    private final InjectableFile.Factory _factFile;

    @Inject
    public SharedFolderTagFileAndIcon(InjectableDriver dr, CfgAbsRootAnchor cfgAbsRootAnchor,
            IMapSIndex2SID sidx2sid, InjectableFile.Factory factFile)
    {
        _dr = dr;
        _cfgAbsRootAnchor = cfgAbsRootAnchor;
        _sidx2sid = sidx2sid;
        _factFile = factFile;
    }

    /**
     * Add the tag file and overlay icon for a shared folder, assuming path is the root of the
     * shared store identified by {@code sidx}.
     */
    public void addTagFileAndIcon(SIndex sidx, final Path path, Trans t)
            throws IOException, SQLException
    {
        addTagFileAndIconImpl(sidx, path);

        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                try {
                    deleteTagFileAndIconImp(path);
                } catch (IOException e) {
                    Util.fatal("unrecoverable: " + Util.e(e));
                }
            }
        });
    }

    /**
     * Delete the tag file and overlay icon for a shared folder, assuming path is the root of the
     * shared store identified by {@code sidx}.
     */
    public void deleteTagFileAndIcon(final SIndex sidx, final Path path, Trans t)
            throws IOException
    {
        deleteTagFileAndIconImp(path);

        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                try {
                    addTagFileAndIconImpl(sidx, path);
                } catch (Exception e) {
                    Util.fatal("unrecoverable: " + Util.e(e));
                }
            }
        });
    }

    private void addTagFileAndIconImpl(SIndex sidx, Path path) throws IOException, SQLException
    {
        String absPath = path.toAbsoluteString(_cfgAbsRootAnchor.get());
        if (!OSUtil.isLinux()) {
            _dr.setFolderIcon(absPath, OSUtil.getIconPath(Icon.SharedFolder));
        }

        SID sid = _sidx2sid.getNullable_(sidx);
        // this method may be called during store creation when the sidx hasn't been added to the
        // in-memory store list, therefore we have to fall back to getAbsent.
        if (sid == null) sid = _sidx2sid.getAbsent_(sidx);

        String absPathTagFile = Util.join(absPath, C.SHARED_FOLDER_TAG);

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

    private void deleteTagFileAndIconImp(Path path) throws IOException
    {
        String absPath = path.toAbsoluteString(_cfgAbsRootAnchor.get());
        _dr.setFolderIcon(absPath, "");

        deleteTagFile(Util.join(absPath, C.SHARED_FOLDER_TAG));
    }

    private void deleteTagFile(String absPathTagFile) throws IOException
    {
        // use recursive deletion in case the user created a folder in place of the tag file
        _factFile.create(absPathTagFile).deleteOrThrowIfExistRecursively();
    }
}
