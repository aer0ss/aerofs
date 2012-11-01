/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.syncstat;

import com.aerofs.proto.SyncStatus.SyncSignInReply;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.SyncStatus.SyncStatusServiceStub;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;

public class SyncStatusClient extends SyncStatusServiceStub {

    private final String _user;

    SyncStatusClient(SyncStatusServiceStubCallbacks callbacks, String user)
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
