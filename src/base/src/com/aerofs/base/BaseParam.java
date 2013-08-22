/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base;

import com.aerofs.base.params.IProperty;

import java.net.InetSocketAddress;
import java.net.URL;
import java.security.cert.X509Certificate;

import static com.aerofs.base.config.ConfigurationProperties.getAddressProperty;
import static com.aerofs.base.config.ConfigurationProperties.getCertificateProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.aerofs.base.config.ConfigurationProperties.getUrlProperty;

/**
 * Base parameters for the servers, the desktop app and the Android client.
 * Do not add code to this class if it's not used by the Android client too.
 *
 * NB: Properties that are to be read from props must appear in subclasses of BaseParam, because
 * BaseParam's static intializers will run before setPropertySource is called.
 */
public class BaseParam
{
    // recommended size for file I/O buffers
    public static final int FILE_BUF_SIZE = 512 * C.KB;

    public static class Cacert
    {
        public static final IProperty<X509Certificate> CACERT = getCertificateProperty(
                "config.loader.base_ca_certificate", null);
    }

    public static class XMPP
    {
        public static final IProperty<String> SERVER_DOMAIN = getStringProperty("base.xmpp.domain",
                "aerofs.com");

        public static String getMucAddress()
        {
            return "c." + SERVER_DOMAIN.get();
        }

        public static final IProperty<InetSocketAddress> ADDRESS = getAddressProperty(
                "base.xmpp.address", InetSocketAddress.createUnresolved("x.aerofs.com", 443));
    }

    public static class Metrics
    {
        public static final IProperty<InetSocketAddress> ADDRESS = getAddressProperty(
                "base.metrics.address",
                InetSocketAddress.createUnresolved("metrics.aerofs.com", 2003));
    }

    public static class Zephyr
    {
        public static final String TRANSPORT_ID = "z";

        // staging value: "staging.aerofs.com:8888"
        // this value is dynamic but clients will not pick up the new value on failure
        public static final IProperty<InetSocketAddress> ADDRESS = getAddressProperty(
                "base.zephyr.address", InetSocketAddress.createUnresolved("relay.aerofs.com", 443));
    }

    public static class WWW
    {
        public static final IProperty<URL> URL = getUrlProperty("base.www.url",
                "https://www.aerofs.com");

        public static String url()
        {
            return URL.get().toExternalForm();
        }

        public static final IProperty<String> SUPPORT_EMAIL_ADDRESS =
                getStringProperty("base.www.support_email_address", "support@aerofs.com");

        public static final IProperty<String> MARKETING_HOST_URL =
                getStringProperty("base.www.marketing_host_url", url());

        public static final IProperty<String> DASHBOARD_HOST_URL =
                getStringProperty("base.www.dashboard_host_url", url());

        public static final IProperty<String> PASSWORD_RESET_REQUEST_URL =
                getStringProperty("base.www.password_reset_request_url",
                        url() + "/request_password_reset");

        public static final IProperty<String> UPGRADE_URL =
                getStringProperty("base.www.upgrade_url", url() + "/upgrade");

        public static final IProperty<String> TEAM_MEMBERS_URL =
                getStringProperty("base.www.team_members_url", url() + "/admin/team_members");

        public static final IProperty<String> DEVICES_URL =
                getStringProperty("base.www.devices_url", url() + "/devices");

        public static final IProperty<String> TEAM_SERVER_DEVICES_URL =
                getStringProperty("base.www.team_server_devices_url", url() + "/admin/team_servers");

        public static final IProperty<String> DOWNLOAD_URL =
                getStringProperty("base.www.download_url", url() + "/download");

        public static final IProperty<String> TOS_URL =
                getStringProperty("base.www.tos_url", url() + "/terms#privacy");

        public static final String FAQ_SYNC_HISTORY_URL
                = "https://aerofs.zendesk.com/entries/23753136";
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

        public static final IProperty<URL> URL = getUrlProperty("base.sp.url",
                "https://sp.aerofs.com/sp");
    }

    public static class MobileService
    {
        private static final byte VERSION_NUMBER = 3;
        public static final byte[] MAGIC_BYTES = {'M', 'B', 'L', VERSION_NUMBER};
    }

    public static class Verkehr
    {
        public static final IProperty<String> HOST =
                getStringProperty("base.verkehr.host", "verkehr.aerofs.com");

        public static final IProperty<String> PUBLISH_PORT =
                getStringProperty("base.verkehr.port.publish", "9293");
        public static final IProperty<String> ADMIN_PORT =
                getStringProperty("base.verkehr.port.admin", "25234");
        public static final IProperty<String> SUBSCRIBE_PORT =
                getStringProperty("base.verkehr.port.subscribe", "443");
    }

    public static class VerkehrTopics
    {
        public static final String TOPIC_SEPARATOR = "/";
        public static final String CMD_CHANNEL_TOPIC_PREFIX = "cmd" + TOPIC_SEPARATOR;
        public static final String ACL_CHANNEL_TOPIC_PREFIX = "acl" + TOPIC_SEPARATOR;
        public static final String SSS_CHANNEL_TOPIC_PREFIX = "sss" + TOPIC_SEPARATOR;
    }

    public static class Mixpanel
    {
        public static final IProperty<String> API_ENDPOINT = getStringProperty("base.mixpanel.url",
                "https://api.mixpanel.com/track/?data=");
    }
}
