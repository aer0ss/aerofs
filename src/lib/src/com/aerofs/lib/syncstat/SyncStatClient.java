package com.aerofs.lib.syncstat;

import com.aerofs.proto.Syncstat.SyncSignInReply;
import org.apache.log4j.Logger;

import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Syncstat.SyncStatServiceStub;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;

public class SyncStatClient extends SyncStatServiceStub {

    private static final Logger l = Util.l(SyncStatClient.class);

    private final String _user;

    SyncStatClient(SyncStatServiceStubCallbacks callbacks, String user)
    {
        super(callbacks);
        _user = user;
    }

    /**
     * Sign into the Sync Status server with the config scryted credentials.
     */
    public ListenableFuture<SyncSignInReply> signInRemote()
    {
        ListenableFuture<SyncSignInReply> future = super.signIn(_user,
                ByteString.copyFrom(Cfg.scrypted()),
                ByteString.copyFrom(Cfg.did().getBytes()));

        Futures.addCallback(future, new FutureCallback<SyncSignInReply>()
        {
            @Override
            public void onSuccess(SyncSignInReply reply)
            {
                // TODO (MP) possibly send activities to the server.
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                l.warn("Signing into Sync Status failed: " + Util.e(throwable));
            }
        });

        return future;
    }
}