/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.daemon.core.net.tng.Preference;


public interface ITransportListenerFactory
{
    ITransportListener getInstance_(String id, Preference pref);
}