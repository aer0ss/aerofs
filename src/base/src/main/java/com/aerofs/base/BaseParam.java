/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base;

import java.net.URL;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
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
        // Audit log posting

        public static final int                START_POSTING_AUDIT_EVENTS_AFTER =
                getIntegerProperty(            "base.audit.post.delay.initial",  30 * (int) C.SEC);

        public static final int                AUDIT_POSTING_INTERVAL =
                getIntegerProperty(            "base.audit.post.interval",  15 * (int) C.SEC);
    }
}
