package com.aerofs.daemon.core.net.throttling;

import com.aerofs.daemon.lib.DaemonParam;

/**
 * Contains important parameters for throttling operation
 */
class LimitParam
{
    static final long _MAX_UL_BW = Long.MAX_VALUE;
    static final long _MIN_UL_BW = DaemonParam.MAX_UNICAST_MESSAGE_SIZE; // bytes/sec [basically I can send out 1 unicast packet per sec]
    static final long _MAX_DL_BW = Long.MAX_VALUE;
    static final long _MIN_DL_BW = DaemonParam.MAX_UNICAST_MESSAGE_SIZE + 1024; // bytes/sec
}
