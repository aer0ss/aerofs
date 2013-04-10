/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base;

import com.aerofs.base.properties.DynamicInetSocketAddress;
import com.aerofs.base.properties.DynamicUrlProperty;
import com.aerofs.labeling.L;
import com.netflix.config.DynamicStringProperty;

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
    public static final int FILE_BUF_SIZE = 512 * C.KB;

    public static class Xmpp
    {
        public static final String SERVER_DOMAIN = "aerofs.com";
        public static final String MUC_ADDR = "c." + SERVER_DOMAIN;

        // staging value: "staging.aerofs.com:9328"
        // this value is dynamic but clients will not pick up the new value on failure
        public static final DynamicInetSocketAddress ADDRESS =
                new DynamicInetSocketAddress("base.xmpp.address",
                        InetSocketAddress.createUnresolved("x.aerofs.com", 443));
    }

    public static class Zephyr
    {
        public static final String TRANSPORT_ID = "z";

        // staging value: "staging.aerofs.com:8888"
        // this value is dynamic but clients will not pick up the new value on failure
        public static final DynamicInetSocketAddress ADDRESS =
                new DynamicInetSocketAddress("base.zephyr.address",
                        InetSocketAddress.createUnresolved("zephyr.aerofs.com", 443));
    }

    public static class WWW
    {
        private static final String AEROFSURL = "https://www.aerofs.com";

        public static final DynamicStringProperty SUPPORT_EMAIL_ADDRESS =
                new DynamicStringProperty("base.www.support_email_address", "support@aerofs.com");

        public static final DynamicStringProperty MARKETING_HOST_URL =
                new DynamicStringProperty("base.www.marketing_host_url", AEROFSURL);

        public static final DynamicStringProperty DASHBOARD_HOST_URL =
                new DynamicStringProperty("base.www.dashboard_host_url", AEROFSURL);

        public static final DynamicStringProperty PASSWORD_RESET_REQUEST_URL =
                new DynamicStringProperty("base.www.password_reset_request_url",
                        AEROFSURL + "/request_password_reset");

        public static final DynamicStringProperty UPGRADE_URL =
                new DynamicStringProperty("base.www.upgrade_url", AEROFSURL + "/upgrade");

        public static final DynamicStringProperty TEAM_MEMBERS_URL =
                new DynamicStringProperty("base.www.team_members_url", AEROFSURL + "/admin/users");

        public static final DynamicStringProperty DEVICES_URL =
                new DynamicStringProperty("base.www.devices_url", AEROFSURL + "/devices");

        public static final DynamicStringProperty TEAM_SERVER_DEVICES_URL =
                new DynamicStringProperty("base.www.team_server_devices_url",
                        AEROFSURL + "/admin/team_servers");

        public static final DynamicStringProperty DOWNLOAD_URL =
                new DynamicStringProperty("base.www.download_url", AEROFSURL + "/download");

        public static final DynamicStringProperty TOS_URL =
                new DynamicStringProperty("base.www.tos_url", AEROFSURL + "/terms#privacy");
    }

    public static class SV
    {
        public static final long
                CONNECT_TIMEOUT = 1 * C.MIN,
                READ_TIMEOUT = 30 * C.SEC;
    }

    public static class SP
    {
        public static final String SP_POST_PARAM_PROTOCOL = "protocol_vers";
        public static final String SP_POST_PARAM_DATA = "data";

        // When incrementing SP_PROTOCOL_VERSION, make sure to also update:
        // 1) src/web/development.ini
        // 2) src/web/production.ini
        // 3) syncdet_test/lib/param.py
        // 4) code with a "WAIT_FOR_SP_PROTOCOL_VERSION_CHANGE" comment
        public static final int SP_PROTOCOL_VERSION = 20;

        // staging value: "https://staging.aerofs.com/sp/sp"
        public static final DynamicUrlProperty URL =
                new DynamicUrlProperty("base.sp_url", "https://sp.aerofs.com/sp");
    }

    public static class MobileService
    {
        public static final byte[] MAGIC_BYTES = "MOBL".getBytes();
        public static final int VERSION_NUMBER = 3;
    }

    public static class VerkehrTopics
    {
        public static final String TOPIC_SEPARATOR = "/";
        public static final String CMD_CHANNEL_TOPIC_PREFIX = "cmd" + TOPIC_SEPARATOR;
        public static final String ACL_CHANNEL_TOPIC_PREFIX = "acl" + TOPIC_SEPARATOR;
        public static final String SSS_CHANNEL_TOPIC_PREFIX = "sss" + TOPIC_SEPARATOR;
    }
}
