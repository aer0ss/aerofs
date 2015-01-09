/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.tunnel;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.NettyUtil;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.testlib.AbstractBaseTest;
import com.aerofs.testlib.TempCert;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.Future;

import static java.util.concurrent.Executors.newCachedThreadPool;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestTunnel extends AbstractBaseTest
{
    private static TempCert caCert;
    private static TempCert serverCert;
    private static TempCert clientCert;
    private static SSLEngineFactory clientSslEngineFactory;

    protected static final UserID user = UserID.fromInternal("foo@bar.baz");
    protected static final DID did = DID.generate();

    private static final byte[] MSG_HELLO = new byte[] {'H', 'e', 'l', 'l', 'o'};
    private static final byte[] MSG_WORLD = new byte[] {'W', 'o', 'r', 'l', 'd'};

    private static final ClientSocketChannelFactory clientChannelFactory =
            new NioClientSocketChannelFactory(newCachedThreadPool(), newCachedThreadPool(), 1, 2);

    @BeforeClass
    public static void generateCert()
    {
        caCert = TempCert.generateCA();
        clientCert = TempCert.generateDaemon(user, did, caCert);
        serverCert = TempCert.generateDaemon(UserID.DUMMY, new DID(UniqueID.ZERO), caCert);
        clientSslEngineFactory =
                new SSLEngineFactory(Mode.Client, Platform.Desktop, clientCert, caCert, null);
    }

    @AfterClass
    public static void cleanupCert()
    {
        caCert.cleanup();
    }

    private Timer timer = new HashedWheelTimer();
    private int port;

    private ServerConnectionWatcher serverConnections;
    private VirtualConnectionWatcher serverVirtualConnections;
    private VirtualConnectionWatcher clientVirtualConnections;

    @Before
    public void setUp() throws Exception
    {
        serverConnections = new ServerConnectionWatcher(timer, serverCert, caCert);
        serverVirtualConnections = new VirtualConnectionWatcher();
        clientVirtualConnections = new VirtualConnectionWatcher();
        serverConnections.server.start();
        port = serverConnections.server.getListeningPort();
    }

    @After
    public void tearDown() throws Exception
    {
        serverConnections.server.stop();
    }

    static class Tunnel<C, S>
    {
        final C client;
        final S server;

        private Tunnel(C client, S server)
        {
            this.client = client;
            this.server = server;
        }
    }

    /**
     * Create a physical tunnel connection
     */
    Tunnel<Channel, TunnelHandler> makePhysical() throws Exception
    {
        ChannelFuture cf = new TunnelClient("127.0.0.1", port, user, did,
                clientChannelFactory,
                clientSslEngineFactory,
                () -> Channels.pipeline(clientVirtualConnections.handler),
                timer).connect();
        Channel c = cf.getChannel();
        cf.awaitUninterruptibly();
        return new Tunnel<>(c, serverConnections.nextConnection().get());
    }

    /**
     * Create a virtual tunnel connection, on top of a physical one
     */
    Tunnel<Channel, Channel> makeVirtual(TunnelHandler h) throws Exception
    {
        Channel c = h.newVirtualChannel(Channels.pipeline(serverVirtualConnections.handler));
        // the connection is only open on the other side when the first packet is sent
        c.write(ChannelBuffers.EMPTY_BUFFER);
        return new Tunnel<>(clientVirtualConnections.nextConnection().get(), c);
    }

    static void assertBufferEquals(byte[] expected, ChannelBuffer actual)
    {
        assertArrayEquals(expected, NettyUtil.toByteArray(actual));
    }

    @Test
    public void shouldOpenTunnelConnection() throws Exception
    {
        Tunnel<Channel, TunnelHandler> t = makePhysical();
        assertTrue(t.client.isConnected());
        assertTrue(t.server._channel.isConnected());
    }

    @Test
    public void shouldCloseTunnelConnectionFromClient() throws Exception
    {
        Tunnel<Channel, TunnelHandler> t = makePhysical();

        t.client.close().awaitUninterruptibly();
        t.server._channel.getCloseFuture().awaitUninterruptibly();

        // RACE CONDITION: close future is complete before state flip
        //assertFalse(t.client.isConnected());
        //assertFalse(t.server._channel.isConnected());
    }

    @Test
    public void shouldCloseTunnelConnectionFromServer() throws Exception
    {
        Tunnel<Channel, TunnelHandler> t = makePhysical();

        t.server._channel.close().awaitUninterruptibly();
        t.client.getCloseFuture().awaitUninterruptibly();

        // RACE CONDITION: close future is complete before state flip
        //assertFalse(t.client.isConnected());
        //assertFalse(t.server._channel.isConnected());
    }

    @Test
    public void shouldOpenVirtualConnection() throws Exception
    {
        Tunnel<Channel, TunnelHandler> p = makePhysical();
        Tunnel<Channel, Channel> v = makeVirtual(p.server);

        assertTrue(v.client.isConnected());
        assertTrue(v.server.isConnected());
    }

    @Test
    public void shouldCloseVirtualConnectionFromClient() throws Exception
    {
        Tunnel<Channel, TunnelHandler> p = makePhysical();
        Tunnel<Channel, Channel> v = makeVirtual(p.server);

        assertTrue(v.client.isConnected());
        assertTrue(v.server.isConnected());

        v.client.close().awaitUninterruptibly();
        v.server.getCloseFuture().awaitUninterruptibly();

        assertFalse(v.client.isConnected());
        assertFalse(v.server.isConnected());

        // check that the underlying physical tunnel is still connected
        assertTrue(p.client.isConnected());
        assertTrue(p.server._channel.isConnected());
    }

    @Test
    public void shouldCloseVirtualConnectionFromServer() throws Exception
    {
        Tunnel<Channel, TunnelHandler> p = makePhysical();
        Tunnel<Channel, Channel> v = makeVirtual(p.server);

        assertTrue(v.client.isConnected());
        assertTrue(v.server.isConnected());

        v.server.close().awaitUninterruptibly();
        v.client.getCloseFuture().awaitUninterruptibly();

        assertFalse(v.client.isConnected());
        assertFalse(v.server.isConnected());

        // check that the underlying physical tunnel is still connected
        assertTrue(p.client.isConnected());
        assertTrue(p.server._channel.isConnected());
    }

    @Test
    public void shouldWriteFromServer() throws Exception
    {
        Tunnel<Channel, TunnelHandler> p = makePhysical();
        Tunnel<Channel, Channel> v = makeVirtual(p.server);

        v.server.write(ChannelBuffers.wrappedBuffer(MSG_HELLO)).awaitUninterruptibly();

        assertBufferEquals(MSG_HELLO, clientVirtualConnections.messageReceived(v.client).get());
    }

    @Test
    public void shouldWriteFromClient() throws Exception
    {
        Tunnel<Channel, TunnelHandler> p = makePhysical();
        Tunnel<Channel, Channel> v = makeVirtual(p.server);

        v.client.write(ChannelBuffers.wrappedBuffer(MSG_HELLO)).awaitUninterruptibly();

        assertBufferEquals(MSG_HELLO, serverVirtualConnections.messageReceived(v.server).get());
    }

    @Test
    public void shouldSuspendResumeFromClient() throws Exception
    {
        Tunnel<Channel, TunnelHandler> p = makePhysical();
        Tunnel<Channel, Channel> v = makeVirtual(p.server);

        Future<Boolean> writable = serverVirtualConnections.interestChanged(v.server);
        v.client.setReadable(false).awaitUninterruptibly();
        assertFalse(writable.get());
        assertFalse(v.client.isReadable());

        writable = serverVirtualConnections.interestChanged(v.server);
        v.client.setReadable(true);
        assertTrue(writable.get());
        assertTrue(v.client.isReadable());
    }

    @Test
    public void shouldSuspendResumeFromServer() throws Exception
    {
        Tunnel<Channel, TunnelHandler> p = makePhysical();
        Tunnel<Channel, Channel> v = makeVirtual(p.server);

        Future<Boolean> writable = clientVirtualConnections.interestChanged(v.client);
        v.server.setReadable(false).awaitUninterruptibly();
        assertFalse(writable.get());
        assertFalse(v.server.isReadable());

        writable = clientVirtualConnections.interestChanged(v.client);
        v.server.setReadable(true);
        assertTrue(writable.get());
        assertTrue(v.server.isReadable());
    }

    @Test
    public void shouldSuspendResumeInterlaced() throws Exception
    {
        Tunnel<Channel, TunnelHandler> p = makePhysical();
        Tunnel<Channel, Channel> v = makeVirtual(p.server);

        Future<Boolean> clientWritable = clientVirtualConnections.interestChanged(v.client);
        v.server.setReadable(false).awaitUninterruptibly();
        assertFalse(clientWritable.get());
        assertFalse(v.server.isReadable());

        Future<Boolean> serverWritable = serverVirtualConnections.interestChanged(v.server);
        v.client.setReadable(false).awaitUninterruptibly();
        assertFalse(serverWritable.get());
        assertFalse(v.client.isReadable());

        clientWritable = clientVirtualConnections.interestChanged(v.client);
        v.server.setReadable(true);
        assertTrue(clientWritable.get());
        assertTrue(v.server.isReadable());

        serverWritable = serverVirtualConnections.interestChanged(v.server);
        v.client.setReadable(true);
        assertTrue(serverWritable.get());
        assertTrue(v.client.isReadable());
    }

    @Test
    public void shouldSuspendResumeNested() throws Exception
    {
        Tunnel<Channel, TunnelHandler> p = makePhysical();
        Tunnel<Channel, Channel> v = makeVirtual(p.server);

        Future<Boolean> clientWritable = clientVirtualConnections.interestChanged(v.client);
        v.server.setReadable(false).awaitUninterruptibly();
        assertFalse(clientWritable.get());
        assertFalse(v.server.isReadable());

        Future<Boolean> serverWritable = serverVirtualConnections.interestChanged(v.server);
        v.client.setReadable(false).awaitUninterruptibly();
        assertFalse(serverWritable.get());
        assertFalse(v.client.isReadable());

        serverWritable = serverVirtualConnections.interestChanged(v.server);
        v.client.setReadable(true);
        assertTrue(serverWritable.get());
        assertTrue(v.client.isReadable());

        clientWritable = clientVirtualConnections.interestChanged(v.client);
        v.server.setReadable(true);
        assertTrue(clientWritable.get());
        assertTrue(v.server.isReadable());
    }

    @Test
    public void shouldMultiplexMessages() throws Exception
    {
        Tunnel<Channel, TunnelHandler> p = makePhysical();
        Tunnel<Channel, Channel> v0 = makeVirtual(p.server);
        Tunnel<Channel, Channel> v1 = makeVirtual(p.server);

        v0.client.write(ChannelBuffers.wrappedBuffer(MSG_HELLO));
        v0.server.write(ChannelBuffers.wrappedBuffer(MSG_WORLD));

        v1.server.write(ChannelBuffers.wrappedBuffer(MSG_HELLO));
        v1.client.write(ChannelBuffers.wrappedBuffer(MSG_WORLD));

        assertBufferEquals(MSG_WORLD, serverVirtualConnections.messageReceived(v1.server).get());
        assertBufferEquals(MSG_HELLO, clientVirtualConnections.messageReceived(v1.client).get());

        assertBufferEquals(MSG_HELLO, serverVirtualConnections.messageReceived(v0.server).get());
        assertBufferEquals(MSG_WORLD, clientVirtualConnections.messageReceived(v0.client).get());
    }

    @Test
    public void shouldCloseVirtualConnectionsWhenPhysicalConnectionClosedFromClient() throws Exception
    {
        Tunnel<Channel, TunnelHandler> p = makePhysical();
        Tunnel<Channel, Channel> v0 = makeVirtual(p.server);
        Tunnel<Channel, Channel> v1 = makeVirtual(p.server);

        p.client.close().awaitUninterruptibly();

        v0.client.getCloseFuture().awaitUninterruptibly();
        v0.server.getCloseFuture().awaitUninterruptibly();
        v1.client.getCloseFuture().awaitUninterruptibly();
        v1.server.getCloseFuture().awaitUninterruptibly();
    }

    @Test
    public void shouldCloseVirtualConnectionsWhenPhysicalConnectionClosedFromServer() throws Exception
    {
        Tunnel<Channel, TunnelHandler> p = makePhysical();
        Tunnel<Channel, Channel> v0 = makeVirtual(p.server);
        Tunnel<Channel, Channel> v1 = makeVirtual(p.server);

        p.server._channel.close().awaitUninterruptibly();

        v0.client.getCloseFuture().awaitUninterruptibly();
        v0.server.getCloseFuture().awaitUninterruptibly();
        v1.client.getCloseFuture().awaitUninterruptibly();
        v1.server.getCloseFuture().awaitUninterruptibly();
    }

    @Test
    public void shouldSplitLargePayloads() throws Exception
    {
        Tunnel<Channel, TunnelHandler> p = makePhysical();
        Tunnel<Channel, Channel> v = makeVirtual(p.server);

        ChannelBuffer large = ChannelBuffers.wrappedBuffer(new byte[TunnelHandler.MAX_MESSAGE_SIZE + 1]);
        large.setByte(0, 0);
        large.setByte(255, 255);
        large.setByte(TunnelHandler.MAX_MESSAGE_SIZE, 42);

        v.client.write(large).awaitUninterruptibly();

        ChannelBuffer chunk0 = serverVirtualConnections.messageReceived(v.server).get();
        ChannelBuffer chunk1 = serverVirtualConnections.messageReceived(v.server).get();
        assertBufferEquals(large.array(), ChannelBuffers.wrappedBuffer(chunk0, chunk1));
    }
}
