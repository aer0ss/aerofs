package com.aerofs.base;

import static com.aerofs.base.config.ConfigurationProperties.*;

public class AuditParam
{
    public final boolean    _enabled;
    public final int        _servicePort;
    public final String     _servicePath;
    public final String     _publicUrl;
    public final int        _connTimeout;
    public final int        _readTimeout;
    public final int        _initialDelay;
    public final int        _interval;

    public AuditParam(boolean enabled, int servicePort, String servicePath, String publicUrl,
                      int connTimeout, int readTimeout, int initialDelay, int interval)
    {
        _enabled            = enabled;
        _servicePort        = servicePort;
        _servicePath        = servicePath;
        _publicUrl          = publicUrl;
        _connTimeout        = connTimeout;
        _readTimeout        = readTimeout;
        _initialDelay       = initialDelay;
        _interval           = interval;
    }

    public static AuditParam fromConfiguration()
    {
        return new AuditParam(
                getBooleanProperty( "base.audit.enabled",               false),
                getIntegerProperty( "base.audit.service.port",          9300),
                getStringProperty(  "base.audit.service.path",          "/event"),
                getStringProperty(  "base.audit.public.url",
                        "https://share.syncfs.com/audit/event"),
                getIntegerProperty( "base.audit.service.conn.timeout",  10 * (int)C.SEC),
                getIntegerProperty( "base.audit.service.read.timeout",  10 * (int)C.SEC),
                getIntegerProperty( "base.audit.post.delay.inital",     30 * (int)C.SEC),
                getIntegerProperty( "base.audit.post.interval",         15 * (int)C.SEC)
        );
    }

    // used by tests
    public static AuditParam getDefault()
    {
        return new AuditParam(
                false,
                9300,
                "/event",
                "https://share.syncfs.com/audit/event",
                10 * (int)C.SEC,
                10 * (int)C.SEC,
                30 * (int)C.SEC,
                15 * (int)C.SEC);
    }
}
