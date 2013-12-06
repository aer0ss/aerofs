package com.aerofs.daemon.rest;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.daemon.rest.event.EIObjectInfo;
import com.aerofs.daemon.rest.handler.HdFileContent;
import com.aerofs.daemon.rest.handler.HdListChildren;
import com.aerofs.daemon.rest.handler.HdObjectInfo;
import com.google.inject.Inject;

public class RestCoreEventHandlerRegistar implements ICoreEventHandlerRegistrar
{
    HdListChildren _hdListChildren;
    HdObjectInfo _hdObjectInfo;
    HdFileContent _hdFileContent;

    @Inject
    RestCoreEventHandlerRegistar(HdListChildren hdListChildren,
            HdObjectInfo hdObjectInfo, HdFileContent hdFileContent)
    {
        _hdListChildren = hdListChildren;
        _hdObjectInfo = hdObjectInfo;
        _hdFileContent = hdFileContent;
    }

    @Override
    public void registerHandlers_(CoreEventDispatcher disp)
    {
        disp
                .setHandler_(EIListChildren.class, _hdListChildren)
                .setHandler_(EIObjectInfo.class, _hdObjectInfo)
                .setHandler_(EIFileContent.class, _hdFileContent)
        ;
    }
}
