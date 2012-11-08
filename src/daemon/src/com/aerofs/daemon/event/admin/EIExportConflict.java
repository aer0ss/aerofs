/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.KIndex;

import java.io.File;

public class EIExportConflict extends AbstractEBIMC
{
    public final Path _path;
    public final KIndex _kidx;
    public File _dst;

    public EIExportConflict(IIMCExecutor imce, Path path, KIndex kidx)
    {
        super(imce);
        _path = path;
        _kidx = kidx;
    }

    public void setResult_(File dst)
    {
        _dst = dst;
    }
}
