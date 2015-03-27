/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.SID;

public interface IStores
{
    void updateStores(SID[] sidsAdded, SID[] sidsRemoved);
}
