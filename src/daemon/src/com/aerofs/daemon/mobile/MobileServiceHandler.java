/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.mobile;

import com.aerofs.base.net.AbstractRpcServerHandler;
import com.aerofs.proto.Mobile.MobileServiceReactor;
import com.google.common.util.concurrent.ListenableFuture;

class MobileServiceHandler extends AbstractRpcServerHandler
{
    private final MobileServiceReactor _reactor;

    public MobileServiceHandler(MobileService service)
    {
        _reactor = new MobileServiceReactor(service);
    }

    @Override
    protected ListenableFuture<byte[]> react(byte[] data)
    {
        return _reactor.react(data);
    }
}