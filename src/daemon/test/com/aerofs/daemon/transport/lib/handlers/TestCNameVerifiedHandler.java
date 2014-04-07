/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.testlib.LoggerSetup;
import com.aerofs.daemon.transport.lib.ChannelData;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.google.common.collect.Lists;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.UpstreamChannelStateEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.InetSocketAddress;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class TestCNameVerifiedHandler
{
    static
    {
        LoggerSetup.init();
    }

    private static final InetSocketAddress REMOTE = InetSocketAddress.createUnresolved("remote", 9999);

    private final IUnicastListener unicastListener = mock(IUnicastListener.class);
    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
    private final ChannelPipeline pipeline = mock(ChannelPipeline.class);
    private final Channel channel = mock(Channel.class);
    private final ChannelFuture closeFuture = Channels.future(channel);
    private final UserID userID = UserID.DUMMY;
    private final DID did = DID.generate();
    private final ChannelStateEvent openEvent = new UpstreamChannelStateEvent(channel, ChannelState.OPEN, Boolean.TRUE);
    private final ChannelData channelData = new ChannelData(userID, did);
    private final List<ChannelStateEvent> events = Lists.newLinkedList();

    @Before
    public void setup()
            throws Exception
    {
        when(ctx.getChannel()).thenReturn(channel);
        when(ctx.getPipeline()).thenReturn(pipeline);
        when(channel.getPipeline()).thenReturn(pipeline);
        when(channel.getCloseFuture()).thenReturn(closeFuture);

        // the first time we try to get the attachment it's to check that it doesn't exist
        // on subsequent calls we return the expected ChannelData (note, we set this in advance - I'll check that it matches in the tests)
        when(channel.getAttachment()).thenReturn(null).thenReturn(channelData);

        // store all events we attempt to send
        doAnswer(new Answer()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Object[] args = invocation.getArguments();
                events.add((ChannelStateEvent) args[0]);
                return null;
            }
        }).when(ctx).sendUpstream(Matchers.<ChannelStateEvent>any());
    }

    @Test
    public void shouldForwardChannelConnectedEventAndNotifyListenerIfPeerVerified()
            throws Exception
    {
        CNameVerifiedHandler verifiedHandler = new CNameVerifiedHandler(unicastListener, HandlerMode.CLIENT);

        // we're in the client pipeline, so we expect the remote DID to be set
        verifiedHandler.setExpectedRemoteDID(did);
        // then, let's fire the channelOpen event to indicate that the pipeline was created
        verifiedHandler.channelOpen(ctx, openEvent);

        // first, let's indicate that the peer was verified
        verifiedHandler.onPeerVerified(userID, did);

        // now, simulate a ChannelConnected event
        ChannelStateEvent connectedEvent = new UpstreamChannelStateEvent(channel, ChannelState.CONNECTED, REMOTE);
        verifiedHandler.channelConnected(ctx, connectedEvent);

        InOrder inOrder = inOrder(channel, unicastListener, ctx);
        inOrder.verify(channel).setAttachment(channelData);
        inOrder.verify(unicastListener).onDeviceConnected(did);
        inOrder.verify(ctx).sendUpstream(any(ChannelEvent.class));

        verify(unicastListener).onDeviceConnected(did);
        verify(channel).setAttachment(channelData);

        assertThat(events, hasSize(2));
        assertThat(events.get(0), equalTo(openEvent));
        assertThat(events.get(1).getState(), equalTo(ChannelState.CONNECTED));
        assertThat((InetSocketAddress) events.get(1).getValue(), equalTo(REMOTE));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowIfExpectedDIDSetOnServerCNameVerifiedHandler()
    {
        CNameVerifiedHandler verifiedHandler = new CNameVerifiedHandler(unicastListener, HandlerMode.SERVER);
        verifiedHandler.setExpectedRemoteDID(did); // you cannot set expected remote DID if this handler is in the server pipeline
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowInClientPipelineOnlyIfReceivedDIDEqualToExpectedDID()
            throws Exception
    {
        CNameVerifiedHandler verifiedHandler = new CNameVerifiedHandler(unicastListener, HandlerMode.CLIENT);

        // we're in the client pipeline, so we expect the remote DID to be set (this is a random DID)
        verifiedHandler.setExpectedRemoteDID(DID.generate());
        // then, let's fire the channelOpen event to indicate that the pipeline was created
        verifiedHandler.channelOpen(ctx, openEvent);

        // now, let's indicate that the peer was verified
        verifiedHandler.onPeerVerified(userID, did);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowIfChannelConnectedEventIsReceivedBeforePeerVerified()
            throws Exception
    {
        CNameVerifiedHandler verifiedHandler = new CNameVerifiedHandler(unicastListener, HandlerMode.CLIENT);

        // we're in the client pipeline, so we expect the remote DID to be set
        verifiedHandler.setExpectedRemoteDID(did);
        // then, let's fire the channelOpen event to indicate that the pipeline was created
        verifiedHandler.channelOpen(ctx, openEvent);

        // now, before verification let's fire the ChannelConnected event
        ChannelStateEvent connectedEvent = new UpstreamChannelStateEvent(channel, ChannelState.CONNECTED, REMOTE);
        verifiedHandler.channelConnected(ctx, connectedEvent);
    }
}
