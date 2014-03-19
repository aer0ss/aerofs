/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.google.protobuf.Message;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;

/**
 * Implemented by classes that can fill out
 * the information required by diagnostics.proto channel handler messages.
 */
public interface IChannelDiagnosticsHandler extends ChannelHandler
{
    Message getDiagnostics(Channel channel);
}
