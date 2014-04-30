/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.lib.DaemonParam;

public class Metrics {

    public int getMaxUnicastSize_()
    {
        return DaemonParam.MAX_UNICAST_MESSAGE_SIZE;
    }
}
