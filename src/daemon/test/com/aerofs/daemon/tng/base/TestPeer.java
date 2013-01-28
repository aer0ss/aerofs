/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.base.id.DID;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.DropDelayedInlineExecutor;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.base.streams.NewOutgoingStream;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.lib.OutArg;
import com.aerofs.base.id.SID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

import static com.aerofs.lib.event.Prio.HI;
import static com.aerofs.lib.event.Prio.LO;
import static com.aerofs.testlib.FutureAssert.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

// FIXME: If I use send_ internally, I _MUST_ mock PeerConnection.send_ to return a valid future!

public class TestPeer extends AbstractTest
{
    // parameters for all messages

    private final DID _did = new DID(DID.ZERO);
    private final SID _sid = new SID(SID.ZERO);
    private final StreamID _streamId = new StreamID(0);

    // 1st connection

    private final PeerConnection _connection0 = mock(PeerConnection.class);
    private final UncancellableFuture<Void> _connection0ConnectFuture = UncancellableFuture.create();
    private final UncancellableFuture<Void> _connection0CloseFuture = UncancellableFuture.create();

    // 2nd connection

    private final PeerConnection _connection1 = mock(PeerConnection.class);
    private final UncancellableFuture<Void> _connection1ConnectFuture = UncancellableFuture.create();
    private final UncancellableFuture<Void> _connection1CloseFuture = UncancellableFuture.create();

    // connection factory and executor used by the peer

    private final PeerConnectionFactory _connectionFactory = mock(PeerConnectionFactory.class);
    private final ISingleThreadedPrioritizedExecutor _executor = new DropDelayedInlineExecutor();

    // peer itself

    private final Peer _peer = Peer.getInstance_(_did, _executor, _connectionFactory);

    public TestPeer()
    {
        when(_connection0.getCloseFuture_()).thenReturn(_connection0CloseFuture);
        when(_connection0.connect_()).thenReturn(_connection0ConnectFuture);

        when(_connection1.getCloseFuture_()).thenReturn(_connection1CloseFuture);
        when(_connection1.connect_()).thenReturn(_connection1ConnectFuture);

        when(_connectionFactory.createConnection_(any(IPeer.class))).thenReturn(_connection0)
                        .thenReturn(_connection1);
    }

    @Test
    public void shouldCreateAPeerConnectionAndCallConnectOnItIfANewUnicastPacketNeedsToBeSentAndThereIsNoExistingPeerConnection()
    {
        when(_connection0.connect_()).thenReturn(UncancellableFuture.<Void>create());

        _peer.sendDatagram_(_sid, new byte[]{0}, LO);

        verify(_connection0).connect_();
    }

    @Test
    public void shouldCreateAPeerConnectionAndCallConnectOnItIfANewStreamNeedsToBeStartedAndThereIsNoExistingPeerConnection()
    {
        when(_connection0.connect_()).thenReturn(UncancellableFuture.<Void>create());

        _peer.beginStream_(_streamId, _sid, LO);

        verify(_connection0).connect_();
    }

    @Test
    public void shouldCallSendOnPeerConnectionForAllQueuedBeginStreamsCallsInPriorityOrderWhenTheConnectCallForThatPeerConnectionSucceeds()
    {
        when(_connection0.send_(any(Object.class), any(Prio.class))).thenReturn(
                UncancellableFuture.<Void>create());

        // queue begin stream calls

        _peer.beginStream_(new StreamID(0), _sid, LO);
        _peer.beginStream_(new StreamID(1), _sid, LO);
        _peer.beginStream_(new StreamID(2), _sid, HI);

        // trigger a successful connect attempt

        _connection0ConnectFuture.set(null);

        // expect the receive loop to be started

        verify(_connection0).startReceiveLoop_();

        // expect all these pending streams to be sent in the following order: 2, 0, 1

        InOrder streamCalls = inOrder(_connection0);

        streamCalls.verify(_connection0).send_(isNewOutgoingStream_(2, _sid), eq(HI));

        streamCalls.verify(_connection0).send_(isNewOutgoingStream_(0, _sid), eq(LO));

        streamCalls.verify(_connection0).send_(isNewOutgoingStream_(1, _sid), eq(LO));
    }

    @Test
    public void shouldCallSendOnPeerConnectionForAllQueuedUnicastPacketsInPriorityOrderWhenTheConnectCallForThatPeerConnectionSucceeds()
    {
        when(_connection0.send_(any(Object.class), any(Prio.class))).thenReturn(
                UncancellableFuture.<Void>create());

        // queue unicast packet calls

        final byte[] DATA_0 = new byte[]{0};
        final byte[] DATA_1 = new byte[]{0};
        final byte[] DATA_2 = new byte[]{0};

        _peer.sendDatagram_(_sid, DATA_0, LO);
        _peer.sendDatagram_(_sid, DATA_1, LO);
        _peer.sendDatagram_(_sid, DATA_2, HI);

        // trigger a successful connect attempt

        _connection0ConnectFuture.set(null);

        // expect the receive loop to be started

        verify(_connection0).startReceiveLoop_();

        // expect all these pending packets to be sent in the following order: 2, 0, 1

        InOrder sendPacketCalls = inOrder(_connection0);

        sendPacketCalls.verify(_connection0)
                       .send_(isOutgoingUnicastPacket_(_sid, DATA_2), eq(HI));

        sendPacketCalls.verify(_connection0)
                       .send_(isOutgoingUnicastPacket_(_sid, DATA_0), eq(LO));

        sendPacketCalls.verify(_connection0)
                       .send_(isOutgoingUnicastPacket_(_sid, DATA_1), eq(LO));
    }

    @Test
    public void shouldNotifySenderOfErrorForAllQueuedBeginStreamCallsWhenTheConnectCallForAPeerConnectionFails()
    {
        //  queue begin stream calls

        final int NUM_STREAM_CALLS = 3;
        ImmutableList.Builder<ListenableFuture<IOutgoingStream>> streamFuturesBuilder = ImmutableList
                .builder();

        for (int i = 0; i < NUM_STREAM_CALLS; i++) {
            streamFuturesBuilder.add(_peer.beginStream_(new StreamID(i), _sid, LO));
        }

        final ImmutableList<ListenableFuture<IOutgoingStream>> streamFutures = streamFuturesBuilder.build();

        for (ListenableFuture<IOutgoingStream> streamFuture : streamFutures) {
            assertFalse(streamFuture.isDone());
        }

        // trigger a connect exception

        ExTransport CONNECT_FAIL_EXCEPTION = new ExTransport("connect failed");
        _connection0ConnectFuture.setException(CONNECT_FAIL_EXCEPTION);

        for (ListenableFuture<IOutgoingStream> streamFuture : streamFutures) {
            assertTrue(streamFuture.isDone());
        }

        ExTransport beginFutureEx;
        for (ListenableFuture<IOutgoingStream> streamFuture : streamFutures) {
            beginFutureEx = (ExTransport) getFutureThrowable(streamFuture);
            assertNotNull(beginFutureEx); // I don't expose the underlying error to the caller
        }
    }

    @Test
    public void shouldNotifySenderOfErrorForAllPendingUnicastPacketsWhenTheConnectCallForAPeerConnectionFails()
    {
        //  queue unicast packet calls

        final int NUM_SEND_PACKET_CALLS = 3;
        ImmutableList.Builder<ListenableFuture<Void>> packetFuturesBuilder = ImmutableList.builder();

        for (int i = 0; i < NUM_SEND_PACKET_CALLS; i++) {
            packetFuturesBuilder.add(_peer.sendDatagram_(_sid, new byte[]{0},
                LO));
        }

        final ImmutableList<ListenableFuture<Void>> packetFutures = packetFuturesBuilder.build();

        for (ListenableFuture<Void> packetFuture : packetFutures) {
            assertFalse(packetFuture.isDone());
        }

        // trigger a connect exception

        ExTransport CONNECT_FAIL_EXCEPTION = new ExTransport("connect failed");
        _connection0ConnectFuture.setException(CONNECT_FAIL_EXCEPTION);

        for (ListenableFuture<Void> packetFuture : packetFutures) {
            assertTrue(packetFuture.isDone());
        }

        ExTransport beginFutureEx;
        for (ListenableFuture<Void> packetFuture : packetFutures) {
            beginFutureEx = (ExTransport) getFutureThrowable(packetFuture);
            assertNotNull(beginFutureEx); // again, I don't expose underlying error to the caller
        }
    }

    @Test
    public void shouldCreateAPeerConnectionAndCallConnectOnItIfThePreviousPeerConnectionHasClosedAndANewBeginStreamCallIsMade()
    {
        // make a send packet call

        ListenableFuture<Void> packetFuture = _peer.sendDatagram_(_sid, new byte[]{0}, LO);

        assertFalse(packetFuture.isDone());

        // now trigger a connection failure

        _connection0ConnectFuture.setException(new ExTransport("fail"));

        assertTrue(packetFuture.isDone());

        // make a begin stream call now

        ListenableFuture<IOutgoingStream> streamFuture = _peer.beginStream_(new StreamID(0), _sid,
            LO);

        verify(_connectionFactory, times(2)).createConnection_(_peer);

        assertFalse(streamFuture.isDone());
    }

    @Test
    public void shouldCreateAPeerConnectionAndCallConnectOnItIfThePreviousPeerConnectionHasClosedAndANewUnicastPacketSendCallIsMade()
    {
        // let's make a begin stream call

        ListenableFuture<IOutgoingStream> streamFuture = _peer.beginStream_(new StreamID(0), _sid,
                LO);

        assertFalse(streamFuture.isDone());

        // now trigger a connection failure

        _connection0ConnectFuture.setException(new ExTransport("fail"));

        assertTrue(streamFuture.isDone());

        // let's make a send packet call now

        ListenableFuture<Void> packetFuture = _peer.sendDatagram_(_sid,
            new byte[]{0}, LO);

        verify(_connectionFactory, times(2)).createConnection_(_peer);

        assertFalse(packetFuture.isDone());
    }

    @Test
    public void shouldNotMakeTheSameBeginStreamCallAgainOnceTheSenderHasBeenNotifiedOfItsFailure()
    {
        when(_connection1.send_(anyObject(), any(Prio.class))).thenReturn(
                UncancellableFuture.<Void>create());

        _peer.beginStream_(new StreamID(0), _sid, LO);

        _connection0ConnectFuture.setException(new ExTransport("fail"));

        _peer.beginStream_(new StreamID(1), _sid, LO);

        _connection1ConnectFuture.set(null);

        verify(_connection1, never()).send_(isNewOutgoingStream_(0, _sid), eq(LO));
        verify(_connection1).send_(isNewOutgoingStream_(1, _sid), eq(LO));
    }

    @Test
    public void shouldNotMakeTheSameSendUnicastPacketCallAgainOnceTheSenderHasBeenNotifiedOfItsFailure()
    {
        when(_connection1.send_(anyObject(), any(Prio.class))).thenReturn(
                UncancellableFuture.<Void>create());

        byte[] DATA_0 = new byte[]{0};
        _peer.sendDatagram_(_sid, DATA_0, LO);

        _connection0ConnectFuture.setException(new ExTransport("fail"));

        byte[] DATA_1 = new byte[]{1};
        _peer.sendDatagram_(_sid, DATA_1, LO);

        _connection1ConnectFuture.set(null);

        verify(_connection1, never()).send_(isOutgoingUnicastPacket_(_sid, DATA_0),
            eq(LO));
        verify(_connection1).send_(isOutgoingUnicastPacket_(_sid, DATA_1), eq(LO));
    }

    @Test
    public void shouldSendABeginStreamOnThePeerConnectionIfAValidPeerConnectionExistsAndABeginStreamCallIsMade()
    {
        UncancellableFuture<Void> sendFuture0 = UncancellableFuture.create();
        UncancellableFuture<Void> sendFuture1 = UncancellableFuture.create();

        when(_connection0.send_(anyObject(), any(Prio.class))).thenReturn(sendFuture0)
                .thenReturn(sendFuture1);

        // send a packet to start the connection process

        final byte[] DATA = new byte[]{0};
        ListenableFuture<Void> packetFuture = _peer.sendDatagram_(_sid, DATA,
            LO);

        // connect succeeds

        _connection0ConnectFuture.set(null);

        // we now have an active connection

        // now send a begin stream

        _peer.beginStream_(new StreamID(0), _sid, LO);

        InOrder sends = inOrder(_connection0);
        sends.verify(_connection0).send_(isOutgoingUnicastPacket_(_sid, DATA), eq(LO));
        sends.verify(_connection0).send_(isNewOutgoingStream_(0, _sid), eq(LO));

        assertCompletionFutureChainedProperly(sendFuture0, packetFuture);
        // can't verify stream future because its value is actually set by the stream handler
    }

    @Test
    public void shouldSendAUnicastPacketOnThePeerConnectionIfAValidPeerConnectionExistsAndASendUnicastPacketCallIsMade()
    {
        UncancellableFuture<Void> sendFuture0 = UncancellableFuture.create();
        UncancellableFuture<Void> sendFuture1 = UncancellableFuture.create();

        when(_connection0.send_(anyObject(), any(Prio.class))).thenReturn(sendFuture0)
                .thenReturn(sendFuture1);

        // send a begin stream to start the connection process

        _peer.beginStream_(new StreamID(0), _sid, LO);

        // connect succeeds

        _connection0ConnectFuture.set(null);

        // now send a packet

        final byte[] DATA = new byte[]{0};
        ListenableFuture<Void> packetFuture = _peer.sendDatagram_(_sid, DATA,
            LO);

        InOrder sends = inOrder(_connection0);
        sends.verify(_connection0).send_(isNewOutgoingStream_(0, _sid), eq(LO));
        sends.verify(_connection0).send_(isOutgoingUnicastPacket_(_sid, DATA), eq(LO));

        // can't verify stream future because its value is actually set by the stream handler
        assertCompletionFutureChainedProperly(sendFuture1, packetFuture);
    }

    private Peer setupPeerAndMockExecutorToGrabDelayedRunnable_(final OutArg<Runnable> delayedRunnableRef)
    {
        DropDelayedInlineExecutor executor = spy(new DropDelayedInlineExecutor());
        Peer peer = Peer.getInstance_(_did, executor, _connectionFactory);

        // setup the executor so that we can grab the connect-timeout task

        doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Runnable delayed = (Runnable) invocation.getArguments()[0];
                delayedRunnableRef.set(delayed);
                return null;
            }
        }).when(executor).executeAfterDelay(any(Runnable.class), anyLong());

        return peer;
    }

    @Test
    public void shouldDropAllPendingBeginStreamAndSendUnicastPacketCallsWhenAConnectTimeoutOccursButRequeueAnyPendingPulseCalls()
    {
        final OutArg<Runnable> delayedRunnableRef = new OutArg<Runnable>(null);
        Peer peer = setupPeerAndMockExecutorToGrabDelayedRunnable_(delayedRunnableRef);

        // queue up a couple of calls

        final StreamID streamId = new StreamID(0);
        ListenableFuture<IOutgoingStream> streamFuture = peer.beginStream_(streamId, _sid, LO);

        final byte[] DATA = new byte[]{0};
        ListenableFuture<Void> packetFuture = peer.sendDatagram_(_sid, DATA, LO);

        ListenableFuture<Void> pulseFuture = peer.pulse_(Prio.LO);

        assertFalse(streamFuture.isDone());
        assertFalse(packetFuture.isDone());
        assertFalse(pulseFuture.isDone());

        // simulate a timeout

        delayedRunnableRef.get().run();

        // check that our futures have been tripped

        assertTrue(streamFuture.isDone());
        assertTrue(packetFuture.isDone());
        assertFalse(pulseFuture.isDone());

        assertThrows(streamFuture, ExTransport.class);
        assertThrows(packetFuture, ExTransport.class);
    }

    @Test
    public void shouldNotInvalidateValidConnectionWhenTimeoutOccursAfterConnectAttemptSucceeds()
    {
        when(_connection0.send_(anyObject(), any(Prio.class))).thenReturn(
                UncancellableFuture.<Void>create());

        final OutArg<Runnable> delayedRunnableRef = new OutArg<Runnable>(null);
        Peer peer = setupPeerAndMockExecutorToGrabDelayedRunnable_(delayedRunnableRef);

        // queue up a call

        final StreamID streamId = new StreamID(0);
        ListenableFuture<IOutgoingStream> streamFuture = peer.beginStream_(streamId, _sid, LO);

        // simulate the connection future as having succeeded

        _connection0ConnectFuture.set(null);

        // now simulate the connect timeout

        delayedRunnableRef.get().run();

        // now send another packet - this should go through since the connection hasn't been
        // invalidated

        final byte[] DATA = new byte[]{0};
        ListenableFuture<Void> packetFuture = peer.sendDatagram_(_sid, DATA,
            LO);

        assertFalse(streamFuture.isDone());
        assertFalse(packetFuture.isDone());

        // check that our futures are still not tripped

        assertFalse(streamFuture.isDone());
        assertFalse(packetFuture.isDone());

        // and that both the packet before and after the timeout were sent out

        verify(_connection0).send_(isNewOutgoingStream_(0, _sid), eq(LO));
        verify(_connection0).send_(isOutgoingUnicastPacket_(_sid, DATA), eq(LO));
    }

    @Test
    public void shouldNotInvalidateANewOngoingConnectAttemptIfAnOldConnectAttemptTimesOut()
    {
        DropDelayedInlineExecutor executor = spy(new DropDelayedInlineExecutor());

        final List<OutArg<Runnable>> delayedRunnableRefs = new ArrayList<OutArg<Runnable>>(2);

        doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Runnable runnable = (Runnable) invocation.getArguments()[0];
                delayedRunnableRefs.add(new OutArg<Runnable>(runnable));
                return null;
            }
        }).when(executor).executeAfterDelay(any(Runnable.class), anyLong());

        Peer peer = Peer.getInstance_(_did, executor, _connectionFactory);

        // send a packet to initiate the connection process

        ListenableFuture<Void> sendFuture0 = peer.sendDatagram_(_sid,
            new byte[]{0}, LO);

        // signal connection process failure

        _connection0ConnectFuture.setException(new ExTransport("connect failed"));

        assertTrue(sendFuture0.isDone());

        // send a second packet to start the connection process again

        ListenableFuture<Void> sendFuture1 = peer.sendDatagram_(_sid,
            new byte[]{0}, LO);

        // now run the first timeout runnable

        assertEquals(2, delayedRunnableRefs.size()); // two outstanding connect timeouts

        OutArg<Runnable> firstConnectTimeoutRunnable = delayedRunnableRefs.remove(0);
        firstConnectTimeoutRunnable.get().run();

        assertFalse(sendFuture1.isDone()); // shouldn't drop the second send call
    }

    private void activateConnection0_()
    {
        when(_connection0.send_(anyObject(), any(Prio.class))).thenReturn(
            UncancellableFuture.<Void>create());

        // send a packet to start the connection process

        _peer.sendDatagram_(_sid, new byte[]{0}, LO);

        // connect succeeds

        _connection0ConnectFuture.set(null);

        // we now have an active connection
    }

    @Test
    public void shouldDisconnectActivePeerConnectionWhenDestroyIsCalled()
    {
        activateConnection0_();

        ExTransport DESTROY_EXCEPTION = new ExTransport("explicit destroy");
        _peer.destroy_(DESTROY_EXCEPTION);

        verify(_connection0).disconnect_(DESTROY_EXCEPTION);

        // FIXME: below is heavily testing implementation

        try {
            _peer.sendDatagram_(_sid, new byte[]{0}, LO);
            fail("peer should be invalidated");
        } catch (AssertionError e) {
            // pass
        }
    }

    @Test
    public void shouldDisconnectOngoingConnectionAttemptWhenDestroyIsCalledAndConnectAndSendSuccessfullyForTheNextConnection()
    {
        when(_connection0.send_(anyObject(), any(Prio.class))).thenReturn(
            UncancellableFuture.<Void>create());

        // send a packet to start the connection process
        _peer.sendDatagram_(_sid, new byte[]{0}, LO);

        // We shouldn't have finished connecting. This is a mock object, but just in case,
        // make sure it's not done
        assertFalse(_connection0ConnectFuture.isDone());

        Exception exception = new Exception("Test destroy");
        _peer.destroy_(exception);

        verify(_connection0).disconnect_(exception);
    }

    private static IUnicastConnection makeIUnicastConnection()
    {
        IUnicastConnection unicast = mock(IUnicastConnection.class);
        when(unicast.getCloseFuture_()).thenReturn(UncancellableFuture.<Void>create());

        return unicast;
    }

    @Test
    public void shouldStartReceiveLoopOnNewIncomingConnectionAndAllowItToBeUsedForSubsequentSendCalls()
    {
        IUnicastConnection unicast = makeIUnicastConnection();

        when(_connectionFactory.createConnection_(_peer, unicast))
                        .thenReturn(_connection0);

        _peer.onIncomingConnection_(unicast);

        // attempt to send a packet

        final byte[] DATA = new byte[]{0};
        _peer.sendDatagram_(_sid, DATA, LO);

        verify(_connection0).startReceiveLoop_();
        verify(_connection0).send_(isOutgoingUnicastPacket_(_sid, DATA), eq(LO));
    }

    @Test
    // FIXME: this is testing implementation - what happens if we ignore the second attempt?
    public void shouldAssertIfOwnerAttemptsToCallSetConnectionASecondTimeAfterTheFirstCallInstallsAValidPeerConnection()
    {
        IUnicastConnection unicast = makeIUnicastConnection();

        when(_connectionFactory.createConnection_(_peer, unicast))
            .thenReturn(_connection0);

        _peer.onIncomingConnection_(unicast);

        try {
            _peer.onIncomingConnection_(unicast);
            fail("should assert if someone tries to set connection a second time");
        } catch (AssertionError e) {
            // pass
        }
    }

    //
    // helper types
    //

    private static class IsNewOutgoinStreamObject extends ArgumentMatcher<Object>
    {
        private final StreamID _streamId;
        private final SID _sid;

        private IsNewOutgoinStreamObject(StreamID streamId, SID sid)
        {
            this._sid = sid;
            this._streamId = streamId;
        }

        @Override
        public boolean matches(Object arg)
        {
            if (arg instanceof NewOutgoingStream) {
                NewOutgoingStream streamArguments = (NewOutgoingStream) arg;
                return streamArguments.getId_().equals(_streamId) &&
                       streamArguments.getSid_().equals(_sid);
            }

            return false;
        }
    }

    private static Object isNewOutgoingStream_(int streamId, SID sid)
    {
        return argThat(new IsNewOutgoinStreamObject(new StreamID(streamId), sid));
    }

    private static class IsOugoingUnicastPacketObject extends ArgumentMatcher<Object>
    {
        private final SID _sid;
        private final byte[] _data;

        private IsOugoingUnicastPacketObject(SID sid, byte[] data)
        {
            this._data = data;
            this._sid = sid;
        }

        @Override
        public boolean matches(Object arg)
        {
            if (arg instanceof OutgoingUnicastPacket) {
                OutgoingUnicastPacket unicastArguments = (OutgoingUnicastPacket) arg;
                return unicastArguments.getSid_().equals(_sid) &&
                       unicastArguments.getPayload_().equals(_data);
            }

            return false;
        }
    }

    private static Object isOutgoingUnicastPacket_(SID sid, byte[] data)
    {
        return argThat(new IsOugoingUnicastPacketObject(sid, data));
    }
}