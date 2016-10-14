package com.aerofs.ui;

import com.aerofs.base.C;

public class UIParam
{
    public static final long UPDATE_CHECKER_INITIAL_DELAY = 10 * C.SEC;
    public static final long UPDATE_CHECKER_INTERVAL = 1 * C.HOUR;
    public static final long ROOT_ANCHOR_POLL_INTERVAL= 10 * C.SEC;
    public static final long LOGIN_PASSWD_RETRY_DELAY = 3 * C.SEC;

    public static final long DM_RESTART_INTERVAL        = 5 * C.SEC;
    public static final int  DM_LAUNCH_PING_RETRIES     = 120;
    public static final long DM_LOG_ARCHIVE_STARTUP_DELAY = 15 * C.MIN;
    // upload archived logs every 2 hours so we can retry in a timely manner.
    // Since the upload is tuned for high latency, the most likely case of failure is the client
    // is offline. Thus we don't want to retry too soon nor do we want to wait too long to retry.
    // Hence the period of 2 hours is chosen.
    public static final long DM_LOG_ARCHIVE_INTERVAL    = 2 * C.HOUR;
    public static final long DM_RESTART_MONITORING_INTERVAL = 5 * C.SEC;

    public static final int DEFAULT_REFRESH_DELAY = 100;
    public static final int SLOW_REFRESH_DELAY = 1000;

    public static final long DAEMON_CONNECTION_RETRY_INTERVAL = 1 * C.SEC;
}
