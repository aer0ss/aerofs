package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.lib.id.KIndex;

public class TestLinkedFolder extends AbstractTestLinkedObject<IPhysicalFolder>
{
    @Override
    protected IPhysicalFolder createPhysicalObject(LinkedStorage s, ResolvedPath path, KIndex kidx)
    {
        return new LinkedFolder(s, path);
    }
}
