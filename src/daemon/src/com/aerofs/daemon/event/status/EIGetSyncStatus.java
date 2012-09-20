package com.aerofs.daemon.event.status;

import java.util.Collection;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.proto.Ritual.PBSyncStatus;

public class EIGetSyncStatus extends AbstractEBIMC
{
    private final Path _path;
    public boolean _isServerUp;
    public Collection<PBSyncStatus> _peers;

    public EIGetSyncStatus(Path path, IIMCExecutor imce)
    {
        super(imce);
        _path = path;
    }

    public void setResult_(boolean isServerUp, Collection<PBSyncStatus> peers)
    {
        _isServerUp = isServerUp;
        _peers = peers;
    }

    public Path getPath()
    {
        return _path;
    }
}
