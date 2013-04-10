/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

public class VerkehrConfiguration
{
    @NotEmpty
    @JsonProperty
    private String host;

    @JsonProperty
    private short port;

    @NotEmpty
    @JsonProperty
    private String certFile;

    public String getHost()
    {
        return host;
    }

    public short getPort()
    {
        return port;
    }

    public String getCertFile()
    {
        return certFile;
    }
}
