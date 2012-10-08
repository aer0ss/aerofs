package com.aerofs.lib;

import com.aerofs.l.L;
import com.aerofs.lib.cfg.Cfg;

import java.net.MalformedURLException;
import java.net.URL;

public class Param
{
    public static final int FILE_BUF_SIZE                    = 512 * C.KB;
    public static final int FILE_CHUNK_SIZE                  = 4 * C.MB;
    public static final int FSICLIENT_POOL_SIZE              = 6;
    public static final boolean STRICT_LISTENERS             = false;
    public static final int MIN_PASSWD_LENGTH                = 6;
    public static final long FREQUENT_DEFECT_SENDER_INTERVAL = 3 * C.HOUR;
    public static final long EXP_RETRY_MIN_DEFAULT           = 2 * C.SEC;
    public static final long EXP_RETRY_MAX_DEFFAULT          = 60 * C.SEC;

    public static class PostUpdate
    {
        // These variables are saved here rather than *PostUpdateTasks classes so that both
        // UI and processes can access them.
        public static final int DAEMON_POST_UPDATE_TASKS = 5;
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
        public static String zephyrHost()
        {
            return "zephyr.aerofs.com";
        }

        public static short zephyrPort()
        {
            return Cfg.staging() ?
                (short) 8888 :
                (short) 443;
        }
    }

    public static class SV
    {
        public static final String
            DOWNLOAD_LINK = "https://" + L.get().webHost() + "/download",
            DOWNLOAD_BASE = "https://cache.client." + (Cfg.staging() ? "stg." : "") + "aerofs.com",
            NOCACHE_DOWNLOAD_BASE = "https://nocache.client." + (Cfg.staging() ? "stg." : "") + "aerofs.com",
            SUPPORT_EMAIL_ADDRESS = "support@aerofs.com";
    }

    public static class SP
    {
        public static final String WEB_BASE = "https://" + L.get().webHost() + "/";
        public static final URL URL;

        static {
            URL url;
            try {
                url = new URL("https://" + (Cfg.staging() ? "staging.aerofs.com/sp" : L.get().spHost()) + "/sp");
            } catch (MalformedURLException e) {
                Util.fatal(e);
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
                Util.fatal(e);
                url = null;
            }
            URL = url;
        }
    }
}
