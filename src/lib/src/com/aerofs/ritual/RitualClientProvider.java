package com.aerofs.ritual;

import com.aerofs.base.Loggers;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.nativesocket.NativeSocketHelper;
import com.aerofs.lib.nativesocket.RitualSocketFile;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.newsclub.net.unix.NativeSocketAddress;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

import static com.aerofs.lib.LibParam.Ritual.LENGTH_FIELD_SIZE;
import static com.aerofs.lib.LibParam.Ritual.MAX_FRAME_LENGTH;
import static com.google.common.base.Preconditions.checkNotNull;

public final class RitualClientProvider implements IRitualClientProvider
{
    //
    // all fields from this point on protected by 'this'
    //

    private static final Logger l = Loggers.getLogger(RitualClientProvider.class);

    private final File _ritualSocketFile;
    private final ClientBootstrap _bootstrap;

    private @Nullable Channel _ritualChannel;
    private @Nullable RitualClient _ritualNonblockingClient;
    private @Nullable RitualBlockingClient _ritualBlockingClient;

    private final ChannelPipelineFactory _pipelineFactory = () -> (Channels.pipeline(
            new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, LENGTH_FIELD_SIZE, 0, LENGTH_FIELD_SIZE),
            new LengthFieldPrepender(LENGTH_FIELD_SIZE),
            new RitualClientHandler()));

    public RitualClientProvider(RitualSocketFile rsf)
    {
        _ritualSocketFile = rsf.get();
        _bootstrap = NativeSocketHelper.createClientBootstrap(_pipelineFactory);
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
            _ritualChannel = _bootstrap.connect(new NativeSocketAddress(_ritualSocketFile)).getChannel();
            RitualClientHandler handler = getRitualClientHandler(_ritualChannel);
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
            _ritualNonblockingClient = new RitualClient(handler);
            _ritualBlockingClient = new RitualBlockingClient(handler);
        }
    }

    /**
     * Return the instance of {@link RitualClientHandler} in the pipeline
     */
    private static RitualClientHandler getRitualClientHandler(Channel channel)
    {
        return (RitualClientHandler)channel.getPipeline().getLast();
    }
}
