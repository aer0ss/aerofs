package com.aerofs.daemon.tng.xmpp.zephyr.handler;

import com.aerofs.daemon.tng.xmpp.netty.ChannelStateMatcher;
import com.aerofs.daemon.tng.xmpp.netty.MockChannelEventSink;
import com.aerofs.daemon.tng.xmpp.netty.MockChannelFactory;
import com.aerofs.daemon.tng.xmpp.netty.MockSinkEventListener;
import com.aerofs.testlib.AbstractTest;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.net.InetSocketAddress;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class TestProxyPipeline extends AbstractTest
{
    @Captor ArgumentCaptor<ChannelHandlerContext> ctxCaptor;
    @Captor ArgumentCaptor<ChannelEvent> eventCaptor;

    Channel channel;
    ChannelUpstreamHandler mockHandler;

    @Before
    public void setUp() throws Exception
    {
        final String proxyHost = "localhost";
        final int proxyPort = 3129;

        MockSinkEventListener listener = new MockSinkEventListener() {

            @Override
            public void writeRequested(MessageEvent e) throws Exception
            {
                assertTrue(e.getMessage() instanceof HttpRequest);

                HttpRequest request = (HttpRequest) e.getMessage();
                assertEquals(HttpMethod.CONNECT, request.getMethod());

                super.writeRequested(e);
            }

            @Override
            public void connectRequested(ChannelStateEvent e) throws Exception
            {
                InetSocketAddress address = (InetSocketAddress) e.getValue();
                assertEquals(proxyHost, address.getHostName());
                assertEquals(proxyPort, address.getPort());

                super.connectRequested(e);
            }

        };

        // Create the mock handler to verify calls on
        mockHandler = mock(ChannelUpstreamHandler.class);

        // Create the handler pipeline
        ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addLast("proxy", new ConnectionProxyHandler(
                new InetSocketAddress(proxyHost, proxyPort)));
        pipeline.addLast("connect", new ConnectTunnelHandler());
        pipeline.addLast("mock", mockHandler);

        MockChannelEventSink sink = new MockChannelEventSink(listener);
        ChannelFactory factory = new MockChannelFactory(sink);

        channel = factory.newChannel(pipeline);
    }

    @Test
    public void shouldInterceptAndMakeHttpConnectRequest() throws Exception
    {
        // Connect to some fake server
        ChannelFuture f = channel.connect(
                new InetSocketAddress("localhost", 443));

        // Verify that, because no response was received from the proxy yet,
        // no channelConnected event was sent to the mock
        verify(mockHandler, never()).handleUpstream(
                any(ChannelHandlerContext.class),
                argThat(new ChannelStateMatcher(
                        ChannelState.CONNECTED)));

        // Respond as the proxy, saying the connection was successful
        Channels.fireMessageReceived(channel, new DefaultHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK));

        // Verify that the future succeeded
        assertTrue(f.isSuccess());

        // Verify that the channelConnected event was propagated
        verify(mockHandler, atLeastOnce()).handleUpstream(
                ctxCaptor.capture(),
                eventCaptor.capture());

        assertTrue(eventCaptor.getValue() instanceof ChannelStateEvent);
        ChannelStateEvent e = (ChannelStateEvent) eventCaptor.getValue();

        assertEquals(ChannelState.CONNECTED, e.getState());
        assertNotNull(e.getValue());

        // Cleanup
        channel.disconnect().awaitUninterruptibly();
    }

    @Test
    public void shouldInterceptAndMakeHttpConnectRequestButFail() throws Exception
    {
        ChannelFuture f = channel.connect(
                new InetSocketAddress("localhost", 443));

        // Verify that, because no response was received from the proxy yet,
        // no channelConnected event was sent to the mock
        verify(mockHandler, never()).handleUpstream(
                any(ChannelHandlerContext.class),
                argThat(new ChannelStateMatcher(ChannelState.CONNECTED)));

        // Respond as the proxy, saying the connection was successful
        Channels.fireMessageReceived(channel, new DefaultHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE));

        // Make sure the future failed
        assertTrue(f.isDone());
        assertFalse(f.isSuccess());

        // Verify that the channelConnected event was propagated
        verify(mockHandler, atLeastOnce()).handleUpstream(
                ctxCaptor.capture(),
                eventCaptor.capture());

        assertTrue(eventCaptor.getValue() instanceof ExceptionEvent);

        // Cleanup
        channel.disconnect().awaitUninterruptibly();
    }

}
