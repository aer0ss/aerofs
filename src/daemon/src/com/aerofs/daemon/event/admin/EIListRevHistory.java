package com.aerofs.daemon.event.admin;

import java.util.Collection;

import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Revision;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public class EIListRevHistory extends AbstractEBIMC {
    private final Path _path;
    private Collection<Revision> _revisions;

    public EIListRevHistory(Path path, IIMCExecutor imce)
    {
        super(imce);
        _path = path;
    }

    public void setResult_(Collection<Revision> revisions)
    {
        _revisions = revisions;
    }

    public Path getPath()
    {
        return _path;
    }

    public Collection<Revision> getRevisions()
    {
        return _revisions;
    }
}
