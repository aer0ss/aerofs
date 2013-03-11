/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server;

import com.yammer.metrics.core.HealthCheck;

public final class DeviceManagementServiceHealthCheck extends HealthCheck
{
    public DeviceManagementServiceHealthCheck()
    {
        super("device-management-health-check");
    }

    @Override
    protected Result check()
            throws Exception
    {
        // TODO (MP) finish this.
        return Result.healthy();
    }
}
