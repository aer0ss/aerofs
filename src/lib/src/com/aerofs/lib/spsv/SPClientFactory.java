package com.aerofs.lib.spsv;

import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;
import java.net.URL;

/**
 * A class wrapping SP*Client factory methods
 * They return a new instance to a client to the SP Service.
 * See doc in SPBlockingClient for more information.
 */
public class SPClientFactory
{
    // TODO (WW) use injection and remove this test stub. See SPBlockingClient.Factory
    public static SPServiceStubCallbacks s_testCallbacks = null;

    public static SPClient newClient(URL spURL, String user)
    {
        SPServiceStubCallbacks callbacks = new SPClientHandler(spURL);
        return new SPClient(callbacks, user);
    }

    public static SPBlockingClient newBlockingClient(URL spURL, String user)
    {
        SPServiceStubCallbacks callbacks;

        // This static variable will be non-null when a junit test sets it to circumvent the
        // SPClientHandler. Otherwise, use the standard SPClientHandler which contacts the SP
        // Servlet
        if (s_testCallbacks != null) {
            callbacks = s_testCallbacks;
        } else {
            callbacks = new SPClientHandler(spURL);
        }
        return new SPBlockingClient(callbacks, user);
    }
}
