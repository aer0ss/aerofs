package com.aerofs.sp.client;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.IURLConnectionConfigurator;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.configuration.EnterpriseCertificateProvider;
import com.aerofs.proto.Sp.SPServiceBlockingStub;
import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;
import org.slf4j.Logger;

/**
 * This is a synchronous interface
 */
public class SPBlockingClient extends SPServiceBlockingStub
{
    private static final Logger l = Loggers.getLogger(SPBlockingClient.class);

    public static final IURLConnectionConfigurator MUTUAL_AUTH_CONNECTION_CONFIGURATOR;
    public static final IURLConnectionConfigurator ONE_WAY_AUTH_CONNECTION_CONFIGURATOR;

    private final UserID _user;

    private static IBadCredentialListener _bcl;

    static
    {
        // N.B. if the enterprise certificate is not provided when the client is built in
        //   enterprise mode, fallback to use the production CA cert instead.
        ICertificateProvider certificateProvider = PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT
                ? new EnterpriseCertificateProvider()
                : new CfgCACertificateProvider();

        MUTUAL_AUTH_CONNECTION_CONFIGURATOR
                = new MutualAuthURLConnectionConfigurator(certificateProvider);
        ONE_WAY_AUTH_CONNECTION_CONFIGURATOR
                = new OneWayAuthURLConnectionConfigurator(certificateProvider);
    }

    /**
     * Compared to SPClientFactory, which will be removed in the future, this Factory enables DI
     * (although not strictly needed at this time, we follow this pattern anyway).
     */
    public static class Factory
    {
        public SPBlockingClient create_(UserID user)
        {
            return new SPBlockingClient(
                    new SPClientHandler(SP.URL, MUTUAL_AUTH_CONNECTION_CONFIGURATOR), user);
        }

        public SPBlockingClient create_(UserID user, IURLConnectionConfigurator configurator)
        {
            return new SPBlockingClient(new SPClientHandler(SP.URL, configurator), user);
        }

        /**
         * Use an invalid UserID, since the caller will not be using signInRemote().
         */
        public SPBlockingClient create_(IURLConnectionConfigurator configurator)
        {
            return new SPBlockingClient(new SPClientHandler(SP.URL, configurator),
                    UserID.fromInternal(""));
        }
    }

    public static void setBadCredentialListener(IBadCredentialListener bcl)
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
            super.signInDevice(_user.getString(), Cfg.did().toPB());
        } catch (ExBadCredential e) {
            if (_bcl != null) {
               l.debug("ExBadCredential Caught, informing UI.");
               _bcl.exceptionReceived();
            }
            throw e;
        }
    }
}
