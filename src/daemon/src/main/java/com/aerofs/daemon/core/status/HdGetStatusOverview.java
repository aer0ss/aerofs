/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.status;

import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.event.status.EIGetStatusOverview;
import com.aerofs.lib.Path;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import java.util.List;

public class HdGetStatusOverview extends AbstractHdIMC<EIGetStatusOverview>
{
    private final PathStatus _ps;

    @Inject
    public HdGetStatusOverview(PathStatus ps)
    {
        _ps = ps;
    }

    @Override
    protected void handleThrows_(EIGetStatusOverview ev) throws Exception
    {
        List<PBPathStatus> statusOverviews = Lists.newArrayList();
        for (Path path : ev.getPathList()) {
            statusOverviews.add(_ps.getStatus_(path));
        }
        ev.setResult_(statusOverviews);
    }
}
