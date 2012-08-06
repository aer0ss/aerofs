package com.aerofs.daemon.transport.xmpp.zephyr.client.netty;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;

public interface IZephyrIOEventSink
{
    void onChannelSendComplete_(Channel channel, long length);
    void onChannelConnected_(Channel channel);
    void onChannelDisconnected_(Channel channel);
    void onMessageReceivedFromChannel_(Channel channel, ChannelBuffer data);
    void onChannelRegisteredWithZephyr_(Channel channel, int zid);
    void onChannelBoundWithZephyr_(Channel channel);
    void onChannelException_(Channel channel, Exception e);
}
