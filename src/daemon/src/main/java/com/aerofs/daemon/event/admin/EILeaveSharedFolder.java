/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.lib.Path;

public class EILeaveSharedFolder extends AbstractEBIMC
{
    public final Path _path;

    public EILeaveSharedFolder(Path path)
    {
        super(Core.imce());
        _path = path;
    }
}
