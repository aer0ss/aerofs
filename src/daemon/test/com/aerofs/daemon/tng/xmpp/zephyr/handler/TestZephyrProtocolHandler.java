package com.aerofs.daemon.tng.xmpp.zephyr.handler;

import com.aerofs.daemon.tng.xmpp.netty.MockChannelEventSink;
import com.aerofs.daemon.tng.xmpp.netty.MockChannelFactory;
import com.aerofs.daemon.tng.xmpp.netty.MockSinkEventListener;
import com.aerofs.daemon.tng.xmpp.zephyr.IZephyrUnicastEventSink;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrBindRequest;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrRegistrationMessage;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.testlib.AbstractTest;
import org.jboss.netty.channel.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.mockito.Mockito.verify;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestZephyrProtocolHandler extends AbstractTest
{
    SocketAddress address;
    @Mock IZephyrUnicastEventSink sink;
    ChannelFactory defaultChannelFactory;
    ChannelPipeline pipeline;
    UncancellableFuture<Void> closeFuture;

    @Before
    public void setUp()
    {
        address = new InetSocketAddress("localhost", 3000);

        closeFuture = UncancellableFuture.createCloseFuture();
        pipeline = new StrictChannelPipeline();
        pipeline.addLast("handler", new ZephyrProtocolHandler(sink, closeFuture));

        defaultChannelFactory = new MockChannelFactory(new MockChannelEventSink(new MockSinkEventListener()));
    }

    @Test
    public void shouldStartConnectingGetRegisteredBindAndCompleteConnectionThenClose() throws Exception
    {
        Channel channel = defaultChannelFactory.newChannel(pipeline);

        // Connect to Zephyr. This should not complete until fully bound
        ChannelFuture future = channel.connect(address);
        assertFalse(future.isDone());

        // Fire the registration message the server would send
        Channels.fireMessageReceived(channel, new ZephyrRegistrationMessage(1));
        verify(sink).onChannelRegisteredWithZephyr_(1);

        // We still should not be connected
        assertFalse(future.isDone());

        // Send the bind request to the server
        ChannelFuture bindFuture = Channels.write(channel, new ZephyrBindRequest(2));
        assertTrue(bindFuture.isSuccess());

        // Now the connection should be successful
        assertTrue(future.isSuccess());

        // And close
        ChannelFuture close = channel.close();
        assertTrue(close.isDone());
        assertTrue(close.isSuccess());
    }

    @Test
    public void shouldStartConnectingAndHandleError() throws Exception
    {
        Channel channel = defaultChannelFactory.newChannel(pipeline);

        // Connect to Zephyr. This should not complete until fully bound
        ChannelFuture future = channel.connect(address);
        assertFalse(future.isDone());

        // Simulate an exception
        Channels.fireExceptionCaught(channel, new Exception("Test failure"));

        assertTrue(future.isDone());
        assertFalse(future.isSuccess());
        assertFalse(channel.isOpen());
    }

    @Test
    public void shouldStartConnectingGetRegisteredAttemptBindAndHandleError() throws Exception
    {
        ChannelFactory factory = new MockChannelFactory(new MockChannelEventSink(new MockSinkEventListener() {

            @Override
            public void writeRequested(MessageEvent e) throws Exception
            {
                Throwable cause = new Exception("Test failure");
                e.getFuture().setFailure(cause);
                Channels.fireExceptionCaught(e.getChannel(), cause);
            }

        }));

        Channel channel = factory.newChannel(pipeline);

        // Connect to Zephyr. This should not complete until fully bound
        ChannelFuture future = channel.connect(address);
        assertFalse(future.isDone());

        // Fire the registration message the server would send
        Channels.fireMessageReceived(channel, new ZephyrRegistrationMessage(1));
        verify(sink).onChannelRegisteredWithZephyr_(1);

        // We still should not be connected
        assertFalse(future.isDone());

        // Send the bind request to the server
        ChannelFuture bindFuture = Channels.write(channel, new ZephyrBindRequest(2));

        assertTrue(bindFuture.isDone());
        assertFalse(bindFuture.isSuccess());
    }

    @Test
    public void shouldStartConnectingAndManuallyClose() throws Exception
    {
        Channel channel = defaultChannelFactory.newChannel(pipeline);

        // Connect to Zephyr. This should not complete until fully bound
        ChannelFuture future = channel.connect(address);
        assertFalse(future.isDone());

        // Manually close the channel before successful connection
        ChannelFuture close = channel.close();
        assertTrue(close.isDone());
        assertTrue(close.isSuccess());

        // Check that the connecting future is also set
        assertTrue(future.isDone());
        assertFalse(future.isSuccess());
    }
}
