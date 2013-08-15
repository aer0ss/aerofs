/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.lib;

import java.net.NetworkInterface;
import java.util.Set;

public interface ILinkStateListener
{
    /**
     * Utility function to be called by handlers when the network link changes
     * i.e. interfaces are added, removed, etc.
     *
     * @param removed set of network interfaces that were removed
     * @param added set of network interfaces that were added
     * @param prev full set of previous network interfaces (includes <code>removed</code>)
     * @param current full set of current network interfaces (includes <code>added</code>)
     */
    void linkStateChanged(
            Set<NetworkInterface> removed,
            Set<NetworkInterface> added,
            Set<NetworkInterface> prev,
            Set<NetworkInterface> current);
}
