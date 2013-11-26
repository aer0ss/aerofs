package com.aerofs.sp.client;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.IURLConnectionConfigurator;
import com.aerofs.base.net.SslURLConnectionConfigurator;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.proto.Sp.SPServiceBlockingStub;
import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;
import org.slf4j.Logger;

/**
 * This is a synchronous interface
 */
public class SPBlockingClient extends SPServiceBlockingStub
{
    private static final Logger l = Loggers.getLogger(SPBlockingClient.class);

    private static IBadCredentialListener _bcl;

    public static class Factory
    {
        // only used for mutual auth
        private final UserID _user;
        private final DID _did;

        private final IURLConnectionConfigurator MUTUAL_AUTH;
        private final IURLConnectionConfigurator ONE_WAY_AUTH;

        /**
         * Construct a client factory.
         *
         * The created clients will only be able to perform unauthenticated calls.
         */
        public Factory(ICertificateProvider cacert)
        {
            this(UserID.DUMMY, cacert);
        }

        /**
         * Construct a client factory.
         *
         * The created clients will only be able to perform one-way auth and should NOT use
         * {@link #signInRemote}
         */
        public Factory(UserID user, ICertificateProvider cacert)
        {
            _user = user;
            _did = new DID(DID.ZERO);
            MUTUAL_AUTH = null;
            ONE_WAY_AUTH = SslURLConnectionConfigurator.oneWayAuth(Platform.Desktop, cacert);
        }

        /**
         * Construct a client factory.
         *
         * The created clients will use mutual auth by default
         */
        public Factory(UserID user, DID did, IPrivateKeyProvider key, ICertificateProvider cacert)
        {
            _user = user;
            _did = did;
            MUTUAL_AUTH = SslURLConnectionConfigurator.mutualAuth(Platform.Desktop, key, cacert);
            ONE_WAY_AUTH = SslURLConnectionConfigurator.oneWayAuth(Platform.Desktop, cacert);
        }

        public SPBlockingClient create()
        {
            return new SPBlockingClient(
                    new SPClientHandler(SP.URL, MUTUAL_AUTH != null ? MUTUAL_AUTH : ONE_WAY_AUTH),
                    this);
        }
    }

    public static void setBadCredentialListener(IBadCredentialListener bcl)
    {
        _bcl = bcl;
    }

    private final Factory _f;

    private SPBlockingClient(SPServiceStubCallbacks callbacks, Factory f)
    {
        super(callbacks);
        _f = f;
    }

    /**
     * Sign into the SP server using a device certificate
     */
    public SPBlockingClient signInRemote() throws Exception
    {
        try {
            super.signInDevice(_f._user.getString(), _f._did.toPB());
            return this;
        } catch (ExBadCredential e) {
            if (_bcl != null) {
               l.debug("ExBadCredential Caught, informing UI.");
               _bcl.exceptionReceived();
            }
            throw e;
        }
    }
}
