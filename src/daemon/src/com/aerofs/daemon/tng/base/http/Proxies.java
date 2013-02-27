/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.http;

import com.aerofs.lib.Util;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public abstract class Proxies
{
    private static final Logger l = Util.l(Proxies.class);

    private Proxies()
    {
        // Private constructor to prevent subclassing
    }

    public static Proxy getSystemProxy(Proxy.Type type, String host)
    {
        switch (type) {
        case HTTP:
            return getSystemHttpProxy(host);
        case DIRECT:
            return Proxy.NO_PROXY;
        default:
            // unsupported proxy type
            assert false : type;
            break;
        }

        return Proxy.NO_PROXY;
    }

    public static Proxy getSystemHttpProxy(String host)
    {
        Proxy proxy = Proxy.NO_PROXY;
        try {
            // Grab the Proxies that Java will give us
            List<Proxy> proxies = ProxySelector.getDefault().select(new URI("http://" + host));

            // Iterate over the list of proxies and select the first HTTP proxy
            for (Proxy systemProxy : proxies) {
                if (systemProxy.type() == Type.HTTP) {
                    assert systemProxy.address() instanceof InetSocketAddress : "SystemUtil proxy address is not an inet address";

                    // For some reason, the Proxy returned by the ProxySelector is not resolved,
                    // and any connection to the Proxy's address will result in an UnknownHostException
                    // For this reason, we rebuild the Proxy object
                    InetSocketAddress addr = (InetSocketAddress) systemProxy.address();
                    proxy = new Proxy(Type.HTTP,
                            new InetSocketAddress(addr.getHostName(), addr.getPort()));
                    break;
                }
            }
        } catch (URISyntaxException e) {
            l.warn("Failed to parse proxy address: " + e);
        }
        return proxy;
    }

}
