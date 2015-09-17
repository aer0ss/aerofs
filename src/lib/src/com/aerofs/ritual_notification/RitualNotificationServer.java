package com.aerofs.ritual_notification;

import com.aerofs.base.Loggers;
import com.aerofs.lib.BlockIncomingMessagesHandler;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.MagicPrepender;
import com.aerofs.lib.nativesocket.AbstractNativeSocketPeerAuthenticator;
import com.aerofs.lib.nativesocket.NativeSocketHelper;
import com.aerofs.lib.nativesocket.RitualNotificationSocketFile;
import com.google.inject.Inject;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;
import org.newsclub.net.unix.NativeSocketAddress;
import org.slf4j.Logger;

import java.io.File;

public class RitualNotificationServer
{
    private static final Logger l = Loggers.getLogger(RitualNotificationServer.class);

    private final RitualNotifier _ritualNotifier = new RitualNotifier();
    private final File _rnsSocketFile;
    private final ServerBootstrap _bootstrap;
    private Channel _channel;

    @Inject
    public RitualNotificationServer(RitualNotificationSocketFile rnsf,
            final AbstractNativeSocketPeerAuthenticator authenticator)
    {
        _rnsSocketFile = rnsf.get();
        ChannelPipelineFactory _pipelineFactory = () -> (Channels.pipeline(
                authenticator,
                new MagicPrepender(LibParam.RITUAL_NOTIFICATION_MAGIC),
                new BlockIncomingMessagesHandler(),
                new LengthFieldPrepender(4),
                new ProtobufEncoder(),
                _ritualNotifier));
        _bootstrap = NativeSocketHelper.createServerBootstrap(_rnsSocketFile, _pipelineFactory);
    }

    public void start_()
    {
        l.info("Starting Ritual Notification server");
        _channel = _bootstrap.bind(new NativeSocketAddress(_rnsSocketFile));
        l.info("Ritual Notification server bound to {}", _rnsSocketFile);
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