/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import org.hibernate.validator.constraints.NotEmpty;

public class PollingConfiguration
{
    @Min(0L)
    @Max(10000L)
    @JsonProperty
    private long initialDelayInSeconds;

    public long getInitialDelayInSeconds()
    {
        return initialDelayInSeconds;
    }

    @Min(1L)
    @Max(10000L)
    @JsonProperty
    private long intervalInSeconds;

    public long getIntervalInSeconds()
    {
        return intervalInSeconds;
    }
}
