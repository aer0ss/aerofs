/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.base.id.DID;

/**
 */
public interface IPeerPresenceListener
{
    void onPresenceServiceConnected_();

    void onPresenceServiceDisconnected_();

    void onPeerOnline_(DID did);

    void onPeerOffline_(DID did);
}
