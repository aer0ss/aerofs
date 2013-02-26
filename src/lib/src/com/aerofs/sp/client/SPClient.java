package com.aerofs.sp.client;

import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common.Void;
import com.aerofs.proto.Sp.SPServiceStub;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

public class SPClient extends SPServiceStub
{
    private static final Logger l = Util.l(SPClient.class);

    private final UserID _user;

    SPClient(SPServiceStubCallbacks callbacks, UserID user)
    {
        super(callbacks);
        _user = user;
    }

    /**
     * Sign into the SP server with the config scryted credentials
     */
    public ListenableFuture<Void> signInRemote()
    {
        ListenableFuture<Void> future = super.signIn(_user.getString(),
                ByteString.copyFrom(Cfg.scrypted()));

        Futures.addCallback(future, new FutureCallback<Void>()
        {
            @Override
            public void onSuccess(Void signInReply)
            {
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                l.warn("Signing into SP failed: " + Util.e(throwable));
            }
        });
        return future;
    }
}
