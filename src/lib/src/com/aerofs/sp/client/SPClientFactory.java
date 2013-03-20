package com.aerofs.sp.client;

import com.aerofs.base.net.IURLConnectionConfigurator;
import com.aerofs.base.net.NullURLConnectionConfigurator;
import com.aerofs.labeling.L;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;
import java.net.URL;

/**
 * A class wrapping SP*Client factory methods
 * They return a new instance to a client to the SP Service.
 * See doc in SPBlockingClient for more information.
 * TODO (WW) remove this class and use SPBlockingClient.Factory instead.
 */
public class SPClientFactory
{
    public static SPBlockingClient newBlockingClient(URL spURL, UserID user)
    {
        return new SPBlockingClient(new SPClientHandler(spURL, getDefaultConfigurator()), user);
    }

    public static SPBlockingClient newBlockingClientWithNullConnectionConfigurator(URL spURL,
            UserID user)
    {
        return new SPBlockingClient(new SPClientHandler(spURL,
                NullURLConnectionConfigurator.NULL_URL_CONNECTION_CONFIGURATOR), user);
    }

    static IURLConnectionConfigurator getDefaultConfigurator()
    {
        return L.get().isMultiuser() ?
                SSLURLConnectionConfigurator.SSL_URL_CONNECTION_CONFIGURATOR :
                NullURLConnectionConfigurator.NULL_URL_CONNECTION_CONFIGURATOR;
    }
}
