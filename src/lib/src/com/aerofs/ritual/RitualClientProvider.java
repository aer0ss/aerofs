package com.aerofs.ritual;

import com.aerofs.base.net.AddressResolverHandler;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

import static com.aerofs.lib.LibParam.Ritual.LENGTH_FIELD_SIZE;
import static com.aerofs.lib.LibParam.Ritual.MAX_FRAME_LENGTH;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

public final class RitualClientProvider implements IRitualClientProvider
{
    private final ClientBootstrap _bootstrap;

    //
    // all fields from this point on protected by 'this'
    //

    private @Nullable Channel _ritualChannel;
    private @Nullable RitualClient _ritualNonblockingClient;
    private @Nullable RitualBlockingClient _ritualBlockingClient;

    public RitualClientProvider(ClientSocketChannelFactory clientSocketChannelFactory)
    {
        ClientBootstrap bootstrap = new ClientBootstrap(clientSocketChannelFactory);
        bootstrap.setPipelineFactory(() -> Channels.pipeline(
                new AddressResolverHandler(newSingleThreadExecutor()),
                new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, LENGTH_FIELD_SIZE, 0, LENGTH_FIELD_SIZE),
                new LengthFieldPrepender(LENGTH_FIELD_SIZE),
                new RitualClientHandler()));

        _bootstrap = bootstrap;
    }

    @Override
    public synchronized RitualBlockingClient getBlockingClient()
    {
        setupRitualClients();
        return checkNotNull(_ritualBlockingClient);
    }

    @Override
    public synchronized RitualClient getNonBlockingClient()
    {
        setupRitualClients();
        return checkNotNull(_ritualNonblockingClient);
    }

    /**
     * Sets up the ritual blocking and non-blocking clients if necessary
     * <strong>Synchronize on 'this' before calling this method</strong>
     */
    private void setupRitualClients()
    {
        if (_ritualChannel == null) {
            InetSocketAddress unresolved = InetSocketAddress.createUnresolved(
                    LibParam.LOCALHOST_ADDR.getHostName(), Cfg.port(PortType.RITUAL));
            ChannelFuture future = _bootstrap.connect(unresolved);

            _ritualChannel = future.getChannel();
            _ritualChannel.getCloseFuture().addListener(new ChannelFutureListener()
            {
                @Override
                public void operationComplete(ChannelFuture channelFuture)
                        throws Exception
                {
                    synchronized (this) {
                        _ritualChannel = null;
                        _ritualNonblockingClient = null;
                        _ritualBlockingClient = null;
                    }
                }
            });

            RitualClientHandler handler = getRitualClientHandler(_ritualChannel);

            _ritualNonblockingClient = new RitualClient(handler);
            _ritualBlockingClient = new RitualBlockingClient(handler);
        }
    }

    /**
     * Return the instance of {@link RitualClientHandler} in the pipeline
     */
    private static RitualClientHandler getRitualClientHandler(Channel channel)
    {
        return (RitualClientHandler) channel.getPipeline().getLast();
    }
}
