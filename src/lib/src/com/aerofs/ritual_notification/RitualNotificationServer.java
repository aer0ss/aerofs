package com.aerofs.ritual_notification;

import com.aerofs.base.net.AddressResolverHandler;
import com.aerofs.lib.BlockIncomingMessagesHandler;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.MagicHandler;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;

import javax.inject.Inject;
import java.net.InetSocketAddress;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

public class RitualNotificationServer
{
    private final RitualNotifier _ritualNotifier;
    private final ServerBootstrap _bootstrap;
    private final RitualNotificationSystemConfiguration _config;

    @Inject
    public RitualNotificationServer(ServerSocketChannelFactory serverSocketChannelFactory,
            RitualNotifier notifier, RitualNotificationSystemConfiguration config)
    {
        this._ritualNotifier = notifier;

        ServerBootstrap bootstrap = new ServerBootstrap(serverSocketChannelFactory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
                    throws Exception
            {
                return Channels.pipeline(
                        new AddressResolverHandler(newSingleThreadExecutor()),
                        new MagicHandler(LibParam.RITUAL_NOTIFICATION_MAGIC),
                        new BlockIncomingMessagesHandler(),
                        new LengthFieldPrepender(4),
                        new ProtobufEncoder(),
                        _ritualNotifier);
            }
        });
        bootstrap.setOption("SO_REUSEADDR", true);

        this._bootstrap = bootstrap;
        this._config = config;
    }

    public void start_()
    {
        _bootstrap.bind(new InetSocketAddress(_config.getAddress().getHostName(),
                _config.getPort())); // resolves inline
    }

    public RitualNotifier getRitualNotifier()
    {
        return _ritualNotifier;
    }
}