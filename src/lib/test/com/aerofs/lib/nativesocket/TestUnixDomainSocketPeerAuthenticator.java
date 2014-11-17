/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.nativesocket;

import com.aerofs.lib.os.IOSUtil;
import com.aerofs.testlib.AbstractTest;
import com.flipkart.phantom.netty.common.OioAcceptedSocketChannel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.UpstreamChannelStateEvent;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.newsclub.net.unix.NativeSocketAddress;

import java.io.File;
import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestUnixDomainSocketPeerAuthenticator extends AbstractTest
{
    @Mock IOSUtil _iosUtil;

    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
    private final OioAcceptedSocketChannel channel = mock(OioAcceptedSocketChannel.class);
    private final ChannelFuture closeFuture = Channels.future(channel);
    private final ChannelPipeline pipeline = mock(ChannelPipeline.class);
    private final ChannelStateEvent openEvent =
            new UpstreamChannelStateEvent(channel, ChannelState.OPEN, Boolean.TRUE);
    private UnixDomainSockPeerAuthenticator _unixDomainSockPeerAuthenticator;
    private ChannelStateEvent connectedEvent;


    @Before
    public void setup() throws Exception
    {
        // Ensures tests run only on Unix/Mac. Cannot use OSUtil here since its not initialized.
        // This test suite is not relevant on Windows because for WNPs, we provide a security
        // descriptor that should stop any client from obtaining a handle to the pipe.
        Assume.assumeFalse("Test is meant only for *nix platform",
                System.getProperty("os.name").toLowerCase().contains("win"));

        TemporaryFolder tempFolder = new TemporaryFolder();
        tempFolder.create();
        File tempSocketFile = tempFolder.newFile("tempSocketFile.sock");
        // For a CONNECTED event, we must provide a SocketAddress as the 3rd value
        // in the constructor.
        connectedEvent = new UpstreamChannelStateEvent(channel, ChannelState.CONNECTED,
                new NativeSocketAddress(tempSocketFile));

        when(ctx.getChannel()).thenReturn(channel);
        when(ctx.getPipeline()).thenReturn(pipeline);
        when(channel.getCloseFuture()).thenReturn(closeFuture);
        when(pipeline.getLast()).thenReturn(mock(ChannelHandler.class));

        _unixDomainSockPeerAuthenticator = new UnixDomainSockPeerAuthenticator(_iosUtil);
        _unixDomainSockPeerAuthenticator.channelOpen(ctx, openEvent);
    }

    @Test(expected = NativeSocketUnknownPeerException.class)
    public void shouldThrowIfServerUidNotEqualClientUid()
            throws IOException
    {
        // Make client program's uid != server program's uid.
        when(channel.getPeerUID()).thenReturn(1);
        when(_iosUtil.getUserUid()).thenReturn(0);
        _unixDomainSockPeerAuthenticator.channelConnected(ctx, connectedEvent);
    }

    @Test
    public void shouldSendChannelEventUpstreamIfServerUidEqualsClientUid()
            throws IOException
    {
        // Make client program's uid = server program's uid.
        when(channel.getPeerUID()).thenReturn(1);
        when(_iosUtil.getUserUid()).thenReturn(1);
        _unixDomainSockPeerAuthenticator.channelConnected(ctx, connectedEvent);
        // If channel context sendUpstream is called, uid check was passed.
        verify(ctx, times(1)).sendUpstream(connectedEvent);
    }

}