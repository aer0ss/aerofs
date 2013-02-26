/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

import javax.annotation.Nullable;

public class EIDeleteRevision extends AbstractEBIMC
{
    public final Path _path;
    public final @Nullable byte[] _index;

    public EIDeleteRevision(IIMCExecutor imce, Path path, @Nullable byte[] index)
    {
        super(imce);
        _path = path;
        _index = index;
    }
}
