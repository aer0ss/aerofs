package com.aerofs.sp.client;

import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.proto.Sp.SPServiceBlockingStub;
import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

import java.net.URL;

/**
 * This is a synchronous interface
 */
public class SPBlockingClient extends SPServiceBlockingStub
{
    private static final Logger l = Util.l(SPBlockingClient.class);

    private final String _user;
    private static IBadCredentialListener _bcl;

    /**
     * Compared to SPClientFactory, which will be removed in the future, this Factory enables DI
     */
    public static class Factory
    {
        public SPBlockingClient create_(URL spURL, String user)
        {
            return new SPBlockingClient(new SPClientHandler(spURL), user);
        }
    }

    public static void setListener(IBadCredentialListener bcl)
    {
        _bcl = bcl;
    }

    SPBlockingClient(SPServiceStubCallbacks callbacks, String user)
    {
        super(callbacks);
        _user = user;
    }

    /**
     * Sign into the SP server with the config scryted credentials
     */
    public void signInRemote() throws Exception
    {
        try {
            super.signIn(_user, ByteString.copyFrom(Cfg.scrypted()));
        } catch (ExBadCredential e) {
            if (_bcl != null) {
               l.debug("ExBadCredential Caught, informing UI.");
               _bcl.exceptionReceived();
            }
            throw e;
        }
    }
}
