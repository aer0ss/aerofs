package com.aerofs.lib.syncstat;

import com.aerofs.proto.Syncstat.SyncSignInReply;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Syncstat.SyncStatServiceStub;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;

public class SyncStatClient extends SyncStatServiceStub {

    private final String _user;

    SyncStatClient(SyncStatServiceStubCallbacks callbacks, String user)
    {
        super(callbacks);
        _user = user;
    }

    /**
     * Sign into the Sync Status server with the config scryted credentials.
     *
     * NB: the caller is fully responsible for reply handling (exceptions and epoch-related logic)
     */
    public ListenableFuture<SyncSignInReply> signInRemote()
    {
        return super.signIn(_user,
                ByteString.copyFrom(Cfg.scrypted()),
                ByteString.copyFrom(Cfg.did().getBytes()));
    }
}
