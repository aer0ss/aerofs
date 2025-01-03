package com.aerofs.daemon.rest;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.rest.event.EICreateObject;
import com.aerofs.daemon.rest.event.EIDeleteObject;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.daemon.rest.event.EIFileUpload;
import com.aerofs.daemon.rest.event.EIObjectInfo;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.daemon.rest.event.EIMoveObject;
import com.aerofs.daemon.rest.event.EIObjectPath;
import com.aerofs.daemon.rest.handler.HdCreateObject;
import com.aerofs.daemon.rest.handler.HdDeleteObject;
import com.aerofs.daemon.rest.handler.HdFileContent;
import com.aerofs.daemon.rest.handler.HdFileUpload;
import com.aerofs.daemon.rest.handler.HdObjectInfo;
import com.aerofs.daemon.rest.handler.HdListChildren;
import com.aerofs.daemon.rest.handler.HdMoveObject;
import com.aerofs.daemon.rest.handler.HdObjectPath;
import com.google.inject.Inject;

public class RestCoreEventHandlerRegistar implements ICoreEventHandlerRegistrar
{
    @Inject private HdListChildren _hdListChildren;
    @Inject private HdObjectInfo _hdObjectInfo;
    @Inject private HdFileContent _hdFileContent;
    @Inject private HdCreateObject _hdCreateObject;
    @Inject private HdMoveObject _hdMoveObject;
    @Inject private HdDeleteObject _hdDeleteObject;
    @Inject private HdFileUpload _hdFileUpload;
    @Inject private HdObjectPath _hdObjectPath;

    @Override
    public void registerHandlers_(CoreEventDispatcher disp)
    {
        disp
                .setHandler_(EIListChildren.class, _hdListChildren)
                .setHandler_(EIObjectInfo.class, _hdObjectInfo)
                .setHandler_(EICreateObject.class, _hdCreateObject)
                .setHandler_(EIMoveObject.class, _hdMoveObject)
                .setHandler_(EIDeleteObject.class, _hdDeleteObject)
                .setHandler_(EIFileContent.class, _hdFileContent)
                .setHandler_(EIFileUpload.class, _hdFileUpload)
                .setHandler_(EIObjectPath.class, _hdObjectPath)
        ;
    }
}
