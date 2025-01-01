package com.aerofs.sp.client;

import com.aerofs.base.BaseUtil;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.base.net.IURLConnectionConfigurator;
import com.aerofs.base.net.SslURLConnectionConfigurator;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.proto.Sp.SPServiceBlockingStub;
import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;
import com.google.common.base.Throwables;

import java.net.MalformedURLException;
import java.net.URI;

/**
 * This is a synchronous interface
 */
public class SPBlockingClient extends SPServiceBlockingStub
{
    public static class Factory
    {
        // only used for mutual auth
        private final UserID _user;
        private final DID _did;
        private final String _url;

        private final IURLConnectionConfigurator MUTUAL_AUTH;
        private final IURLConnectionConfigurator ONE_WAY_AUTH;

        /**
         * Construct a client factory.
         *
         * The created clients will only be able to perform unauthenticated calls.
         */
        public Factory(String url, ICertificateProvider cacert)
        {
            this(url, UserID.DUMMY, cacert);
        }

        /**
         * Construct a client factory.
         *
         * The created clients will only be able to perform one-way auth and should NOT use
         * {@link #signInRemote}
         */
        public Factory(String url, UserID user, ICertificateProvider cacert)
        {
            _url = url;
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
        public Factory(String url, UserID user, DID did, IPrivateKeyProvider key, ICertificateProvider cacert)
        {
            _url = url;
            _user = user;
            _did = did;
            MUTUAL_AUTH = SslURLConnectionConfigurator.mutualAuth(Platform.Desktop, key, cacert);
            ONE_WAY_AUTH = SslURLConnectionConfigurator.oneWayAuth(Platform.Desktop, cacert);
        }

        public SPBlockingClient create()
        {
            try {
                return new SPBlockingClient(
                        new SPClientHandler(URI.create(_url).toURL(), MUTUAL_AUTH != null ? MUTUAL_AUTH : ONE_WAY_AUTH),
                        this);
            } catch (final MalformedURLException e) {
                throw Throwables.propagate(e);
            }
        }
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
        super.signInDevice(_f._user.getString(), BaseUtil.toPB(_f._did));
        return this;
    }
}
