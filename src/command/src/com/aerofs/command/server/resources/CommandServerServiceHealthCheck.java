/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.resources;

import com.yammer.metrics.core.HealthCheck;

public final class CommandServerServiceHealthCheck extends HealthCheck
{
    public CommandServerServiceHealthCheck()
    {
        super("cmd-server-health-check");
    }

    @Override
    protected Result check()
            throws Exception
    {
        // TODO (MP) finish this.
        return Result.healthy();
    }
}
