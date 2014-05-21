package com.aerofs.sp.server.authentication;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

public class SystemAuthParam
{
    public static final boolean SYSTEM_AUTH_ENDPOINT_ENABLED =
            getBooleanProperty("system.authorization.endpoint_enabled", false);
    public static final String SYSTEM_AUTH_ENDPOINT_HOST =
            getStringProperty("system.authorization.endpoint_host", "");
    public static final int SYSTEM_AUTH_ENDPOINT_PORT =
            getIntegerProperty("system.authorization.endpoint_port", 0);
    public static final boolean SYSTEM_AUTH_ENDPOINT_USE_SSL =
            getBooleanProperty("system.authorization.endpoint_use_ssl", false);
    public static final String SYSTEM_AUTH_ENDPOINT_CERTIFICATE =
            getStringProperty("system.authorization.endpoint_certificate", "");
}