package com.aerofs.ui;

import com.aerofs.base.C;

public class UIParam
{
    public static final long UPDATE_CHECKER_INITIAL_DELAY = 5 * C.MIN;
    public static final long UPDATE_CHECKER_INTERVAL = 1 * C.HOUR;
    public static final long ROOT_ANCHOR_POLL_INTERVAL= 30 * C.SEC;
    public static final long TRANSPORT_PING_SAMPLE_TIME_WINDOW = 30 * C.MIN;
    public static final long TRANSPORT_FLOOD_DURATION = 10 * C.SEC;
    public static final long LOGIN_PASSWD_RETRY_DELAY = 3 * C.SEC;

    public static final long DM_RESTART_INTERVAL        = 5 * C.SEC;
    public static final int  DM_LAUNCH_PING_RETRIES     = 120;
    public static final long DM_LOG_ARCHIVE_STARTUP_DELAY = 15 * C.MIN;
    public static final long DM_LOG_ARCHIVE_INTERVAL    = 24 * C.HOUR;
    public static final long DM_RESTART_MONITORING_INTERVAL = 5 * C.SEC;

    public static final int DEFAULT_REFRESH_DELAY = 100;
    public static final int SLOW_REFRESH_DELAY = 1000;

    public static final long DAEMON_CONNECTION_RETRY_INTERVAL = 1 * C.SEC;
}