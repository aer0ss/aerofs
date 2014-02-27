/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.lib.DaemonParam;

public class Metrics {

    private int _maxcastSize = DaemonParam.MAX_MAXCAST_MESSAGE_SIZE;

    public void setRecommendedMaxcastSize_(int size)
    {
        _maxcastSize = size;
    }

    // TODO don't recommend but enforce instead?
    public int getRecommendedMaxcastSize_()
    {
        return _maxcastSize;
    }

    public int getMaxUnicastSize_()
    {
        return DaemonParam.MAX_UNICAST_MESSAGE_SIZE;
    }
}
