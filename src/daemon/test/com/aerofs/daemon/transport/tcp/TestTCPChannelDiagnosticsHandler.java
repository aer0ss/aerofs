/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.transport.lib.ChannelData;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.handlers.HandlerMode;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.proto.Diagnostics.ChannelState;
import com.aerofs.proto.Diagnostics.TCPChannel;
import com.aerofs.rocklog.Defect;
import com.aerofs.rocklog.RockLog;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DefaultWriteCompletionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.UpstreamMessageEvent;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TestTCPChannelDiagnosticsHandler
{
    private final InetSocketAddress remoteAddress = InetSocketAddress.createUnresolved("aerofs.com", 9999);
    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
    private final Channel channel = mock(Channel.class);
    private final ChannelPipeline pipeline = mock(ChannelPipeline.class);
    private final ChannelFuture closeFuture = Channels.future(channel);
    private final IOStatsHandler ioStatsHandler = new IOStatsHandler(new TransportStats());
    private final WriteCompletionEvent writeCompletionEvent = new DefaultWriteCompletionEvent(channel, 192);
    private final ChannelBuffer buffer = ChannelBuffers.copiedBuffer(new byte[]{0x00, 0x00});
    private final MessageEvent messageEvent = new UpstreamMessageEvent(channel, buffer, remoteAddress);
    private final RockLog rockLog = mock(RockLog.class);
    private final Defect defect = mock(Defect.class);

    // set in tests
    private HandlerMode handlerMode;
    private TCPChannelDiagnosticsHandler handler;

    @Before
    public void setup()
            throws Exception
    {
        // setup some
        when(channel.getPipeline()).thenReturn(pipeline);
        when(channel.getCloseFuture()).thenReturn(closeFuture);
        when(channel.getRemoteAddress()).thenReturn(remoteAddress);

        when(pipeline.get(IOStatsHandler.class)).thenReturn(ioStatsHandler);

        doNothing().when(ctx).sendUpstream(any(ChannelEvent.class));
        doNothing().when(ctx).sendDownstream(any(ChannelEvent.class));

        // setup rocklog
        when(rockLog.newDefect(anyString())).thenReturn(defect);
        when(defect.addData(anyString(), anyString())).thenReturn(defect);
        doNothing().when(defect).send();

        // setup the IOStats handler
        ioStatsHandler.writeComplete(ctx, writeCompletionEvent);
        ioStatsHandler.messageReceived(ctx, messageEvent);
    }

    //
    // these tests are run with HandlerMade = CLIENT
    //

    @Test
    public void shouldReturnValidDiagnosticsMessageWhenConnecting()
    {
        handlerMode = HandlerMode.CLIENT;
        handler = new TCPChannelDiagnosticsHandler(handlerMode, rockLog);
        checkValidPBMessage(handler.getDiagnostics(channel), ChannelState.CONNECTING, handlerMode);
    }

    @Test
    public void shouldReturnValidDiagnosticsMessageWhenVerified()
    {
        // pretend that we're verified
        when(channel.getAttachment()).thenReturn(new ChannelData(UserID.DUMMY, DID.generate()));

        // now check the diagnostics message
        handlerMode = HandlerMode.CLIENT;
        handler = new TCPChannelDiagnosticsHandler(handlerMode, rockLog);
        checkValidPBMessage(handler.getDiagnostics(channel), ChannelState.VERIFIED, handlerMode);
    }

    @Test
    public void shouldReturnValidDiagnosticsMessageWhenClosed()
    {
        // trip the close future
        closeFuture.setSuccess();

        // now check the diagnostics message
        handlerMode = HandlerMode.CLIENT;
        handler = new TCPChannelDiagnosticsHandler(handlerMode, rockLog);
        checkValidPBMessage(handler.getDiagnostics(channel), ChannelState.CLOSED, handlerMode);
    }

    @Test
    public void shouldReturnValidDiagnosticsMessageWhenRemoteAddressIsNull()
    {
        // pretend to return a null remote address (still not sure why this happens)
        when(channel.getRemoteAddress()).thenReturn(null);

        // pretend that we're connecting, but, for some reason the remote address is null
        handlerMode = HandlerMode.CLIENT;
        handler = new TCPChannelDiagnosticsHandler(handlerMode, rockLog);
        checkValidPBMessage(handler.getDiagnostics(channel), ChannelState.CONNECTING, handlerMode, null);
    }

    //
    // run one test with the server mode
    //

    @Test
    public void shouldReturnValidDiagnosticsMessageWhenVerifiedInServerMode()
    {
        // pretend that we're verified
        when(channel.getAttachment()).thenReturn(new ChannelData(UserID.DUMMY, DID.generate()));

        // now check the diagnostics message
        HandlerMode handlerMode = HandlerMode.SERVER;
        handler = new TCPChannelDiagnosticsHandler(handlerMode, rockLog);
        checkValidPBMessage(handler.getDiagnostics(channel), ChannelState.VERIFIED, handlerMode);
    }

    private void checkValidPBMessage(TCPChannel message, ChannelState expectedState, HandlerMode mode)
    {
        checkValidPBMessage(message, expectedState, mode, remoteAddress);
    }

    private void checkValidPBMessage(TCPChannel message, ChannelState expectedState, HandlerMode mode, @Nullable InetSocketAddress remoteAddress)
    {
        assertThat(message.getState(), equalTo(expectedState));
        assertThat(message.getBytesSent(), equalTo(writeCompletionEvent.getWrittenAmount()));
        assertThat(message.getBytesReceived(), equalTo((long) buffer.readableBytes()));
        assertThat(message.hasLifetime(), equalTo(true));
        assertThat(message.getOriginator(), equalTo(mode == HandlerMode.CLIENT));

        // check the address
        if (remoteAddress != null) {
            assertThat(message.getRemoteAddress().getHost(), equalTo(remoteAddress.getHostName()));
            assertThat(message.getRemoteAddress().getPort(), equalTo(remoteAddress.getPort()));
        } else {
            assertThat(message.hasRemoteAddress(), equalTo(false));
        }
    }
}
