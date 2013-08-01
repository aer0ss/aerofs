/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server;

import com.aerofs.base.Loggers;
import com.aerofs.devman.server.config.DeviceManagementServiceConfiguration;
import com.aerofs.devman.server.resources.DeviceManagementServiceHealthCheck;
import com.aerofs.devman.server.resources.DevicesResource;
import com.aerofs.devman.server.resources.PollingIntervalResource;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;
import org.slf4j.Logger;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.fasterxml.jackson.databind.PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES;

/**
 * Represents the device management REST service.
 */
public final class DeviceManagementService extends Service<DeviceManagementServiceConfiguration>
{
    private static final Logger l = Loggers.getLogger(DeviceManagementService.class);

    @Override
    public void initialize(Bootstrap<DeviceManagementServiceConfiguration> bootstrap)
    {
        bootstrap.setName("device-management");
    }

    @Override
    public void run(DeviceManagementServiceConfiguration configuration, Environment environment)
            throws Exception
    {
        final PooledJedisConnectionProvider provider = new PooledJedisConnectionProvider();
        final JedisThreadLocalTransaction trans = new JedisThreadLocalTransaction(provider);

        l.warn("redis host=" + configuration.getRedisConfiguration().getHost() + " port=" +
                configuration.getRedisConfiguration().getPort());
        provider.init_(
                configuration.getRedisConfiguration().getHost(),
                (short) configuration.getRedisConfiguration().getPort());

        l.warn("verkehr host=" + configuration.getVerkehrConfiguration().getHost() + " port=" +
                configuration.getVerkehrConfiguration().getPort());
        final VerkehrWebClient vkclient = new VerkehrWebClient(
                configuration.getVerkehrConfiguration().getHost(),
                configuration.getVerkehrConfiguration().getPort());

        final VerkehrPuller puller = new VerkehrPuller(vkclient, trans);

        // Last seen time  poller.
        l.warn("puller initDelay=" +
                configuration.getPollingConfiguration().getInitialDelayInSeconds() + " interval=" +
                configuration.getPollingConfiguration().getIntervalInSeconds());
        ScheduledExecutorService scheduledExecutorService =
                environment.managedScheduledExecutorService("last-seen-time-poller", 1);
        scheduledExecutorService.scheduleAtFixedRate(
                puller,
                configuration.getPollingConfiguration().getInitialDelayInSeconds(),
                configuration.getPollingConfiguration().getIntervalInSeconds(),
                TimeUnit.SECONDS);

        environment.getObjectMapperFactory().setPropertyNamingStrategy(CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);

        // REST API stuff.
        environment.addResource(new DevicesResource(trans));
        environment.addResource(
                new PollingIntervalResource(configuration.getPollingConfiguration().getIntervalInSeconds()));

        // Health check.
        environment.addHealthCheck(new DeviceManagementServiceHealthCheck());
    }
}
