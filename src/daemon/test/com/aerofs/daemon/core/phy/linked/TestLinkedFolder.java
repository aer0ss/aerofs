package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOKID;

public class TestLinkedFolder extends AbstractTestLinkedObject<IPhysicalFolder>
{
    @Override
    protected IPhysicalFolder createPhysicalObject(LinkedStorage s, SOKID sokid, Path path)
    {
        return new LinkedFolder(s, sokid.soid(), path);
    }
}
