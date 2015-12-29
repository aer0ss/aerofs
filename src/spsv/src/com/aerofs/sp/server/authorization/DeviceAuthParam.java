package com.aerofs.sp.server.authorization;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

public class DeviceAuthParam
{
    public static final boolean DEVICE_AUTH_ENDPOINT_ENABLED =
            getBooleanProperty("device.authorization.endpoint_enabled", false);
    public static final String DEVICE_AUTH_ENDPOINT_HOST =
            getStringProperty("device.authorization.endpoint_host");
    public static final int DEVICE_AUTH_ENDPOINT_PORT =
            getIntegerProperty("device.authorization.endpoint_port", 0);
    public static final String DEVICE_AUTH_ENDPOINT_PATH =
            getStringProperty("device.authorization.endpoint_path");
    public static final boolean DEVICE_AUTH_ENDPOINT_USE_SSL =
            getBooleanProperty("device.authorization.endpoint_use_ssl", false);
    public static final String DEVICE_AUTH_ENDPOINT_CERTIFICATE =
            getStringProperty("device.authorization.endpoint_certificate");
}