/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.event.status;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.proto.PathStatus.PBPathStatus;

import java.util.Collection;
import java.util.List;

/**
 *
 */
public class EIGetStatusOverview extends AbstractEBIMC
{
    private final List<Path> _pathList;
    public Collection<PBPathStatus> _statusOverviews;

    public EIGetStatusOverview(List<Path> pathList, IIMCExecutor imce)
    {
        super(imce);
        _pathList = pathList;
    }

    public void setResult_(Collection<PBPathStatus> statusOverviews)
    {
        _statusOverviews = statusOverviews;
    }

    public List<Path> getPathList()
    {
        return _pathList;
    }
}
