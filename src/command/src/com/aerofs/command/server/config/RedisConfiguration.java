package com.aerofs.command.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

public class RedisConfiguration
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
