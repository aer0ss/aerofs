/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.SID;

public interface IStores
{
    public void updateStores(SID[] sidsAdded, SID[] sidsRemoved);
}
