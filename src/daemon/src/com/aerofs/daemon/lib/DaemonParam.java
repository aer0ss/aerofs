package com.aerofs.daemon.lib;

import com.aerofs.lib.C;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;

public class DaemonParam
{
    public static final int QUEUE_LENGTH_DEFAULT        = 1024;

    //
    // pulsing parameters
    //

    public static final int MAX_PULSE_FAILURES      = 3;
    public static final long INIT_PULSE_TIMEOUT     = 1 * C.SEC;
    public static final long MAX_PULSE_TIMEOUT      = 60 * C.SEC;
    public static final long CONNECT_TIMEOUT        = 30 * C.SEC;

    public static class TCP
    {
        public static final int MCAST_MAX_DGRAM_SIZE    = 1024;
        public static final long HEARTBEAT_INTERVAL     = 30 * C.SEC;
        public static final long RETRY_INTERVAL         = 5 * C.SEC;
        public static final int BACKLOG                 = 128;
        public static final int QUEUE_LENGTH            = QUEUE_LENGTH_DEFAULT;
        public static final long ARP_GC_INTERVAL        = HEARTBEAT_INTERVAL * 2;
    }

    public static class XMPP
    {
        public static final long CONNECT_TIMEOUT        = 30 * C.SEC;
        public static final int QUEUE_LENGTH            = QUEUE_LENGTH_DEFAULT;
        public static final String MUC_ADDR             = "c." + XMPP.SERVER_DOMAIN;
        public static final String SERVER_DOMAIN        = "aerofs.com";


        public static enum PACKETROUTE
        {
            JINGLE("jingle"),
            ZEPHYR("zephyr"),
            BADBAD("badbad"); // this must _always_ be last

            private PACKETROUTE(String id)
            {
                _id = id;
            }

            public String id()
            {
                return _id;
            }

            public int pref()
            {
                return ordinal();
            }

            @Override
            public String toString()
            {
                return _id;
            }

            private final String _id;
        }
    }

    public static class Zephyr
    {
        public static final int QUEUE_LENGTH = QUEUE_LENGTH_DEFAULT;
        public static final int WORKER_THREAD_POOL_SIZE = 10;
    }

    public static class Jingle
    {
        public static final int QUEUE_LENGTH            = QUEUE_LENGTH_DEFAULT;
        public static final long CALL_TIMEOUT           = 30 * C.SEC;
    }

    public static class DB
    {
        public static final int FLUSHER_CAP_DEFAULT         = 512;

        public static final int OA_CACHE_SIZE               = 10 * 1024;
        public static final int DS_CACHE_SIZE               = 10 * 1024;
    }

    public static class DTLS
    {
        public static final int HS_QUEUE_SIZE               = 256;
        public final static int BUF_SIZE                    = 64 * C.KB;
    }

    public static final int TRANSPORT_DIAGNOSIS_CACHE_CAPACITY = 8;

    // TODO BUGBUG there are unknown deadlocks
    // test with pathologically small lengths e.g. 1
    public static final int QUEUE_LENGTH_CORE               = QUEUE_LENGTH_DEFAULT;

    // the delay to send NEW_UPDATE messages
    public static final long NEW_UPDATE_MESSAGE_DELAY       = 0;

    // it is the initial limit of maxcast message sizes. the limit may be further
    // reduced at runtime based on metrics feedback from transports
    public static final int MAX_MAX_MAXCAST_MESSAGE_SIZE    = 1 * C.KB;

    // 16+ KB is the maximum TLS application buffer size
    // it defines the maximum size for atomic messages and chunks
    // DO NOT use this param directly. use _m.getMaxUnicastSize_() instead
    public static final int MAX_UNICAST_MESSAGE_SIZE        = 8 * C.KB;

    // this is to prevent DOS attacks ONLY. * 3 is to accommodate transport
    // headers, size increment due to encryption, etc
    public static final int MAX_TRANSPORT_MESSAGE_SIZE  =
        Math.max(MAX_UNICAST_MESSAGE_SIZE, MAX_MAX_MAXCAST_MESSAGE_SIZE) * 3;

    public static final int TRANSPORT_FLOOD_MESSAGE_SIZE = MAX_TRANSPORT_MESSAGE_SIZE / 2;

    public static final long ANTI_ENTROPY_INTERVAL = 30 * C.SEC;

    public static final long  STORE_RECONFIG_DELAY      = 5 * C.SEC;

    public static final boolean MAXCAST_IF_NO_AVAILABLE_DEVICES = true;

    public static final int FIND_NEXT_PARTITION_INTERVAL        = 5;

    public static final long LINK_STATE_MONITOR_INTERVAL        = 10 * C.SEC;

    public static final int TC_RECLAIM_HI_WATERMARK     = 3;
    public static final int TC_RECLAIM_LO_WATERMARK     = 1;
    public static final long TC_RECLAIM_DELAY           = 30 * C.SEC;

    public static final long HOSTNAME_MONITOR_MIN_DELAY = 15 * C.SEC;

    public static final long SCANNER_RESCAN_DELAY_MIN   = 500;
    public static final long SCANNER_RESCAN_DELAY_MAX   = 5 * C.SEC;

    public static final long WIN_NOTIFIER_DELETE_WAIT = 5 * C.SEC;
    public static final long WIN_NOTIFIER_DR_DELETE_DELAY =
        WIN_NOTIFIER_DELETE_WAIT + 1 * C.SEC;
    public static final long WIN_NOTIFIER_DR_SCAN_DELAY = 5 * C.SEC;

    // TODO use a unified session management system where a session is consistently deleted from
    // caches at all the layers at once
    // TODO move this method to a more appropriate location
    public static int deviceLRUSize()
    {
        // assert the cfg has been initialized
        assert Cfg.inited();

        return Math.max((Cfg.db().getInt(Key.MAX_SERVER_STACKS) * 20 +
                Cfg.db().getInt(Key.MAX_CLIENT_STACKS) * 2) * 20, 64);
    }
}
