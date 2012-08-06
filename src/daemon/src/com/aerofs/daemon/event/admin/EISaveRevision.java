package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.Path;

public class EISaveRevision extends AbstractEBIMC
{
    public final Path _path;
    public final DID _did;
    public final byte[] _index;
    public final String _dest;  // the destination local path

    public EISaveRevision(Path path, DID did, byte[] index, String dest)
    {
        super(Core.imce());
        _path = path;
        _did = did;
        _index = index;
        _dest = dest;
    }
}
