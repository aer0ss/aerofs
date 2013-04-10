/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server;

import com.aerofs.base.Loggers;
import com.aerofs.command.server.config.CommandServerServiceConfiguration;
import com.aerofs.command.server.resources.CommandServerServiceHealthCheck;
import com.aerofs.command.server.resources.CommandTypesResource;
import com.aerofs.command.server.resources.DevicesResource;
import com.aerofs.servlets.lib.NoopConnectionListener;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;
import org.jboss.netty.util.HashedWheelTimer;
import org.slf4j.Logger;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.aerofs.sp.server.lib.SPParam.VERKEHR_ACK_TIMEOUT;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_RECONNECT_DELAY;
import static com.fasterxml.jackson.databind.PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

/**
 * Represents the device management REST service.
 */
public final class CommandServerService extends Service<CommandServerServiceConfiguration>
{
    private static final Logger l = Loggers.getLogger(CommandServerService.class);

    @Override
    public void initialize(Bootstrap<CommandServerServiceConfiguration> bootstrap)
    {
        bootstrap.setName("cmd-server");
    }

    @Override
    public void run(CommandServerServiceConfiguration configuration, Environment environment)
            throws Exception
    {
        final PooledJedisConnectionProvider provider = new PooledJedisConnectionProvider();
        final JedisThreadLocalTransaction trans = new JedisThreadLocalTransaction(provider);

        l.warn("redis host=" + configuration.getRedisConfiguration().getHost() + " port=" +
                configuration.getRedisConfiguration().getPort());
        provider.init_(
                configuration.getRedisConfiguration().getHost(),
                configuration.getRedisConfiguration().getPort());

        l.warn("verkehr host=" + configuration.getVerkehrConfiguration().getHost() + " port=" +
                configuration.getVerkehrConfiguration().getPort());
        Executor boss = Executors.newCachedThreadPool();
        Executor workers = Executors.newCachedThreadPool();
        HashedWheelTimer timer = new HashedWheelTimer();
        com.aerofs.verkehr.client.lib.admin.ClientFactory adminFactory =
                new com.aerofs.verkehr.client.lib.admin.ClientFactory(
                        configuration.getVerkehrConfiguration().getHost(),
                        configuration.getVerkehrConfiguration().getPort(),
                        boss,
                        workers,
                        configuration.getVerkehrConfiguration().getCertFile(),
                        VERKEHR_RECONNECT_DELAY,
                        VERKEHR_ACK_TIMEOUT,
                        timer,
                        new NoopConnectionListener(),
                        sameThreadExecutor());
        VerkehrAdmin verkehrAdmin = adminFactory.create();
        verkehrAdmin.start();

        environment.getObjectMapperFactory().setPropertyNamingStrategy(CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);

        // REST API stuff.
        environment.addResource(new DevicesResource(trans, verkehrAdmin));
        environment.addResource(new CommandTypesResource());

        // TODO (MP) add get and ack resources.

        // Health check.
        environment.addHealthCheck(new CommandServerServiceHealthCheck());
    }
}
