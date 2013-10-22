/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.link;

import com.google.common.collect.ImmutableSet;

import java.net.NetworkInterface;

public interface ILinkStateListener
{
    /**
     * WARNING never pause during execution of this method
     * e.g. tk.pause_(Cfg.timeout(), reason);
     */
    void onLinkStateChanged(ImmutableSet<NetworkInterface> previous,
            ImmutableSet<NetworkInterface> current, ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed);
}
