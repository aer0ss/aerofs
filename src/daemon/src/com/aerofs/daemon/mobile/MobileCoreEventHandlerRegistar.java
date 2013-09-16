/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.mobile;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.google.inject.Inject;

public class MobileCoreEventHandlerRegistar implements ICoreEventHandlerRegistrar
{
    final private HdDownloadPacket _hdDownloadPacket;
    final private HdGetChildrenAttr _hdGetChildrenAttr;

    @Inject
    MobileCoreEventHandlerRegistar(
            HdDownloadPacket hdDownloadPacket,
            HdGetChildrenAttr hdGetChildrenAttr)
    {
        _hdDownloadPacket = hdDownloadPacket;
        _hdGetChildrenAttr = hdGetChildrenAttr;
    }

    @Override
    public void registerHandlers_(CoreEventDispatcher disp)
    {
        disp
                .setHandler_(EIGetChildrenAttr.class, _hdGetChildrenAttr)
                .setHandler_(EIDownloadPacket.class, _hdDownloadPacket)
        ;
    }
}
