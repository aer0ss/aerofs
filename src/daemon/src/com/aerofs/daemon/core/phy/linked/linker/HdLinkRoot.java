/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.event.fs.EILinkRoot;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;

/**
 * Handler class for LinkRoot. This is used exclusively for linking unlinked external
 * shared folder. It is different from creating an external shared folder.
 */
public class HdLinkRoot extends AbstractHdIMC<EILinkRoot>
{
    private final InjectableFile.Factory _factFile;
    private final LinkRootUtil _lru;

    @Inject
    public HdLinkRoot(InjectableFile.Factory factFile, LinkRootUtil lru)
    {
        _factFile = factFile;
        _lru = lru;
    }

    @Override
    protected void handleThrows_(EILinkRoot ev, Prio prio)
            throws Exception
    {
        InjectableFile f = _factFile.create(ev._path);
        _lru.checkSanity(f);

        l.info("link {} {}", ev._path, ev._sid);
        // Linking the destination
        _lru.linkRoot(f, ev._sid);
        l.info("linked {} {}", ev._path, ev._sid);
    }
}