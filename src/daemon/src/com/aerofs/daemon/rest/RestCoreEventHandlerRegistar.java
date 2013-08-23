package com.aerofs.daemon.rest;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.rest.event.EIDeleteFile;
import com.aerofs.daemon.rest.event.EIDeleteFolder;
import com.aerofs.daemon.rest.event.EIFileInfo;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.daemon.rest.handler.HdDeleteFile;
import com.aerofs.daemon.rest.handler.HdDeleteFolder;
import com.aerofs.daemon.rest.handler.HdFileInfo;
import com.aerofs.daemon.rest.handler.HdListChildren;
import com.google.inject.Inject;

public class RestCoreEventHandlerRegistar implements ICoreEventHandlerRegistrar
{
    HdListChildren _hdListChildren;
    HdDeleteFolder _hdDeleteFolder;
    HdFileInfo _hdFileInfo;
    HdDeleteFile _hdDeleteFile;

    @Inject
    RestCoreEventHandlerRegistar(HdListChildren hdListChildren, HdDeleteFolder hdDeleteFolder,
            HdFileInfo hdFileInfo, HdDeleteFile hdDeleteFile)
    {
        _hdListChildren = hdListChildren;
        _hdDeleteFolder = hdDeleteFolder;
        _hdFileInfo = hdFileInfo;
        _hdDeleteFile = hdDeleteFile;
    }

    @Override
    public void registerHandlers_(CoreEventDispatcher disp)
    {
        disp
                .setHandler_(EIListChildren.class, _hdListChildren)
                .setHandler_(EIDeleteFolder.class, _hdDeleteFolder)
                .setHandler_(EIFileInfo.class, _hdFileInfo)
                .setHandler_(EIDeleteFile.class, _hdDeleteFile)
        ;
    }
}
