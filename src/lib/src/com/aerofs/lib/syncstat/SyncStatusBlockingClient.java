/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.syncstat;

import java.net.URL;

import org.apache.log4j.Logger;

import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.proto.SyncStatus.SyncStatusServiceBlockingStub;
import com.aerofs.proto.SyncStatus.SyncStatusServiceStub.SyncStatusServiceStubCallbacks;
import com.google.protobuf.ByteString;

public class SyncStatusBlockingClient extends SyncStatusServiceBlockingStub {
    private static final Logger l = Util.l(SyncStatusBlockingClient.class);

    private final String _user;

    /**
     * For DI
     */
    public static class Factory
    {
        public SyncStatusBlockingClient create(URL url, String user)
        {
            return new SyncStatusBlockingClient(new SyncStatusClientHandler(url), user);
        }
    }

    SyncStatusBlockingClient(SyncStatusServiceStubCallbacks callbacks, String user)
    {
        super(callbacks);
        _user = user;
    }

    /**
     * Sign into the Sync Status server with the config scryted credentials
     */
    public long signInRemote() throws Exception
    {
        try {
            return super.signIn(_user,
                    ByteString.copyFrom(Cfg.scrypted()),
                    ByteString.copyFrom(Cfg.did().getBytes())).getClientEpoch();
        } catch (ExBadCredential e) {
            l.warn("Signing into sync status server failed: " + Util.e(e));
            throw e;
        }
    }
}
