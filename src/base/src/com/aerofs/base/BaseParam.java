/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base;

import com.aerofs.base.properties.DynamicInetSocketAddress;
import com.aerofs.base.properties.DynamicUrlProperty;
import com.netflix.config.DynamicStringProperty;
import java.net.InetSocketAddress;

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

    public static class Metrics
    {
        public static final DynamicInetSocketAddress ADDRESS = new DynamicInetSocketAddress(
                "base.metrics.address",
                InetSocketAddress.createUnresolved("metrics.aerofs.com", 2003));
    }

    public static class Zephyr
    {
        public static final String TRANSPORT_ID = "z";

        // staging value: "staging.aerofs.com:8888"
        // this value is dynamic but clients will not pick up the new value on failure
        public static final DynamicInetSocketAddress ADDRESS =
                new DynamicInetSocketAddress("base.zephyr.address",
                        InetSocketAddress.createUnresolved("relay.aerofs.com", 443));
    }

    public static class WWW
    {
        public static final DynamicUrlProperty URL =
                new DynamicUrlProperty("base.www.url", "https://www.aerofs.com");

        public static String url()
        {
            return URL.get().toExternalForm();
        }

        public static final DynamicStringProperty SUPPORT_EMAIL_ADDRESS =
                new DynamicStringProperty("base.www.support_email_address", "support@aerofs.com");

        public static final DynamicStringProperty MARKETING_HOST_URL =
                new DynamicStringProperty("base.www.marketing_host_url", url());

        public static final DynamicStringProperty DASHBOARD_HOST_URL =
                new DynamicStringProperty("base.www.dashboard_host_url", url());

        public static final DynamicStringProperty PASSWORD_RESET_REQUEST_URL =
                new DynamicStringProperty("base.www.password_reset_request_url",
                        url() + "/request_password_reset");

        public static final DynamicStringProperty UPGRADE_URL =
                new DynamicStringProperty("base.www.upgrade_url", url() + "/upgrade");

        public static final DynamicStringProperty TEAM_MEMBERS_URL =
                new DynamicStringProperty("base.www.team_members_url", url() + "/admin/team_members");

        public static final DynamicStringProperty DEVICES_URL =
                new DynamicStringProperty("base.www.devices_url", url() + "/devices");

        public static final DynamicStringProperty TEAM_SERVER_DEVICES_URL =
                new DynamicStringProperty("base.www.team_server_devices_url",
                        url() + "/admin/team_servers");

        public static final DynamicStringProperty DOWNLOAD_URL =
                new DynamicStringProperty("base.www.download_url", url() + "/download");

        public static final DynamicStringProperty TOS_URL =
                new DynamicStringProperty("base.www.tos_url", url() + "/terms#privacy");
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

        public static final DynamicUrlProperty URL =
                new DynamicUrlProperty("base.sp.url", "https://sp.aerofs.com/sp");
    }

    public static class CA
    {
        public static final DynamicUrlProperty URL =
                new DynamicUrlProperty("base.ca.url", "http://joan.aerofs.com:1029/prod");
    }

    public static class MobileService
    {
        public static final byte[] MAGIC_BYTES = "MOBL".getBytes();
        public static final int VERSION_NUMBER = 3;
    }

    public static class Verkehr
    {
        public static final DynamicStringProperty HOST =
                new DynamicStringProperty("base.verkehr.host", "verkehr.aerofs.com");

        public static final DynamicStringProperty PUBLISH_PORT =
                new DynamicStringProperty("base.verkehr.port.publish", "9293");
        public static final DynamicStringProperty ADMIN_PORT =
                new DynamicStringProperty("base.verkehr.port.admin", "25234");
        public static final DynamicStringProperty SUBSCRIBE_PORT =
                new DynamicStringProperty("base.verkehr.port.subscribe", "443");
    }

    public static class VerkehrTopics
    {
        public static final String TOPIC_SEPARATOR = "/";
        public static final String CMD_CHANNEL_TOPIC_PREFIX = "cmd" + TOPIC_SEPARATOR;
        public static final String ACL_CHANNEL_TOPIC_PREFIX = "acl" + TOPIC_SEPARATOR;
        public static final String SSS_CHANNEL_TOPIC_PREFIX = "sss" + TOPIC_SEPARATOR;
    }
}
