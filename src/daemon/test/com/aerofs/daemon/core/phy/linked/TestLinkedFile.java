package com.aerofs.daemon.core.phy.linked;

import org.mockito.Mock;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.linker.IgnoreList;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.linked.LinkedFile;
import com.aerofs.daemon.core.phy.linked.LinkedStorage;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile.Factory;

public class TestLinkedFile extends AbstractTestLinkedObject<IPhysicalFile>
{
    @Mock LinkedStorage s;

    String pathAux = "baz";

    @Override
    protected IPhysicalFile createPhysicalObject(CfgAbsRootAnchor cfgAbsRootAnchor,
            Factory factFile, InjectableDriver dr, DirectoryService ds, IgnoreList il,
            SOKID sokid, Path path)
    {
        return new LinkedFile(cfgAbsRootAnchor, factFile, new IFIDMaintainer.Factory(dr, ds),
                s, sokid, path, pathAux);
    }
}
