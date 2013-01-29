/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableFile;

import java.io.IOException;
import java.util.Arrays;

public class BlockExportedFolder
{
    private final BlockStorage _s;
    private final SOID _soid;
    private final Path _path;
    private final InjectableFile.Factory _fileFactory;

    public BlockExportedFolder(BlockStorage s, SOID soid, Path path, InjectableFile.Factory fact)
    {
        _s = s;
        _soid = soid;
        _path = path;
        _fileFactory = fact;
    }

    public InjectableFile asFile()
    {
        return _fileFactory.create(exportedAbsPath());
    }

    public void create_(Trans t)
    {
        // TODO: DF if this folder is actually an anchor, do something to indicate where the
        // other store should go
        //   * Windows: "Where did these files go.txt"
        //   * OSX/Linux: symlink to target store
        final InjectableFile f = asFile();
        t.addListener_(new AbstractTransListener()
        {
            @Override
            public void committed_()
            {
                try {
                    f.ensureDirExists();
                } catch (IOException e) {
                    // Silently fail.
                }
            }
        });
    }
    public void delete_(Trans t)
    {
        final InjectableFile f = asFile();
        t.addListener_(new AbstractTransListener()
        {
            @Override
            public void committed_()
            {
                f.deleteIgnoreErrorRecursively();
            }
        });
    }
    public void move_(BlockExportedFolder target, Trans t)
    {
        // No point in trying to move the folder if there's no folder to move, so quit early
        final InjectableFile source = asFile();
        if (!source.exists()) {
            return;
        }
        final InjectableFile dest = target.asFile();
        t.addListener_(new AbstractTransListener()
        {
            @Override
            public void committed_()
            {
                // There's no guarantee that the destination's parent exists in the autoexport
                // folder, so make sure it exists so we can put it there.
                InjectableFile destParent = dest.getParentFile();
                try {
                    destParent.ensureDirExists();
                } catch (IOException e) {
                    // Fail to move the folder silently if we can't make the target's parent.
                    // Best effort doesn't always succeed.
                    return;
                }
                // Drop the folder in place where it belongs.
                source.moveInSameFileSystemIgnoreError(dest);
            }
        });


    }
    public String exportedAbsPath()
    {
        String storeRoot = _s.storeExportFolder(_soid.sidx());
        // Note: the first element of _path is the SID.toStringFormal().
        // We already take care of that bit in storeExportFolder, so we
        // drop the first path element here.
        String[] elems = _path.elements();
        String relPath = Util.join(Arrays.copyOfRange(elems, 1, elems.length));
        return Util.join(storeRoot, relPath);
    }
}
