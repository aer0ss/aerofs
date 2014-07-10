/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.transport.lib.ChannelData;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.handlers.HandlerMode;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.j.Jid;
import com.aerofs.proto.Diagnostics.ChannelState;
import com.aerofs.proto.Diagnostics.JingleChannel;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TestJingleChannelDiagnosticsHandler
{
    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
    private final Channel channel = mock(Channel.class);
    private final ChannelPipeline pipeline = mock(ChannelPipeline.class);
    private final ChannelFuture closeFuture = Channels.future(channel);
    private final IOStatsHandler ioStatsHandler = new IOStatsHandler(new TransportStats());
    private final WriteCompletionEvent writeCompletionEvent = new DefaultWriteCompletionEvent(channel, 92);
    private final ChannelBuffer buffer = ChannelBuffers.copiedBuffer(new byte[]{0x00, 0x00, 0x00});
    private final MessageEvent messageEvent = new UpstreamMessageEvent(channel, buffer, new JingleAddress(DID.generate(), mock(Jid.class)));
    private final IRoundTripTimes roundTripTimes = mock(IRoundTripTimes.class);

    // set in tests
    private HandlerMode handlerMode;
    private JingleChannelDiagnosticsHandler handler;

    @Before
    public void setup()
            throws Exception
    {
        // setup some default stuff
        when(channel.getPipeline()).thenReturn(pipeline);
        when(channel.getCloseFuture()).thenReturn(closeFuture);

        when(pipeline.get(IOStatsHandler.class)).thenReturn(ioStatsHandler);

        doNothing().when(ctx).sendUpstream(any(ChannelEvent.class));
        doNothing().when(ctx).sendDownstream(any(ChannelEvent.class));

        // setup the IOStats handler
        ioStatsHandler.writeComplete(ctx, writeCompletionEvent);
        ioStatsHandler.messageReceived(ctx, messageEvent);

        handler = new JingleChannelDiagnosticsHandler(handlerMode, roundTripTimes);
    }

    //
    // these tests are run with HandlerMade = CLIENT
    //

    @Test
    public void shouldReturnValidDiagnosticsMessageWhenConnecting()
    {
        handlerMode = HandlerMode.CLIENT;
        checkValidPBMessage(handler.getDiagnostics(channel), ChannelState.CONNECTING, handlerMode);
    }

    @Test
    public void shouldReturnValidDiagnosticsMessageWhenVerified()
    {
        // pretend that we're verified
        when(channel.getAttachment()).thenReturn(new ChannelData(UserID.DUMMY, DID.generate()));

        // now check the diagnostics message
        handlerMode = HandlerMode.CLIENT;
        checkValidPBMessage(handler.getDiagnostics(channel), ChannelState.VERIFIED, handlerMode);
    }

    @Test
    public void shouldReturnValidDiagnosticsMessageWhenClosed()
    {
        // trip the close future
        closeFuture.setSuccess();

        // now check the diagnostics message
        handlerMode = HandlerMode.CLIENT;
        checkValidPBMessage(handler.getDiagnostics(channel), ChannelState.CLOSED, handlerMode);
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
        checkValidPBMessage(handler.getDiagnostics(channel), ChannelState.VERIFIED, handlerMode);
    }

    private void checkValidPBMessage(JingleChannel message, ChannelState expectedState, HandlerMode mode)
    {
        assertThat(message.getState(), equalTo(expectedState));
        assertThat(message.getBytesSent(), equalTo(writeCompletionEvent.getWrittenAmount()));
        assertThat(message.getBytesReceived(), equalTo((long) buffer.readableBytes()));
        assertThat(message.hasLifetime(), equalTo(true));
        assertThat(message.getOriginator(), equalTo(mode == HandlerMode.CLIENT));
    }
}
