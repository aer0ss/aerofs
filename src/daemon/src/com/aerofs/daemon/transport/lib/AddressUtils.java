/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import java.net.InetSocketAddress;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utility functions for printing sockets, data transfer strings, etc.
 */
public class AddressUtils
{
    /**
     * Helpful logging method to print an {@link InetSocketAddress} in a consistent way
     *
     * @param a address to print
     * @return log string of the form: addr:port
     */
    public static String printaddr(InetSocketAddress a)
    {
        checkNotNull(a);
        return a.getAddress() + ":" + a.getPort();
    }
}
