package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.linker.IgnoreList;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.linked.LinkedFolder;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile.Factory;

public class TestLinkedFolder extends AbstractTestLinkedObject<IPhysicalFolder>
{
    String pathAux = "baz";

    @Override
    protected IPhysicalFolder createPhysicalObject(CfgAbsRootAnchor cfgAbsRootAnchor,
            Factory factFile, InjectableDriver dr, DirectoryService ds, IgnoreList il,
            SOKID sokid, Path path)
    {
        return new LinkedFolder(cfgAbsRootAnchor, factFile, new IFIDMaintainer.Factory(dr, ds),
                il, sokid.soid(), path);
    }
}
