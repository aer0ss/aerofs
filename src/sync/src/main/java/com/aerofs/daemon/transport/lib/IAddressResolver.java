/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.DID;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;

import java.net.SocketAddress;

/**
 * Implemented by classes that can resolve a DID into
 * a remote address that the network stack can connect to.
 */
public interface IAddressResolver
{
    public SocketAddress resolve(DID did) throws ExDeviceUnavailable;
}
