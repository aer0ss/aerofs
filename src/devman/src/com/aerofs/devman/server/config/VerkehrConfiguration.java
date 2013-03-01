package com.aerofs.devman.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

public class VerkehrConfiguration
{
    @NotEmpty
    @JsonProperty
    private String host;

    @JsonProperty
    private short port;

    public String getHost() {
        return host;
    }

    public short getPort() {
        return port;
    }
}
