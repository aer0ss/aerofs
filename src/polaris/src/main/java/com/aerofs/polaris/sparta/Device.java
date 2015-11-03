package com.aerofs.polaris.sparta;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

public class Device {
    public final String id;
    public final String owner;
    public final String name;
    public final String osFamily;
    public final Date installDate;

    @JsonCreator
    public Device(@JsonProperty("id") String id,
                  @JsonProperty("owner") String owner,
                  @JsonProperty("name") String name,
                  @JsonProperty("os_family") String osFamily,
                  @JsonProperty("install_date") Date installDate)
    {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.osFamily = osFamily;
        this.installDate = installDate;
    }
}
