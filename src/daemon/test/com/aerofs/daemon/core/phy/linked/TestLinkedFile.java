package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.lib.id.KIndex;

public class TestLinkedFile extends AbstractTestLinkedObject<IPhysicalFile>
{
    @Override
    protected IPhysicalFile createPhysicalObject(LinkedStorage s, ResolvedPath path, KIndex kidx)
    {
        return new LinkedFile(s, path, kidx);
    }
}
