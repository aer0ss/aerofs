package com.aerofs.sp.client;

import com.aerofs.lib.id.UserID;
import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;
import java.net.URL;

/**
 * A class wrapping SP*Client factory methods
 * They return a new instance to a client to the SP Service.
 * See doc in SPBlockingClient for more information.
 */
public class SPClientFactory
{
    public static SPClient newClient(URL spURL, UserID user)
    {
        SPServiceStubCallbacks callbacks = new SPClientHandler(spURL);
        return new SPClient(callbacks, user);
    }

    public static SPBlockingClient newBlockingClient(URL spURL, UserID user)
    {
        return new SPBlockingClient(new SPClientHandler(spURL), user);
    }
}
