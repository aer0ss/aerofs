package com.aerofs.lib.spsv;

import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Sp.SPServiceStub;
import com.aerofs.proto.Sp.SignInReply;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

public class SPClient extends SPServiceStub
{
    private static final Logger l = Util.l(SPClient.class);

    private final String _user;

    SPClient(SPServiceStubCallbacks callbacks, String user)
    {
        super(callbacks);
        _user = user;
    }

    /**
     * Sign into the SP server with the config scryted credentials
     */
    public ListenableFuture<SignInReply> signInRemote()
    {
        ListenableFuture<SignInReply> future = super.signIn(_user,
                ByteString.copyFrom(Cfg.scrypted()));
        Futures.addCallback(future, new FutureCallback<SignInReply>()
        {
            @Override
            public void onSuccess(SignInReply signInReply)
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
