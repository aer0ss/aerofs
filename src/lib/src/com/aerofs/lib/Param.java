package com.aerofs.lib;

import com.aerofs.l.L;
import com.aerofs.lib.cfg.Cfg;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;

public class Param
{
    // recommended size for file I/O buffers
    public static final int FILE_BUF_SIZE                    = 512 * C.KB;
    // the block size used for content hashing and block storage (see BlockStorage)
    public static final int FILE_BLOCK_SIZE                  = 4 * C.MB;
    public static final int MIN_PASSWD_LENGTH                = 6;
    public static final long FREQUENT_DEFECT_SENDER_INTERVAL = 3 * C.HOUR;
    public static final long EXP_RETRY_MIN_DEFAULT           = 2 * C.SEC;
    public static final long EXP_RETRY_MAX_DEFFAULT          = 60 * C.SEC;

    private static final String XMPP_SERVER_PROP = "aerofs.xmpp";
    private static final String ZEPHYR_SERVER_PROP = "aerofs.zephyr";

    private static InetSocketAddress parseAddress(String str)
    {
        if (str == null) return null;
        int pos = str.lastIndexOf(':');
        if (pos == -1) return null;
        String host = str.substring(0, pos);
        int port = Integer.parseInt(str.substring(pos + 1));
        return InetSocketAddress.createUnresolved(host, port);
    }

    public static InetSocketAddress xmppAddress()
    {
        InetSocketAddress address = parseAddress(System.getProperty(XMPP_SERVER_PROP));
        if (address == null) {
            address = InetSocketAddress.createUnresolved(
                    L.get().xmppServerAddr(), L.get().xmppServerPort());
        }
        return address;
    }

    public static class PostUpdate
    {
        // These variables are saved here rather than *PostUpdateTasks classes so that both
        // UI and processes can access them.
        public static final int DAEMON_POST_UPDATE_TASKS = 13;
        public static final int UI_POST_UPDATE_TASKS = 0;
    }

    public static class Ritual
    {
        public static final int MAX_FRAME_LENGTH = C.MB;
        public static final int LENGTH_FIELD_SIZE = Integer.SIZE / Byte.SIZE;
    }

    public static class Throttling
    {
        // must be consistent with files.proto
        public static final long UNLIMITED_BANDWIDTH = 0;
        public static final long MIN_BANDWIDTH_UI = 10 * C.KB;
    }

    public static class Zephyr
    {
        public static InetSocketAddress zephyrAddress()
        {
            InetSocketAddress address = parseAddress(System.getProperty(ZEPHYR_SERVER_PROP));
            if (address == null) {
                String host;
                int port;
                if (Cfg.staging()) {
                    host = "staging.aerofs.com";
                    port = 8888;
                } else {
                    host = "zephyr.aerofs.com";
                    port = 443;
                }
                address = InetSocketAddress.createUnresolved(host, port);
            }
            return address;
        }

        public static String zephyrHost()
        {
            // Hostname here will not do a reverse lookup since
            // the zephyrAddress() was created with a hostname.
            return zephyrAddress().getHostName();
        }

        public static short zephyrPort()
        {
            return (short)zephyrAddress().getPort();
        }
    }

    public static class SV
    {
        public static final String
            DOWNLOAD_LINK = "https://" + L.get().webHost() + "/download",
            DOWNLOAD_BASE = "https://cache.client." + (Cfg.staging() ? "stg." : "") + "aerofs.com",
            NOCACHE_DOWNLOAD_BASE = "https://nocache.client." + (Cfg.staging() ? "stg." : "") + "aerofs.com",
            SUPPORT_EMAIL_ADDRESS = "support@aerofs.com";

        public static final long CONNECT_TIMEOUT = 1 * C.MIN;
        public static final long READ_TIMEOUT = 30 * C.SEC;
    }

    public static class SP
    {
        public static final String WEB_BASE = "https://" + L.get().webHost() + "/";
        public static final URL URL;

        static {
            URL url;
            try {
                // in staging, the SP war is deployed under /sp by defualt
                // allowing users to deploy their own sp war's (e.g. /yuriSP/sp, /weihanSP/sp, etc.)
                url = new URL(L.get().spUrl() + "/sp");
            } catch (MalformedURLException e) {
                SystemUtil.fatal(e);
                url = null;
            }
            URL = url;
        }
    }

    public static class Verkehr
    {
        public static final String VERKEHR_HOST = Cfg.staging() ? "staging.aerofs.com" : "verkehr.aerofs.com";
        public static final short VERKEHR_PORT = (short) (Cfg.staging() ? 80 : 443);
        public static final long VERKEHR_RETRY_INTERVAL = 5 * C.SEC;
    }

    public static class SyncStat
    {
        public static final URL URL;

        static {
            URL url;
            try {
                url = new URL(Cfg.staging() ?
                        "https://sss-staging.aerofs.com/syncstat" :
                        "https://" + L.get().ssHost() + "/syncstat");
            } catch (MalformedURLException e) {
                SystemUtil.fatal(e);
                url = null;
            }
            URL = url;
        }
    }
}
