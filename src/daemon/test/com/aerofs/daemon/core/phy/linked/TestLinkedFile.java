package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOKID;

public class TestLinkedFile extends AbstractTestLinkedObject<IPhysicalFile>
{
    @Override
    protected IPhysicalFile createPhysicalObject(LinkedStorage s, SOKID sokid, Path path)
    {
        return new LinkedFile(s, sokid, path);
    }
}
