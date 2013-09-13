package com.aerofs.daemon.rest;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.daemon.rest.event.EIFileInfo;
import com.aerofs.daemon.rest.event.EIFolderInfo;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.daemon.rest.handler.HdFileContent;
import com.aerofs.daemon.rest.handler.HdFileInfo;
import com.aerofs.daemon.rest.handler.HdFolderInfo;
import com.aerofs.daemon.rest.handler.HdListChildren;
import com.google.inject.Inject;

public class RestCoreEventHandlerRegistar implements ICoreEventHandlerRegistrar
{
    HdListChildren _hdListChildren;
    HdFileInfo _hdFileInfo;
    HdFolderInfo _hdFolderInfo;
    HdFileContent _hdFileContent;

    @Inject
    RestCoreEventHandlerRegistar(HdListChildren hdListChildren,
            HdFileInfo hdFileInfo, HdFolderInfo hdFolderInfo, HdFileContent hdFileContent)
    {
        _hdListChildren = hdListChildren;
        _hdFileInfo = hdFileInfo;
        _hdFolderInfo = hdFolderInfo;
        _hdFileContent = hdFileContent;
    }

    @Override
    public void registerHandlers_(CoreEventDispatcher disp)
    {
        disp
                .setHandler_(EIListChildren.class, _hdListChildren)
                .setHandler_(EIFileInfo.class, _hdFileInfo)
                .setHandler_(EIFolderInfo.class, _hdFolderInfo)
                .setHandler_(EIFileContent.class, _hdFileContent)
        ;
    }
}
