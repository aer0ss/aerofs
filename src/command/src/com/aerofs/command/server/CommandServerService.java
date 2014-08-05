/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server;

import com.aerofs.base.BaseParam.Verkehr;
import com.aerofs.base.Loggers;
import com.aerofs.command.server.config.CommandServerServiceConfiguration;
import com.aerofs.command.server.resources.CommandTypesResource;
import com.aerofs.command.server.resources.DevicesResource;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import com.google.common.util.concurrent.MoreExecutors;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.slf4j.Logger;

import java.util.concurrent.Executors;

import static com.fasterxml.jackson.databind.PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

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

        l.warn("redis host={} port={}",
                configuration.getRedisConfiguration().getHost(),
                configuration.getRedisConfiguration().getPort());

        provider.init_(
                configuration.getRedisConfiguration().getHost(),
                configuration.getRedisConfiguration().getPort());

        l.warn("verkehr host={} port={}",
                configuration.getVerkehrConfiguration().getHost(),
                configuration.getVerkehrConfiguration().getPort());

        NioClientSocketChannelFactory channelFactory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors .newCachedThreadPool(), 1, 2);
        VerkehrClient verkehrClient = VerkehrClient.create(
                Verkehr.HOST,
                Verkehr.REST_PORT,
                MILLISECONDS.convert(30, SECONDS),
                MILLISECONDS.convert(60, SECONDS),
                10,
                new HashedWheelTimer(),
                MoreExecutors.sameThreadExecutor(),
                channelFactory);

        environment.getObjectMapperFactory().setPropertyNamingStrategy(CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
        environment.addResource(new DevicesResource(trans, verkehrClient));
        environment.addResource(new CommandTypesResource());
    }
}
