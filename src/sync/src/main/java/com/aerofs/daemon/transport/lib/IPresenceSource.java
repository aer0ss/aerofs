/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.SID;

public interface IPresenceSource
{
    /**
     * Update the stores _we are interested in_.
     */
    void updateInterest(SID sid, boolean join);
}
