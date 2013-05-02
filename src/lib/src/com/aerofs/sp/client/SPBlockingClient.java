package com.aerofs.sp.client;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.Loggers;
import com.aerofs.base.net.IURLConnectionConfigurator;
import com.aerofs.labeling.L;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Sp.SPServiceBlockingStub;
import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

/**
 * This is a synchronous interface
 */
public class SPBlockingClient extends SPServiceBlockingStub
{
    private static final Logger l = Loggers.getLogger(SPBlockingClient.class);

    public static final IURLConnectionConfigurator MUTUAL_AUTH_CONNECTION_CONFIGURATOR =
            new MutualAuthURLConnectionConfigurator();
    public static final IURLConnectionConfigurator ONE_WAY_AUTH_CONNECTION_CONFIGURATOR =
            new OneWayAuthURLConnectionConfigurator();

    private final UserID _user;
    private static IBadCredentialListener _bcl;

    /**
     * Compared to SPClientFactory, which will be removed in the future, this Factory enables DI
     * (although not strictly needed at this time, we follow this pattern anyway).
     */
    public static class Factory
    {
        private static IURLConnectionConfigurator getDefaultConfigurator()
        {
            return L.isMultiuser() ?
                    MUTUAL_AUTH_CONNECTION_CONFIGURATOR :
                    ONE_WAY_AUTH_CONNECTION_CONFIGURATOR;
        }

        public SPBlockingClient create_(UserID user)
        {
            return new SPBlockingClient(new SPClientHandler(SP.URL.get(), getDefaultConfigurator()),
                    user);
        }

        public SPBlockingClient create_(UserID user, IURLConnectionConfigurator configurator)
        {
            return new SPBlockingClient(new SPClientHandler(SP.URL.get(), configurator), user);
        }

        /**
         * Use an invalid UserID, since the caller will not be using signInRemote().
         */
        public SPBlockingClient create_(IURLConnectionConfigurator configurator)
        {
            return new SPBlockingClient(new SPClientHandler(SP.URL.get(), configurator),
                    UserID.fromInternal(""));
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
        ByteString credentials = L.isMultiuser() ?
                Cfg.did().toPB() :
                ByteString.copyFrom(Cfg.scrypted());

        try {
            super.signIn(_user.getString(), credentials);
        } catch (ExBadCredential e) {
            if (_bcl != null) {
               l.debug("ExBadCredential Caught, informing UI.");
               _bcl.exceptionReceived();
            }
            throw e;
        }
    }
}
