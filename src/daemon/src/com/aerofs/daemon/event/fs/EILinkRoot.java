/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.fs;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;

/**
 * Event that links unlinked external shared folder.
 */
public class EILinkRoot extends AbstractEBIMC
{
    public final String _path;
    public final SID _sid;

    public EILinkRoot(String path, SID sid)
    {
        super(Core.imce());
        _path = path;
        _sid = sid;
    }
}
