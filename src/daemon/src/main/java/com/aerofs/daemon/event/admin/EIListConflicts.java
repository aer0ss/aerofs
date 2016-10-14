package com.aerofs.daemon.event.admin;

import java.util.List;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.proto.Ritual.ListConflictsReply.ConflictedPath;

public class EIListConflicts extends AbstractEBIMC
{

    public EIListConflicts(IIMCExecutor imce)
    {
        super(imce);
    }

    public List<ConflictedPath> _pathList;

    public void setResult_(List<ConflictedPath> pathList)
    {
        _pathList = pathList;
    }
}
