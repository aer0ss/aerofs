/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public class EIImportFile extends AbstractEBIMC
{
    public final Path _dest;
    public final String _source;

    public EIImportFile(Path destination, String source, IIMCExecutor imce)
    {
        super(imce);
        _dest = destination;
        _source = source;
    }
}
