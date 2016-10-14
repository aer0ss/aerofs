package com.aerofs.rest.api;

import java.util.Date;

public class DeviceStatus {
    public final Boolean online;
    public final Date lastSeen;

    public DeviceStatus(boolean online, Date lastSeen)
    {
        this.online = online;
        this.lastSeen = lastSeen;
    }
}
