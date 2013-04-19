package com.aerofs.sp.client;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.net.IURLConnectionConfigurator;
import com.aerofs.base.net.NullURLConnectionConfigurator;
import com.aerofs.labeling.L;
import com.aerofs.base.id.UserID;


/**
 * A class wrapping SP*Client factory methods
 * They return a new instance to a client to the SP Service.
 * See doc in SPBlockingClient for more information.
 * TODO (WW) remove this class and use SPBlockingClient.Factory instead.
 */
public class SPClientFactory
{
    public static SPBlockingClient newBlockingClient(UserID user)
    {
        return new SPBlockingClient(new SPClientHandler(SP.url(), getDefaultConfigurator()), user);
    }

    public static SPBlockingClient newBlockingClientWithNullConnectionConfigurator(UserID user)
    {
        return new SPBlockingClient(new SPClientHandler(SP.url(),
                NullURLConnectionConfigurator.NULL_URL_CONNECTION_CONFIGURATOR), user);
    }

    static IURLConnectionConfigurator getDefaultConfigurator()
    {
        return L.isMultiuser() ?
                SSLURLConnectionConfigurator.SSL_URL_CONNECTION_CONFIGURATOR :
                NullURLConnectionConfigurator.NULL_URL_CONNECTION_CONFIGURATOR;
    }
}
