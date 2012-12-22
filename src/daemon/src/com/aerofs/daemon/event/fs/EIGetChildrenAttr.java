package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.base.id.UserID;

import java.util.List;

public class EIGetChildrenAttr extends AbstractEIFS
{
    public final Path _path;
    public List<OA> _oas;

    public EIGetChildrenAttr(UserID user, Path path, IIMCExecutor imce)
    {
        super(user, imce);
        _path = path;
    }

    public void setResult_(List<OA> oas)
    {
        _oas = oas;
    }
}
