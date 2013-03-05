/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;


import com.aerofs.base.ex.Exceptions;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.ControllerProto.ControllerServiceBlockingStub;
import com.aerofs.proto.ControllerProto.ControllerServiceReactor;
import com.aerofs.proto.ControllerProto.ControllerServiceStub;
import com.aerofs.proto.ControllerProto.ControllerServiceStub.ControllerServiceStubCallbacks;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Controller Client
 *
 * This class is used by the Java UI to communicate with the Controller Service
 * In other words, if you want to call one of the RPCs defined in controller.proto, you want
 * to use this class.
 *
 * It is available as a singleton from UI.controller()
 *
 * Calls are blocking (synchronous) by default. To make asynchronous calls, use the async()
 * method from this class.
 */
public class ControllerClient extends ControllerServiceBlockingStub
{
    private static Callbacks _callbacks = new Callbacks();
    private ControllerServiceStub _asyncStub = new ControllerServiceStub(_callbacks);

    public ControllerClient()
    {
        super(_callbacks);
    }

    /**
     * Returns the async version of the controller client
     * Allows the caller to perform controller operations using the future-based interface
     */
    public ControllerServiceStub async()
    {
        return _asyncStub;
    }

    static class Callbacks implements ControllerServiceStubCallbacks
    {
        ControllerServiceReactor _reactor = new ControllerServiceReactor(ControllerService.get());
        @Override
        public ListenableFuture<byte[]> doRPC(byte[] data)
        {
            return _reactor.react(data);
        }

        @Override
        public Throwable decodeError(PBException error)
        {
            return Exceptions.fromPB(error);
        }
    }
}
