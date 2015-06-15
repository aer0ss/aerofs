/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base;

import com.aerofs.base.ex.ExBadArgs;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.security.cert.X509Certificate;

import static com.aerofs.base.config.ConfigurationProperties.getAddressProperty;
import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.base.config.ConfigurationProperties.getCertificateProperty;
import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.aerofs.base.config.ConfigurationProperties.getUrlProperty;

/**
 * Base parameters for the servers, the desktop app and the Android client.
 * Do not add code to this class if it's not used by the Android client too.
 *
 * NB: Properties that are to be read from props must appear in subclasses of BaseParam, because
 * BaseParam's static initializers will run before setPropertySource is called.
 */
public class BaseParam
{
    // Recommended size for file I/O buffers.
    public static final int FILE_BUF_SIZE = 512 * C.KB;

    public static class Appliance
    {
        public static final String BASE_HOST = getStringProperty("base.host.unified", "aerofs.com");
    }

    public static class Cacert
    {
        // This property is used by the Android build.
        public static final X509Certificate CACERT = getCertificateProperty(
                "config.loader.base_ca_certificate", null);

        public static final String FILE = "/opt/sparta/cacert.pem";
    }

    public static class XMPP
    {
        public static final InetSocketAddress SERVER_ADDRESS = getAddressProperty("base.xmpp.address",
                InetSocketAddress.createUnresolved("x.aerofs.com", 443));

        public static String getServerDomain()
        {
            String hostname = SERVER_ADDRESS.getHostName();
            String[] split = hostname.split("\\.");

            if (split.length < 2) {
                //similar code in function 'is_hostname_xmpp_compatible',
                //make sure any changes to this code block are reflected accordingly in maintenance_util.py
                throw Throwables.propagate(new ExBadArgs("bad xmpp address"));
            }

            return split[split.length-2] + "." + split[split.length-1];
        }
    }

    public static class Charlie
    {
        public static final URL CHARLIE_URL = getUrlProperty("base.charlie.url", "https://charlie.aerofs.com/checkin");
    }

    public static class Metriks
    {
        public static final URL TRIKS_URL = getUrlProperty("base.triks.url", "http://triks.aerofs.com");
        public static boolean METRIKS_ENABLED = getBooleanProperty("base.triks.enabled", true);
    }

    public static class Zephyr
    {
        // this property is used by the Android build
        public static final String TRANSPORT_ID = "z";

        // this value is dynamic but clients will not pick up the new value on failure
        public static final InetSocketAddress SERVER_ADDRESS = getAddressProperty("base.zephyr.address",
                InetSocketAddress.createUnresolved("relay.aerofs.com", 443));
    }

    public static class WWW
    {
        public static final String DASHBOARD_HOST_URL = getStringProperty("base.www.url", "https://www.aerofs.com");

        // this is non-final because one of the unit tests need to update this value. Due to the
        // way we misuse static final fields for configuration values everywhere, it is difficult
        // for a single test to mock a configuration property.
        public static String SUPPORT_EMAIL_ADDRESS = getStringProperty(
                "base.www.support_email_address", "support@aerofs.com");

        // the marketing Web's location is independent from the URL parameter. It should always
        // points to the product's public home page.
        public static final String MARKETING_HOST_URL = getStringProperty(
                "base.www.marketing_host_url", "https://www.aerofs.com");

        public static final String PASSWORD_RESET_REQUEST_URL = DASHBOARD_HOST_URL + "/request_password_reset";

        public static final String PASSWORD_RESET_URL = DASHBOARD_HOST_URL + "/password_reset";

        public static final String SHARED_FOLDERS_URL = DASHBOARD_HOST_URL + "/shared_folders";

        public static final String ORGANIZATION_USERS_URL = DASHBOARD_HOST_URL + "/users";

        public static final String DEVICES_URL = DASHBOARD_HOST_URL + "/devices";

        public static final String TEAM_SERVER_DEVICES_URL = DASHBOARD_HOST_URL + "/team_servers";

        public static final String DOWNLOAD_URL = DASHBOARD_HOST_URL + "/download";

        public static final String ORG_SETTINGS_URL = DASHBOARD_HOST_URL + "/org/settings";

        public static final String TOS_URL = MARKETING_HOST_URL+ "/terms#privacy";

        public static final String TWO_FACTOR_SETUP_URL = DASHBOARD_HOST_URL + "/settings/two_factor_authentication/intro";

        public static final String RECERTIFY_SUPPORT_URL = "https://support.aerofs.com/hc/en-us/articles/201439354";

        public static final String COLLECT_LOGS_URL = getStringProperty("base.collect_logs.url", "");
    }

    public static class SP
    {
        public static final String SP_POST_PARAM_PROTOCOL = "protocol_vers";
        public static final String SP_POST_PARAM_DATA = "data";

        // See comment in sp.proto
        public static final int SP_PROTOCOL_VERSION = 21;

        public static final String URL = getStringProperty("base.sp.url", "https://sp.aerofs.com/sp/");
    }

    public static class Verkehr
    {
        public static final String HOST =
                getStringProperty("base.verkehr.host", "verkehr.aerofs.com");

        public static final short PROTOBUF_PORT =
                (short) getIntegerProperty("base.verkehr.port.pb", 443).intValue();

        public static final short REST_PORT =
                (short) getIntegerProperty("base.verkehr.port.rest", 25234).intValue();

        public static final long MIN_RETRY_INTERVAL = 5 * C.SEC;
        public static final long MAX_RETRY_INTERVAL = 30 * C.SEC;
    }

    public static class Topics
    {
        public static String getACLTopic(String userId, boolean urlEncoded)
        {
            String topicName = "acl/" + userId;
            if (urlEncoded) {
                try {
                    return URLEncoder.encode(topicName, Charsets.UTF_8.toString());
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalArgumentException("fail create acl topic for " + userId, e);
                }
            } else {
                return topicName;
            }
        }

        public static String getCMDTopic(String formalDID, boolean urlEncoded)
        {
            String topicName = "cmd/" + formalDID;

            if (urlEncoded) {
                try {
                    return URLEncoder.encode(topicName, Charsets.UTF_8.toString());
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalArgumentException("fail create cmd topic for " + formalDID, e);
                }
            } else {
                return topicName;
            }
        }
    }

    public static class Mixpanel
    {
        public static final String API_ENDPOINT = getStringProperty("base.mixpanel.url",
                "https://api.mixpanel.com/track/?data=");
    }

    public static class Audit
    {
        /**
         * Boolean indicating whether or not the audit feature has been enabled.
         *
         * N.B. audit is enabled when both of the following are true:
         *
         *  1. Our license allows us to use the audit feature, AND
         *  2. The user has enabled auditing during the setup process.
         */
        public static boolean                   AUDIT_ENABLED =
                getBooleanProperty(             "base.audit.enabled", false);

        /**
         * Hostname for private (server-component-only) access to the audit service.
         */
        public static final String              SERVICE_HOST =
                getStringProperty(              "base.audit.service.host", "localhost");

        /**
         * Port number for private (server-component-only) access to the audit service.
         */
        public static final Integer             SERVICE_PORT =
                getIntegerProperty(             "base.audit.service.port", 9300);

        /**
         * Path component (server-component-only) access to the audit REST service.
         */
        public static final String              SERVICE_EVENT_PATH =
                getStringProperty(              "base.audit.service.path", "/event");

        /**
         * URL for public (i.e. client) access to the audit REST service.
         */
        public static final URL                 PUBLIC_EVENT_URL =
                getUrlProperty(                 "base.audit.public.url",
                                                "https://share.syncfs.com/audit/event");

        /**
         * Connection timeout in milliseconds.
         */
        public static final int                 CONN_TIMEOUT =
                getIntegerProperty(             "base.audit.service.conn.timeout", 10 * (int)C.SEC);

        /**
         * Read timeout in milliseconds.
         */
        public static final int                 READ_TIMEOUT =
                getIntegerProperty(             "base.audit.service.read.timeout", 10 * (int)C.SEC);

        // ----
        // Downstream channel config

        public static String                    CHANNEL_HOST =
                getStringProperty(              "base.audit.downstream_host", null);

        public static int                       CHANNEL_PORT =
                getIntegerProperty(             "base.audit.downstream_port", 0);

        public static boolean                   CHANNEL_SSL =
                getBooleanProperty(             "base.audit.downstream_ssl_enabled", false);

        public static String                    CHANNEL_CERT =
                getStringProperty(              "base.audit.downstream_certificate", "");

        // ----
        // Audit log posting

        public static final int                START_POSTING_AUDIT_EVENTS_AFTER =
                getIntegerProperty(            "base.audit.post.delay.initial",  30 * (int) C.SEC);

        public static final int                AUDIT_POSTING_INTERVAL =
                getIntegerProperty(            "base.audit.post.interval",  15 * (int) C.SEC);
    }
}
