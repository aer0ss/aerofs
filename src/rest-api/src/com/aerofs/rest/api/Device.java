package com.aerofs.rest.api;

import java.util.Date;

public class Device {
    public final String id;
    public final String owner;
    public final String name;
    public final String osFamily;
    public final Date installDate;

    public Device(String id, String owner, String name, String osFamily, Date installDate)
    {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.osFamily = osFamily;
        this.installDate = installDate;
    }
}
