package com.aerofs.daemon.rest;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.rest.event.EICreateObject;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.daemon.rest.event.EIObjectInfo;
import com.aerofs.daemon.rest.event.EIMoveObject;
import com.aerofs.daemon.rest.handler.HdCreateObject;
import com.aerofs.daemon.rest.handler.HdFileContent;
import com.aerofs.daemon.rest.handler.HdListChildren;
import com.aerofs.daemon.rest.handler.HdObjectInfo;
import com.aerofs.daemon.rest.handler.HdMoveObject;
import com.google.inject.Inject;

public class RestCoreEventHandlerRegistar implements ICoreEventHandlerRegistrar
{
    private final HdListChildren _hdListChildren;
    private final HdObjectInfo _hdObjectInfo;
    private final HdFileContent _hdFileContent;
    private final HdCreateObject _hdCreateObject;
    private final HdMoveObject _hdMoveObject;

    @Inject
    RestCoreEventHandlerRegistar(HdListChildren hdListChildren,
            HdObjectInfo hdObjectInfo,
            HdCreateObject hdCreateObject, HdMoveObject hdMoveObject,
            HdFileContent hdFileContent)
    {
        _hdListChildren = hdListChildren;
        _hdObjectInfo = hdObjectInfo;
        _hdCreateObject = hdCreateObject;
        _hdMoveObject = hdMoveObject;
        _hdFileContent = hdFileContent;
    }

    @Override
    public void registerHandlers_(CoreEventDispatcher disp)
    {
        disp
                .setHandler_(EIListChildren.class, _hdListChildren)
                .setHandler_(EIObjectInfo.class, _hdObjectInfo)
                .setHandler_(EICreateObject.class, _hdCreateObject)
                .setHandler_(EIMoveObject.class, _hdMoveObject)
                .setHandler_(EIFileContent.class, _hdFileContent)
        ;
    }
}
