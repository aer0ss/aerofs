package com.aerofs.ritual_notification;

import com.aerofs.base.net.AddressResolverHandler;
import com.aerofs.lib.BlockIncomingMessagesHandler;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.MagicPrepender;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;

import javax.inject.Inject;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

public class RitualNotificationServer
{
    private final RitualNotifier _ritualNotifier = new RitualNotifier();
    private final ServerBootstrap _bootstrap;
    private Channel _channel;
    private final RitualNotificationSystemConfiguration _config;

    @Inject
    public RitualNotificationServer(ServerSocketChannelFactory serverSocketChannelFactory,
            RitualNotificationSystemConfiguration config)
    {
        ServerBootstrap bootstrap = new ServerBootstrap(serverSocketChannelFactory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
                    throws Exception
            {
                return Channels.pipeline(
                        new AddressResolverHandler(newSingleThreadExecutor()),
                        new MagicPrepender(LibParam.RITUAL_NOTIFICATION_MAGIC),
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
        _channel = _bootstrap.bind(_config.getAddress()); // resolves inline
    }

    public void stop_()
    {
        if (_channel != null) _channel.close();
        _ritualNotifier.shutdown();
    }

    public void addListener(IRitualNotificationClientConnectedListener listener)
    {
        _ritualNotifier.addListener(listener);
    }

    public void removeListener(IRitualNotificationClientConnectedListener listener)
    {
        _ritualNotifier.removeListener(listener);
    }

    /**
     * @return an instance of {@code RitualNotifier}. IMPORTANT: DO NOT CACHE THIS VALUE!
     * It is implementation-dependent as to whether you will get the same instance of {@code RitualNotifier}
     * on multiple calls of this method, or different instances; either way, its behavior
     * will be correct.
     */
    public RitualNotifier getRitualNotifier()
    {
        return _ritualNotifier;
    }
}