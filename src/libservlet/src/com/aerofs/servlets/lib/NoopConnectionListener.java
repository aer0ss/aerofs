/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib;

import com.aerofs.verkehr.client.lib.IConnectionListener;

public final class NoopConnectionListener implements IConnectionListener
{
    @Override
    public final void onConnected()
    {
        // does nothing
    }

    @Override
    public final void onDisconnected()
    {
        // does nothing
    }
}
