/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

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
        public static final String DASHBOARD_HOST_URL = getStringProperty("base.www.url");

        // this is non-final because one of the unit tests need to update this value. Due to the
        // way we misuse static final fields for configuration values everywhere, it is difficult
        // for a single test to mock a configuration property.
        public static String SUPPORT_EMAIL_ADDRESS = getStringProperty(
                "base.www.support_email_address", "support@aerofs.com");

        // the marketing Web's location is independent from the URL parameter. It should always
        // points to the product's public home page.
        public static final String MARKETING_HOST_URL = getStringProperty(
                "base.www.marketing_host_url", "https://www.aerofs.com");

        public static final String PASSWORD_RESET_REQUEST_URL = getStringProperty("reset.request.url");

        public static final String DEVICES_URL = getStringProperty("base.www.devices_url");

        public static final String TEAM_SERVER_DEVICES_URL = getStringProperty("base.www.team_server_devices_url");

        public static final String TWO_FACTOR_SETUP_URL = getStringProperty("base.www.two_factor_setup_url");

        public static final String RECERTIFY_SUPPORT_URL = "https://support.aerofs.com/hc/en-us/articles/201439354";
    }
}
