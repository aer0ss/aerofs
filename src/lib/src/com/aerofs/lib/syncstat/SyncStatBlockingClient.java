package com.aerofs.lib.syncstat;

import java.net.URL;

import org.apache.log4j.Logger;

import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.proto.Syncstat.SyncStatServiceBlockingStub;
import com.aerofs.proto.Syncstat.SyncStatServiceStub.SyncStatServiceStubCallbacks;
import com.google.protobuf.ByteString;

public class SyncStatBlockingClient extends SyncStatServiceBlockingStub {
    private static final Logger l = Util.l(SyncStatBlockingClient.class);

    private final String _user;

    /**
     * For DI
     */
    public static class Factory
    {
        public SyncStatBlockingClient create(URL url, String user)
        {
            return new SyncStatBlockingClient(new SyncStatClientHandler(url), user);
        }
    }

    SyncStatBlockingClient(SyncStatServiceStubCallbacks callbacks, String user)
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
