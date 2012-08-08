package com.aerofs.daemon.tng.xmpp.zephyr;

import com.aerofs.daemon.core.net.link.INetworkLinkStateService;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.ImmediateInlineExecutor;
import com.aerofs.daemon.tng.base.WireData;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.daemon.tng.xmpp.ISignallingService;
import com.aerofs.daemon.tng.xmpp.ISignallingService.SignallingMessage;
import com.aerofs.daemon.tng.xmpp.netty.MockChannelEventSink;
import com.aerofs.daemon.tng.xmpp.netty.MockChannelFactory;
import com.aerofs.daemon.tng.xmpp.netty.MockSinkEventListener;
import com.aerofs.daemon.tng.xmpp.zephyr.exception.ExZephyrFailedToBind;
import com.aerofs.daemon.tng.xmpp.zephyr.handler.StrictChannelPipeline;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrBindRequest;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrDataMessage;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrRegistrationMessage;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.id.DID;
import com.aerofs.proto.Transport;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.aerofs.proto.Transport.PBZephyrCandidateInfo;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.security.Permission;
import java.util.Iterator;

import static com.aerofs.testlib.FutureAssert.assertNoThrow;
import static com.aerofs.testlib.FutureAssert.assertThrows;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestZephyrUnicastConnection extends AbstractTest
{
    final int LOCAL_ZID = 1;
    final int REMOTE_ZID = 2;

    InetSocketAddress zephyrAddress;
    ChannelPipeline pipeline;
    ISingleThreadedPrioritizedExecutor executor;

    @Mock DID localDID;
    @Mock DID remoteDID;
    @Mock INetworkLinkStateService networkLinkStateService;
    @Mock ISignallingService signallingService;

    @Captor ArgumentCaptor<DID> didCaptor;
    @Captor ArgumentCaptor<PBTPHeader> messageCaptor;

    @Before
    public void setUp()
            throws Exception
    {
        when(localDID.toString()).thenReturn("<This Device>");
        when(remoteDID.toString()).thenReturn("<Remote Device>");
        when(signallingService.sendSignallingMessage_(any(SignallingMessage.class)))
                              .thenReturn(UncancellableFuture.createSucceeded((Void) null));
        zephyrAddress = new InetSocketAddress("localhost", 3000);
        pipeline = new StrictChannelPipeline();
        executor = new ImmediateInlineExecutor();

        // Installed to catch System.exit() for Util.fatal()
        System.setSecurityManager(new NoExitSecurityManager());
    }

    @After
    public void tearDown()
            throws Exception
    {
        System.setSecurityManager(null);
    }

    private ZephyrUnicastConnection createConnection(MockSinkEventListener listener)
    {
        return ZephyrUnicastConnection.getInstance_(executor, localDID, remoteDID, zephyrAddress,
                new MockChannelFactory(new MockChannelEventSink(listener)), pipeline,
            networkLinkStateService, signallingService);
    }

    @Test
    public void shouldStartConnectingThenExplicitlyDisconnect() throws Exception
    {
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(LOCAL_ZID));
            }
        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        ListenableFuture<Void> disconnectFuture = connection.disconnect_(
                new ExTransport("Test close"));

        assertThrows(disconnectFuture, ExTransport.class, "Test close");
        assertThrows(connectFuture, ExTransport.class, "Test close");
        assertThrows(connection.getCloseFuture_(), ExTransport.class, "Test close");

        verify(signallingService).sendSignallingMessage_(argThat(isSignallingMessage(remoteDID, LOCAL_ZID)));
    }

    @Test
    public void shouldStartConnectingThenFail()
            throws Exception
    {
        final OutArg<Channel> channelOut = new OutArg<Channel>(null);
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);

                channelOut.set(e.getChannel());
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(LOCAL_ZID));
            }
        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        Channels.fireExceptionCaught(channelOut.get(), new IOException("Test failure"));

        assertThrows(connectFuture, IOException.class, "Test failure");
        assertThrows(connection.getCloseFuture_(), IOException.class, "Test failure");

        verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID)));
    }

    @Test
    public void shouldStartConnectingThenAttemptToSendAndFail() throws Exception
    {
        final OutArg<Channel> channelOut = new OutArg<Channel>(null);
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                channelOut.set(e.getChannel());
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(LOCAL_ZID));
            }
        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        ListenableFuture<Void> sendFuture = connection.send_(
                new byte[][]{"test".getBytes()}, Prio.LO);
        assertThrows(sendFuture, IllegalStateException.class);

        assertFalse(connectFuture.isDone());
        assertFalse(connection.getCloseFuture_().isDone());

        verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID)));
    }

    @Test
    public void shouldStartConnectingThenAttemptToReceiveAndFail() throws Exception
    {
        final OutArg<Channel> channelOut = new OutArg<Channel>(null);
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                channelOut.set(e.getChannel());
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(LOCAL_ZID));
            }
        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        ListenableFuture<ImmutableList<WireData>> recvFuture = connection.receive_();
        assertThrows(recvFuture, IllegalStateException.class);

        assertFalse(connectFuture.isDone());
        assertFalse(connection.getCloseFuture_().isDone());

        verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID)));
    }

    @Test
    public void shouldStartConnectingAndFailToSignalPeer()
            throws Exception
    {
        when(signallingService.sendSignallingMessage_(any(SignallingMessage.class))).thenReturn(
                UncancellableFuture.<Void>createFailed(new Exception("SignallingFailed")));

        final OutArg<Channel> channelOut = new OutArg<Channel>(null);
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                channelOut.set(e.getChannel());
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(LOCAL_ZID));
            }

        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertTrue(connectFuture.isDone());

        assertThrows(connectFuture, Exception.class, "SignallingFailed");
    }

    private static SignallingMessage buildKnowledgeMessage(DID did, int localZid, int remoteZid)
    {
        return new SignallingMessage(did, Transport.PBTPHeader.newBuilder()
                .setType(Type.ZEPHYR_CANDIDATE_INFO)
                .setZephyrInfo(PBZephyrCandidateInfo.newBuilder()
                        .setDestinationZephyrId(localZid)
                        .setSourceZephyrId(remoteZid))
                .build());
    }

    private static SignallingMessage buildRemoteInitiatedKnowledgeMessage(DID did, int remoteZid)
    {
        return new SignallingMessage(did, Transport.PBTPHeader.newBuilder()
                .setType(Type.ZEPHYR_CANDIDATE_INFO)
                .setZephyrInfo(PBZephyrCandidateInfo.newBuilder()
                        .setSourceZephyrId(remoteZid))
                .build());
    }

    @Test
    public void shouldStartConnectingAndFailToBind() throws Exception
    {
        final OutArg<Channel> channelOut = new OutArg<Channel>(null);
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                channelOut.set(e.getChannel());
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(LOCAL_ZID));
            }

            @Override
            public void writeRequested(MessageEvent e)
                    throws Exception
            {
                Exception cause = new IOException("Could not write to channel");
                e.getFuture().setFailure(cause);
                Channels.fireExceptionCaught(e.getChannel(), cause);
            }
        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        // Simulate the remote peer signalling back
        connection.processSignallingMessage_(buildKnowledgeMessage(remoteDID, LOCAL_ZID, REMOTE_ZID));

        assertThrows(connectFuture, ExZephyrFailedToBind.class);
        assertThrows(connection.getCloseFuture_(), ExZephyrFailedToBind.class);
        assertTrue(channelOut.get().getCloseFuture().isDone());

        InOrder ordered = inOrder(signallingService);
        ordered.verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID)));
        ordered.verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID, REMOTE_ZID)));
    }

    @Test
    public void shouldConnectSuccessfullyAndDisconnect()
            throws Exception
    {
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(1));
            }

        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        // Simulate the remote peer signalling back
        connection.processSignallingMessage_(buildKnowledgeMessage(remoteDID, LOCAL_ZID, REMOTE_ZID));

        assertNoThrow(connectFuture);
        assertFalse(connection.getCloseFuture_().isDone());

        InOrder ordered = inOrder(signallingService);
        ordered.verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID)));
        ordered.verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID, REMOTE_ZID)));

        ListenableFuture<Void> disconnectFuture = connection.disconnect_(
                new Exception("Test close"));
        assertThrows(disconnectFuture, Exception.class, "Test close");
        assertThrows(connection.getCloseFuture_(), Exception.class, "Test close");
    }

    @Test
    public void shouldConnectAndFailUponSend() throws Exception
    {
        final OutArg<Channel> channelOut = new OutArg<Channel>(null);
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                channelOut.set(e.getChannel());
                Channels.fireMessageReceived(e.getChannel(), new ZephyrRegistrationMessage(1));
            }

        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        // Simulate the remote peer signalling back
        connection.processSignallingMessage_(buildKnowledgeMessage(remoteDID, LOCAL_ZID, REMOTE_ZID));

        assertNoThrow(connectFuture);
        assertFalse(connection.getCloseFuture_().isDone());
        assertFalse(channelOut.get().getCloseFuture().isDone());

        InOrder ordered = inOrder(signallingService);
        ordered.verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID)));
        ordered.verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID, REMOTE_ZID)));

        Channels.fireExceptionCaught(channelOut.get(), new IOException("Test fail"));

        ListenableFuture<Void> sendFuture = connection.send_(new byte[][] { "test".getBytes() }, Prio.LO);
        assertThrows(sendFuture, IOException.class, "Test fail");
    }

    @Test
    public void shouldConnectAndFailUponReceive() throws Exception
    {
        final OutArg<Channel> channelOut = new OutArg<Channel>(null);
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                throws Exception
            {
                super.connectRequested(e);
                channelOut.set(e.getChannel());
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(1));
            }

        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        // Simulate the remote peer signalling back
        connection.processSignallingMessage_(buildKnowledgeMessage(remoteDID, LOCAL_ZID, REMOTE_ZID));

        assertNoThrow(connectFuture);
        assertFalse(connection.getCloseFuture_().isDone());
        assertFalse(channelOut.get().getCloseFuture().isDone());

        InOrder ordered = inOrder(signallingService);
        ordered.verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID)));
        ordered.verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID, REMOTE_ZID)));

        Channels.fireExceptionCaught(channelOut.get(), new IOException("Test fail"));

        ListenableFuture<ImmutableList<WireData>> recvFuture = connection.receive_();
        assertThrows(recvFuture, IOException.class, "Test fail");
    }

    @Test
    public void shouldFailOnDoubleBind() throws Exception
    {
        final OutArg<Channel> outChannel = new OutArg<Channel>();
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                outChannel.set(e.getChannel());
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(1));
            }

        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        // Simulate the remote peer signalling back
        connection.processSignallingMessage_(buildKnowledgeMessage(remoteDID, LOCAL_ZID, REMOTE_ZID));
        assertNoThrow(connectFuture);

        InOrder ordered = inOrder(signallingService);
        ordered.verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID)));
        ordered.verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID, REMOTE_ZID)));

        try {
            // Simulate the remote peer signalling back again
            connection.processSignallingMessage_(buildKnowledgeMessage(remoteDID, LOCAL_ZID, REMOTE_ZID));
        } catch(AssertionError e) {
            assertTrue(e.getCause().getCause() instanceof NoExitSecurityManager.ExitException);
        }
    }

    @Test
    public void shouldConnectSendAndDisconnect()
            throws Exception
    {
        final OutArg<Channel> outChannel = new OutArg<Channel>();
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                outChannel.set(e.getChannel());
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(1));
            }

        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        // Simulate the remote peer signalling back
        connection.processSignallingMessage_(buildKnowledgeMessage(remoteDID, LOCAL_ZID, REMOTE_ZID));
        assertNoThrow(connectFuture);

        ListenableFuture<Void> sendFuture = connection.send_(new byte[][] { "test".getBytes() }, Prio.LO);
        assertNoThrow(sendFuture);

        ListenableFuture<Void> disconnectFuture = connection.disconnect_(new Exception("Done"));
        assertThrows(disconnectFuture, Exception.class, "Done");
        assertThrows(connection.getCloseFuture_(), Exception.class, "Done");
    }

    @Test
    public void shouldConnectRecvAndDisconnect()
            throws Exception
    {
        final OutArg<Channel> outChannel = new OutArg<Channel>();
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                outChannel.set(e.getChannel());
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(1));
            }

        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        // Simulate the remote peer signalling back
        connection.processSignallingMessage_(buildKnowledgeMessage(remoteDID, LOCAL_ZID, REMOTE_ZID));
        assertNoThrow(connectFuture);

        ListenableFuture<ImmutableList<WireData>> recvFuture = connection.receive_();
        assertFalse(recvFuture.isDone());

        final byte[] dataIn = "Hello".getBytes();
        Channels.fireMessageReceived(outChannel.get(), new ZephyrDataMessage(ChannelBuffers.copiedBuffer(dataIn)));
        assertNoThrow(recvFuture);

        Iterator<WireData> dataIter = recvFuture.get().iterator();
        assertArrayEquals(dataIn, dataIter.next().getData_());
        assertFalse(connection.getCloseFuture_().isDone());

        ListenableFuture<Void> disconnectFuture = connection.disconnect_(new Exception("Done"));
        assertThrows(disconnectFuture, Exception.class, "Done");
        assertThrows(connection.getCloseFuture_(), Exception.class, "Done");
    }

    @Test
    public void shouldAddSelfAsListenerForNetworkLinkStateEvents()
    {
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener());

        verify(networkLinkStateService).addListener_(connection, executor);
    }

    @Test
    public void shouldRemoveSelfAsNetworkLinkStateListenerWhenDisconnectIsCalledOnTheConnection()
    {
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(1));
            }

        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        connection.processSignallingMessage_(buildKnowledgeMessage(remoteDID, LOCAL_ZID, REMOTE_ZID));

        // we have now connected successfully

        connection.disconnect_(new ExTransport("explicit disconnect"));

        verify(networkLinkStateService).removeListener_(connection);
    }

    @Test
    public void shouldRemoveSelfAsNetworkLinkStateListenerWhenAnExceptionOccursWithinThePipeline()
    {
        final OutArg<Channel> channelRef = new OutArg<Channel>(null);
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                channelRef.set(e.getChannel());
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(1));
            }

        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        connection.processSignallingMessage_(buildKnowledgeMessage(remoteDID, LOCAL_ZID, REMOTE_ZID));

        // we have now connected successfully - fire an exception down the pipeline

        Channels.fireExceptionCaught(channelRef.get(), new ExTransport("pipeline-killing exception"));

        verify(networkLinkStateService).removeListener_(connection);
    }

    // FIXME: how do I verify that _all_ paths that teardown the connection will remove the connection
    // from the network link-state service?

    @Test
    // IMPORTANT: ideally we would put simulate two cases: one, where we are in the current set,
    // and one where we are not. This is only the "not" case. Unfortuantely, it is not possible to
    // simulate the other case. For this, we will have to think of another approach
    public void shouldInitiateDisconnectIfSetOfRemovedInterfacesIncludesOneWithAnIPTheConnectionIsUsing()
    {
        final OutArg<Channel> channelRef = new OutArg<Channel>();
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                channelRef.set(e.getChannel());
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(1));
            }

        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        // Simulate the remote peer signalling back
        connection.processSignallingMessage_(buildKnowledgeMessage(remoteDID, LOCAL_ZID, REMOTE_ZID));
        assertNoThrow(connectFuture);

        connection.onLinkStateChanged_(ImmutableSet.<NetworkInterface>of(),
                ImmutableSet.<NetworkInterface>of(), ImmutableSet.<NetworkInterface>of(),
                ImmutableSet.<NetworkInterface>of());

        assertThrows(connection.getCloseFuture_(), ExTransport.class);
    }

    @Test
    public void shouldConnectWhenPeerAttemptsConnectionSimultaneously()
            throws Exception
    {
        final OutArg<Channel> outChannel = new OutArg<Channel>();
        ZephyrUnicastConnection connection = createConnection(new MockSinkEventListener()
        {
            @Override
            public void connectRequested(ChannelStateEvent e)
                    throws Exception
            {
                super.connectRequested(e);
                outChannel.set(e.getChannel());
                Channels.fireMessageReceived(e.getChannel(),
                        new ZephyrRegistrationMessage(1));
            }

        });

        ListenableFuture<Void> connectFuture = connection.connect_();
        assertFalse(connectFuture.isDone());

        // Simulate the remote peer signalling, assuming the remote peer started the connection
        // process
        connection.processSignallingMessage_(
                buildRemoteInitiatedKnowledgeMessage(remoteDID, REMOTE_ZID));
        connection.processSignallingMessage_(
                buildKnowledgeMessage(remoteDID, LOCAL_ZID, REMOTE_ZID));
        assertNoThrow(connectFuture);

        InOrder ordered = inOrder(signallingService);
        ordered.verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID)));
        ordered.verify(signallingService).sendSignallingMessage_(
                argThat(isSignallingMessage(remoteDID, LOCAL_ZID, REMOTE_ZID)));
        ordered.verifyNoMoreInteractions();
    }

    private static ArgumentMatcher<SignallingMessage> isSignallingMessage(final DID did, final int source, final int destination)
    {
        return new ZephyrSignalMatcher(did, source, destination);
    }

    private static ArgumentMatcher<SignallingMessage> isSignallingMessage(final DID did, final int source)
    {
        return new ZephyrSignalMatcher(did, source);
    }

    private static class ZephyrSignalMatcher extends ArgumentMatcher<SignallingMessage>
    {
        private final boolean _hasDestination;
        private final int _source;
        private final int _destination;
        private final DID _did;

        public ZephyrSignalMatcher(DID did, int source)
        {
            _did = did;
            _hasDestination = false;
            _source = source;
            _destination = Constants.ZEPHYR_INVALID_CHAN_ID;
        }

        public ZephyrSignalMatcher(DID did, int source, int destination)
        {
            _did = did;
            _hasDestination = true;
            _source = source;
            _destination = destination;
        }

        @Override
        public boolean matches(Object argument)
        {
            if (!(argument instanceof SignallingMessage)) {
                return false;
            }

            SignallingMessage message = (SignallingMessage) argument;
            if (!message.did.equals(_did)) {
                return false;
            }

            PBTPHeader header = message.message;
            if (!header.hasZephyrInfo()) {
                return false;
            }

            if (!header.getZephyrInfo().hasSourceZephyrId()) {
                return false;
            }

            if (header.getZephyrInfo().getSourceZephyrId() != _source) {
                return false;
            }

            if (!_hasDestination) {
                if (header.getZephyrInfo().hasDestinationZephyrId()) {
                    return false;
                }
                return true;
            }

            if (!header.getZephyrInfo().hasDestinationZephyrId()) {
                return false;
            }

            if (header.getZephyrInfo().getDestinationZephyrId() != _destination) {
                return false;
            }

            return true;
        }
    }

    private class NoExitSecurityManager extends SecurityManager
    {
        public class ExitException extends SecurityException
        {
            private static final long serialVersionUID = 1L;

            public ExitException(int exitCode)
            {
                super("Exiting with status: " + exitCode);
            }
        }

        @Override
        public void checkPermission(Permission perm)
        {

        }

        @Override
        public void checkPermission(Permission perm, Object object)
        {

        }

        @Override
        public void checkExit(int status)
        {
            super.checkExit(status);
            throw new ExitException(status);
        }
    }
}
