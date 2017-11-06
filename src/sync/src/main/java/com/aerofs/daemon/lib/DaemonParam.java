package com.aerofs.daemon.lib;

import com.aerofs.base.C;

public class DaemonParam
{
    public static final int QUEUE_LENGTH_DEFAULT        = 1024;

    //
    // transport parameters
    //

    public static final long DEFAULT_CONNECT_TIMEOUT = 20 * C.SEC;
    public static final long SLOW_CONNECT            = 30 * C.SEC;
    public static final long HEARTBEAT_INTERVAL   = 5 * C.SEC;
    public static final int MAX_FAILED_HEARTBEATS = 3;

    public static class XMPP
    {
        public static final long CONNECT_TIMEOUT        = 30 * C.SEC;
    }

    public static class Zephyr
    {
        public static final long HANDSHAKE_TIMEOUT    = 10 * C.SEC;
    }

    public static class DB
    {
        public static final int OA_CACHE_SIZE               = 10 * C.KB;
        public static final int DS_CACHE_SIZE               = 10 * C.KB;
    }

    // TODO BUGBUG there are unknown deadlocks
    // test with pathologically small lengths e.g. 1
    public static final int QUEUE_LENGTH_CORE               = QUEUE_LENGTH_DEFAULT;

    // it is the initial limit of maxcast message sizes. the limit may be further
    // reduced at runtime based on metrics feedback from transports
    public static final int MAX_MAXCAST_MESSAGE_SIZE = 1 * C.KB;

    // 16+ KB is the maximum TLS application buffer size
    // it defines the maximum size for atomic messages and chunks
    // DO NOT use this param directly. use _m.getMaxUnicastSize_() instead
    public static final int MAX_UNICAST_MESSAGE_SIZE        = 8 * C.KB;

    // this is to prevent DOS attacks ONLY. * 3 is to accommodate transport
    // headers, size increment due to encryption, etc
    public static final int MAX_TRANSPORT_MESSAGE_SIZE  =
        Math.max(MAX_UNICAST_MESSAGE_SIZE, MAX_MAXCAST_MESSAGE_SIZE) * 3;

    public static final long MAX_LINK_POLLING_INTERVAL = 30 * C.SEC;

    public static final long MIN_LINK_POLLING_INTERVAL = 10 * C.SEC;

    public static final int TC_RECLAIM_HI_WATERMARK     = 3;
    public static final int TC_RECLAIM_LO_WATERMARK     = 1;
    public static final long TC_RECLAIM_DELAY           = 30 * C.SEC;

    // wait at least 250ms between two successive progress notifications
    // i.e. send at most 4 progress notifications per transfer per second
    public static final int NOTIFY_THRESHOLD = 250;

    public static final long CHECK_QUOTA_INTERVAL = 10 * C.MIN;
}
