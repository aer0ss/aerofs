/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.injectable.InjectableFile.Factory;
import org.slf4j.Logger;

import java.util.Arrays;

public class BlockExportedFile
{

    private final BlockStorage _s;
    private final SOKID _sokid;
    private final Path _path;
    private final Factory _fileFactory;
    private static Logger l = Loggers.getLogger(BlockExportedFile.class);

    public BlockExportedFile(BlockStorage s, SOKID sokid, Path path, Factory fileFactory)
    {
        _s = s;
        _sokid = sokid;
        _path = path;
        _fileFactory = fileFactory;
    }

    public void delete_(Trans t)
    {
        final InjectableFile f = _fileFactory.create(exportedAbsPath());
        t.addListener_(new AbstractTransListener()
        {
            @Override
            public void committed_()
            {
                l.debug("deleting " + f);
                f.deleteIgnoreErrorRecursively();
            }
        });
    }

    public void move_(BlockExportedFile target, Trans t)
    {
        final InjectableFile source = _fileFactory.create(exportedAbsPath());
        final InjectableFile dest = _fileFactory.create(target.exportedAbsPath());
        t.addListener_(new AbstractTransListener()
        {
            @Override
            public void committed_()
            {
                l.debug("moving " + source + " to " + dest);
                source.moveInSameFileSystemIgnoreError(dest);
            }
        });
    }

    public String exportedAbsPath()
    {
        String storeRoot = _s.storeExportFolder(_sokid.sidx());
        return Util.join(storeRoot, Util.join(_path.elements()));
    }
}
