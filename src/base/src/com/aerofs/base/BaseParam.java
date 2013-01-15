/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base;

import com.aerofs.labeling.L;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Base parameters for the servers, the desktop app and the Android client.
 * Do not add code to this class if it's not used by the Android client too.
 */
public class BaseParam
{
    // recommended size for file I/O buffers
    public static final int FILE_BUF_SIZE                    = 512 * C.KB;

    public static class Xmpp
    {
        public static final String SERVER_DOMAIN        = "aerofs.com";
        public static final String MUC_ADDR             = "c." + SERVER_DOMAIN;

        public static InetSocketAddress xmppAddress()
        {
            return L.get().isStaging() ?
                    InetSocketAddress.createUnresolved("staging.aerofs.com", 9328) :
                    InetSocketAddress.createUnresolved("x.aerofs.com", 443);
        }
    }

    public static class Zephyr
    {
        public static String TRANSPORT_ID = "z";

        public static InetSocketAddress zephyrAddress()
        {
            return L.get().isStaging() ?
                    InetSocketAddress.createUnresolved("staging.aerofs.com", 8888) :
                    InetSocketAddress.createUnresolved("zephyr.aerofs.com", 443);
        }
    }

    public static class SV
    {
        public static final String
                DOWNLOAD_LINK = SP.WEB_BASE + "/download",
                DOWNLOAD_BASE = "https://cache.client." + (L.get().isStaging() ? "stg." : "") + "aerofs.com",
                NOCACHE_DOWNLOAD_BASE = "https://nocache.client." + (L.get().isStaging() ? "stg." : "") + "aerofs.com",
                SUPPORT_EMAIL_ADDRESS = "support@aerofs.com";

        public static final long CONNECT_TIMEOUT = 1 * C.MIN;
        public static final long READ_TIMEOUT = 30 * C.SEC;
    }

    public static class SP
    {
        public static final String WEB_BASE = "https://www.aerofs.com";
        public static final String ADMIN_PANEL_BASE = "https://my.aerofs.com";
        public static final String TEAM_MANAGEMENT_LINK = ADMIN_PANEL_BASE + "/admin/users";

        public static final String SP_POST_PARAM_PROTOCOL  = "protocol_vers";
        public static final String SP_POST_PARAM_DATA      = "data";

        // Make sure to also update:
        // 1) src/web/development.ini
        // 2) src/web/production.ini
        // 3) syncdet_test/lib/param.py
        // when incrementing SP_PROTOCOL_VERSION
        public static final int SP_PROTOCOL_VERSION         = 20;

        public static final java.net.URL URL;

        static {
            URL url;
            try {
                // in staging, the SP war is deployed under /sp by defualt
                // allowing users to deploy their own sp war's (e.g. /yuriSP/sp, /weihanSP/sp, etc.)
                url = L.get().isStaging() ?
                        new URL("https://staging.aerofs.com/sp/sp") :
                        new URL("https://sp.aerofs.com/sp");
            } catch (MalformedURLException e) {
                throw new Error(e);
            }
            URL = url;
        }
    }

    public static class MobileService
    {
        public static final byte[] MAGIC_BYTES = "MOBL".getBytes();
        public static final int VERSION_NUMBER = 2;
    }
}
