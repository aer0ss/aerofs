/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yammer.dropwizard.config.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class CommandServerServiceConfiguration extends Configuration
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
}
