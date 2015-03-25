package com.aerofs.daemon.lib;

import com.aerofs.base.C;
import com.aerofs.lib.LibParam.Daemon;

import java.net.InetSocketAddress;

import static com.aerofs.base.config.ConfigurationProperties.getAddressProperty;
import static com.google.common.base.Preconditions.checkState;

public class DaemonParam
{
    static {
        checkState(Daemon.HEARTBEAT_TIMEOUT > Jingle.CALL_TIMEOUT);
    }

    public static final int QUEUE_LENGTH_DEFAULT        = 1024;

    //
    // transport parameters
    //

    public static final long DEFAULT_CONNECT_TIMEOUT = 20 * C.SEC;
    public static final long SLOW_CONNECT            = 30 * C.SEC;
    public static final long HEARTBEAT_INTERVAL   = 5 * C.SEC;
    public static final int MAX_FAILED_HEARTBEATS = 3;

    public static class TCP
    {
        public static final String MCAST_ADDRESS        = "225.7.8.9";
        public static final int MCAST_PORT              = 29871;
        public static final int MCAST_MAX_DGRAM_SIZE    = 1024;
        public static final int IP_MULTICAST_TTL        = 8;
        public static final long HEARTBEAT_INTERVAL     = 15 * C.SEC;
        public static final long RETRY_INTERVAL         = 5 * C.SEC;
        public static final long ARP_GC_INTERVAL        = HEARTBEAT_INTERVAL * 6;
    }

    public static class XMPP
    {
        public static final long CONNECT_TIMEOUT        = 30 * C.SEC;
    }

    public static class Zephyr
    {
        public static final long HANDSHAKE_TIMEOUT    = 10 * C.SEC;
    }

    public static class Jingle
    {
        public static final long CALL_TIMEOUT                     = 30 * C.SEC;
        public static final InetSocketAddress STUN_SERVER_ADDRESS = getAddressProperty(
                "daemon.stun.address",
                InetSocketAddress.createUnresolved("stun.l.google.com", 19302));
    }

    public static class DB
    {
        public static final int OA_CACHE_SIZE               = 10 * C.KB;
        public static final int DS_CACHE_SIZE               = 10 * C.KB;
    }

    // TODO BUGBUG there are unknown deadlocks
    // test with pathologically small lengths e.g. 1
    public static final int QUEUE_LENGTH_CORE               = QUEUE_LENGTH_DEFAULT;

    // the delay to send NEW_UPDATE messages
    public static final long NEW_UPDATE_MESSAGE_DELAY       = 0;

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

    public static final long ANTI_ENTROPY_INTERVAL = 30 * C.SEC;

    public static final long LINK_STATE_POLLING_INTERVAL        = 10 * C.SEC;

    public static final int TC_RECLAIM_HI_WATERMARK     = 3;
    public static final int TC_RECLAIM_LO_WATERMARK     = 1;
    public static final long TC_RECLAIM_DELAY           = 30 * C.SEC;

    // wait at least 250ms between two successive progress notifications
    // i.e. send at most 4 progress notifications per transfer per second
    public static final int NOTIFY_THRESHOLD = 250;

    public static final long CHECK_QUOTA_INTERVAL = 1 * C.MIN;

    // re-hashing large prefixes synchronously would introduce large delays in RPCs.
    // These delays would lead the transport to abort transfers before it actually
    // has a chance to start which would lead to persistent no-sync for large files
    // when a transfer is interrupted at a late enough point.
    //
    // see SUPPORT-1602 for evidence of this happening in the wild.
    //
    // To avoid this issue, we restrict pipelined hashing to small prefixes and hash
    // the whole file after the download is complete.
    public static final long PREFIX_REHASH_MAX_LENGTH = 16 * C.KB;
}