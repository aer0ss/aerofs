package com.aerofs.ritual_notification;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.TimerUtil;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.net.AbstractNettyReconnectingClient;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.nativesocket.NativeSocketHelper;
import com.aerofs.lib.nativesocket.RitualNotificationSocketFile;
import com.aerofs.lib.notifier.Notifier;
import com.aerofs.proto.RitualNotifications.PBNotification;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufDecoder;
import org.newsclub.net.unix.NativeSocketAddress;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

public class RitualNotificationClient extends AbstractNettyReconnectingClient
{
    private static final Logger l = Loggers.getLogger(RitualNotificationClient.class);

    private final Executor _executor = sameThreadExecutor();
    private final Notifier<IRitualNotificationListener> _listeners = Notifier.create();
    private final File _rnsSocketFile;
    private final ClientBootstrap _bootstrap;

    public RitualNotificationClient(RitualNotificationSocketFile rnsf)
    {
        super(TimerUtil.getGlobalTimer());
        _rnsSocketFile = rnsf.get();
        _bootstrap = NativeSocketHelper.createClientBootstrap(pipelineFactory());
    }

    /**
     * Call this method _before_ starting the daemon or _before_ calling start() to avoid missing
     * events.
     */
    public void addListener(IRitualNotificationListener l)
    {
        _listeners.addListener(l, _executor);
    }

    public void removeListener(IRitualNotificationListener l)
    {
        _listeners.removeListener(l);
    }

    @Override
    protected ChannelFuture connect()
    {
        return _bootstrap.connect(new NativeSocketAddress(_rnsSocketFile));
    }

    @Override
    protected ChannelPipelineFactory pipelineFactory()
    {
        return () -> Channels.pipeline(
                new MagicFrameDecoder(ClientParam.RITUAL_NOTIFICATION_MAGIC),
                new ProtobufDecoder(PBNotification.getDefaultInstance()),
                new NotificationHandler()
        );
    }

    @Override
    public String toString()
    {
        return "RNC";
    }

    private static class MagicFrameDecoder extends LengthFieldBasedFrameDecoder
    {
        private final int _magic;

        public MagicFrameDecoder(int magic)
        {
            super(Integer.MAX_VALUE, 4, 4, 0, 8);
            _magic = magic;
        }

        @Override
        protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer)
                throws Exception
        {
            int magic = buffer.getInt(0);
            if (magic != _magic) {
                throw new ExProtocolError("bad magic exp:" + _magic + " act:" + magic);
            }
            return super.decode(ctx, channel, buffer);
        }
    }

    private class NotificationHandler extends SimpleChannelUpstreamHandler
    {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        {
            l.info("ex:", BaseLogUtil.suppress(e.getCause(), java.net.SocketException.class));
            ctx.getChannel().close();
            SystemUtil.fatalOnUncheckedException(e.getCause());
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) throws IOException
        {
            final PBNotification notification = (PBNotification)me.getMessage();
            _listeners.notifyOnOtherThreads(
                    listener -> listener.onNotificationReceived(notification));
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
        {
            _listeners.notifyOnOtherThreads(
                    IRitualNotificationListener::onNotificationChannelBroken);
        }
    }
}
