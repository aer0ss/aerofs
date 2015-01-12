/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server;

import com.aerofs.base.BaseParam;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.baseline.AdminEnvironment;
import com.aerofs.baseline.RootEnvironment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.ServiceEnvironment;
import com.aerofs.baseline.Managed;
import com.aerofs.command.server.config.CommandServerConfiguration;
import com.aerofs.command.server.resources.CommandSubmissionResource;
import com.aerofs.command.server.resources.CommandTypesResource;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import com.google.common.util.concurrent.MoreExecutors;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;

import java.util.Properties;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class CommandServer extends Service<CommandServerConfiguration> {

    public static void main(String args[]) throws Exception {
        // make an empty Properties object so the verkehr config client won't NPE
        ConfigurationProperties.setProperties(new Properties());
        System.err.println("starting up");

        // start up the command server
        CommandServer commandServer = new CommandServer();
        commandServer.run(args);
    }

    public CommandServer() {
        super("command");
    }


    @Override
    public void init(CommandServerConfiguration configuration, RootEnvironment root, AdminEnvironment admin, ServiceEnvironment service) throws Exception {
        // print some config info
        LOGGER.info("redis host={} port={}", configuration.getRedis().getHost(), configuration.getRedis().getPort());
        LOGGER.info("verkehr host={} port={}", configuration.getVerkehr().getHost(), configuration.getVerkehr().getPort());

        // create the verkehr client
        final VerkehrClient verkehrClient = VerkehrClient.create(
                BaseParam.Verkehr.HOST,
                BaseParam.Verkehr.REST_PORT,
                MILLISECONDS.convert(30, SECONDS),
                MILLISECONDS.convert(60, SECONDS),
                10,
                new HashedWheelTimer(),
                MoreExecutors.sameThreadExecutor(),
                new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), 1, 2));

        // have baseline manage its lifecycle
        root.addManaged(new Managed() {
            @Override
            public void start() throws Exception {
                // noop
            }

            @Override
            public void stop() {
                verkehrClient.shutdown();
            }
        });

        // create the jedis connection objects
        PooledJedisConnectionProvider provider = new PooledJedisConnectionProvider();
        provider.init_(configuration.getRedis().getHost(), configuration.getRedis().getPort());
        JedisThreadLocalTransaction transaction = new JedisThreadLocalTransaction(provider);

        // configure the service injector
        service.addProvider(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(verkehrClient).to(VerkehrClient.class); // automatically singleton
                bind(transaction).to(JedisThreadLocalTransaction.class); // automatically singleton
            }
        });

        // add the resources we're exposing
        service.addResource(CommandTypesResource.class);
        service.addResource(CommandSubmissionResource.class);
    }
}