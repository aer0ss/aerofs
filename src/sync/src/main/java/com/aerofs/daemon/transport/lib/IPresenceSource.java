/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.SID;

public interface IPresenceSource
{
    /**
     * Update the stores _we are interested in_.
     *
     * @param sidsAdded new stores
     * @param sidsRemoved stores in which we are no longer interested in
     */
    void updateInterest(SID[] sidsAdded, SID[] sidsRemoved);
}
