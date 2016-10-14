package com.aerofs.daemon.event.admin;

import java.util.Collection;

import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Child;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public class EIListRevChildren extends AbstractEBIMC {
    private final Path _path;
    private Collection<Child> _children;

    public EIListRevChildren(Path path, IIMCExecutor imce)
    {
        super(imce);
        _path = path;
    }

    public void setResult_(Collection<Child> children)
    {
        _children = children;
    }

    public Path getPath()
    {
        return _path;
    }

    public Collection<Child> getChildren()
    {
        return _children;
    }
}
