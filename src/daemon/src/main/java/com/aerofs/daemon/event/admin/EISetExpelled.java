package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.lib.Path;

public class EISetExpelled extends AbstractEBIMC
{
    public final Path _path;
    public final boolean _expelled;

    /**
     * Set a folder or an anchor to be expelled or not. Throw if it's not a folder or anchor.
     */
    public EISetExpelled(Path path, boolean expelled)
    {
        super(Core.imce());
        _path = path;
        _expelled = expelled;
    }
}
