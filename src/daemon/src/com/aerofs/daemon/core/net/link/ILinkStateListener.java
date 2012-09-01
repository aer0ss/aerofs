/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.link;

import com.google.common.collect.ImmutableSet;

import java.net.NetworkInterface;

public interface ILinkStateListener
{
    void onLinkStateChanged_(ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed, ImmutableSet<NetworkInterface> current,
            ImmutableSet<NetworkInterface> previous);
}
