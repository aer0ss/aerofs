/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.lib.Path;

public class EICreateUrl extends AbstractEBIMC
{
    public final Path _path;
    public String _link;

    public EICreateUrl(Path path)
    {
        super(Core.imce());
        _path = path;
    }

    public void setResult(String link)
    {
        _link = link;
    }

    public String link()
    {
        return _link;
    }
}