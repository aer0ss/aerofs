/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.google.common.collect.ImmutableSet;
import org.junit.Ignore;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

@Ignore("Utility class")
public abstract class TestUtilities
{
    private TestUtilities()
    {}

    public static ImmutableSet<NetworkInterface> getCurrentNetworkInterfaces_()
            throws SocketException
    {
        ImmutableSet.Builder<NetworkInterface> interfacesBuiler = ImmutableSet.builder();

        Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
        while (e.hasMoreElements()) {
            interfacesBuiler.add(e.nextElement());
        }

        return interfacesBuiler.build();
    }
}
