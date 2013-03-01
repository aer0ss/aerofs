package com.aerofs.devman.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yammer.dropwizard.config.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class DeviceManagementServiceConfiguration extends Configuration
{
    @Valid
    @NotNull
    @JsonProperty
    private RedisConfiguration redis = new RedisConfiguration();
    public RedisConfiguration getRedisConfiguration()
    {
        return redis;
    }

    @Valid
    @NotNull
    @JsonProperty
    private VerkehrConfiguration verkehr = new VerkehrConfiguration();
    public VerkehrConfiguration getVerkehrConfiguration()
    {
        return verkehr;
    }

    @Valid
    @NotNull
    @JsonProperty
    private PollingConfiguration polling = new PollingConfiguration();
    public PollingConfiguration getPollingConfiguration()
    {
        return polling;
    }
}
