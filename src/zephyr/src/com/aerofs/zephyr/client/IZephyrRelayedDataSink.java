package com.aerofs.zephyr.client;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;

public interface IZephyrRelayedDataSink
{
    void onDataReceived(Channel channel, ChannelBuffer data);
}
