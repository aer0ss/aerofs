package com.aerofs.sp.client;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.UserID;
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

    private final UserID _user;
    private static IBadCredentialListener _bcl;

    /**
     * Compared to SPClientFactory, which will be removed in the future, this Factory enables DI
     */
    public static class Factory
    {
        public SPBlockingClient create_(URL spURL, UserID user)
        {
            return new SPBlockingClient(new SPClientHandler(spURL,
                    SPClientFactory.getDefaultConfigurator()), user);
        }

        public SPBlockingClient create_(UserID user)
        {
            return create_(SP.URL, user);
        }
    }

    public static void setListener(IBadCredentialListener bcl)
    {
        _bcl = bcl;
    }

    SPBlockingClient(SPServiceStubCallbacks callbacks, UserID user)
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
            super.signIn(_user.toString(), ByteString.copyFrom(Cfg.scrypted()));
        } catch (ExBadCredential e) {
            if (_bcl != null) {
               l.debug("ExBadCredential Caught, informing UI.");
               _bcl.exceptionReceived();
            }
            throw e;
        }
    }
}
