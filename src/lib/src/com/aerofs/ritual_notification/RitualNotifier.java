package com.aerofs.ritual_notification;

import com.aerofs.base.Loggers;
import com.aerofs.proto.RitualNotifications.PBNotification;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;

import java.net.SocketAddress;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.Lists.newCopyOnWriteArrayList;
import static com.google.common.collect.Sets.newCopyOnWriteArraySet;

@org.jboss.netty.channel.ChannelHandler.Sharable
public class RitualNotifier extends SimpleChannelHandler
{
    private static final Logger l = Loggers.getLogger(RitualNotifier.class);

    private final Set<Channel> _channels = newCopyOnWriteArraySet();
    private final List<IRitualNotificationClientConnectedListener> _listeners = newCopyOnWriteArrayList();

    //
    // utility
    //

    private static String clientString(Channel channel)
    {
        return clientString(channel.getId(), channel.getRemoteAddress());
    }

    private static String clientString(Integer channelId, SocketAddress remoteAddress)
    {
        return "client id:" + channelId + " rem:" + remoteAddress;
    }

    private static void addWriteFailureFuture(ChannelFuture writeFuture)
    {
        final Channel channel = writeFuture.getChannel();

        writeFuture.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
                    throws Exception
            {
                if (!channelFuture.isSuccess()) {
                    l.warn("fail write notification to " + clientString(channel));
                    channel.close();
                }
            }
        });
    }

    //
    // listeners
    //

    void addListener(IRitualNotificationClientConnectedListener listener)
    {
        _listeners.add(listener);
    }

    void removeListener(IRitualNotificationClientConnectedListener listener)
    {
        _listeners.remove(listener);
    }

    //
    // channel connection (i.e. new notification clients) methods
    //

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        final Channel channel = e.getChannel();
        final Integer channelId = channel.getId();
        final SocketAddress remoteAddress = channel.getRemoteAddress();

        boolean added = _channels.add(channel);
        if (!added) {
            throw new ExDuplicateNotificationClient(clientString(channelId, remoteAddress));
        }

        channel.getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception
            {
                boolean removed = _channels.remove(channel);
                if (!removed) return;

                l.info("del " + clientString(channelId, remoteAddress));
            }
        });

        l.info("add " + clientString(channelId, remoteAddress));

        for (IRitualNotificationClientConnectedListener listener : _listeners) {
            listener.onNotificationClientConnected();
        }

        l.info("notify of connection " + clientString(channelId, remoteAddress));
    }

    //
    // notification methods
    //

    public void sendNotification(PBNotification notification)
    {
        for (Channel channel : _channels) {
            ChannelFuture writeFuture = channel.write(notification);
            addWriteFailureFuture(writeFuture);
        }
    }

    //
    // error-handling
    //

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception
    {
        l.warn("caught err:{}", e.getCause());
        ctx.getChannel().close();
    }
}
