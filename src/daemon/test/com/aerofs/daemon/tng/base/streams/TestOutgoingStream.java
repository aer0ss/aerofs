/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.streams;

import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.ImmediateInlineExecutor;
import com.aerofs.daemon.tng.SimpleSingleThreadedExecutor;
import com.aerofs.daemon.tng.base.OutgoingAeroFSPacket;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;
import com.aerofs.daemon.tng.ex.ExStreamInvalid;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.base.async.FutureUtil;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.lib.OutArg;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Transport;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.testlib.AbstractTest;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.aerofs.base.async.FutureUtil.addCallback;
import static com.aerofs.testlib.FutureAssert.assertThrows;
import static org.mockito.Mockito.*;
import static com.aerofs.lib.event.Prio.LO;
import static com.aerofs.base.id.UniqueID.ZERO;
import static com.aerofs.proto.Transport.PBStream.Type.BEGIN_STREAM;
import static com.aerofs.testlib.FutureAssert.getFutureThrowable;
import static org.junit.Assert.*;
import static org.mockito.ArgumentCaptor.forClass;

// XXX: Really, I can use this to test any stream implementation that conforms to the
// IOutgoingStream interface
public class TestOutgoingStream extends AbstractTest
{
    private final UncancellableFuture<Void> _connCloseFuture = UncancellableFuture.create();
    private final IConnection _conn = mock(IConnection.class);
    private final StreamID _id = new StreamID(0);
    private final DID _did = new DID(ZERO);
    private final SID _sid = new SID(ZERO);
    private final Prio _pri = LO;
    private final OutgoingStream _stream;

    private OutgoingStream createIOutgoingStream_(ISingleThreadedPrioritizedExecutor executor)
            throws ExStreamAlreadyExists
    {
        return OutgoingStream.getInstance_(executor, _conn, _id, _did, _sid, _pri);
    }

    public TestOutgoingStream()
            throws ExStreamAlreadyExists
    {
        when(_conn.getCloseFuture_()).thenReturn(_connCloseFuture);

        _stream = createIOutgoingStream_(new ImmediateInlineExecutor());
    }

    private void setupConnForControlMessage_()
    {
        when(_conn.send_(any(OutgoingAeroFSPacket.class), any(Prio.class))).thenReturn(
                UncancellableFuture.<Void>create());
    }

    @Test
    public void shouldSendBeginStreamPacketOverWireWhenBeginCalled()
    {
        setupConnForControlMessage_();

        _stream.begin_();

        ArgumentCaptor<OutgoingAeroFSPacket> pkt = forClass(OutgoingAeroFSPacket.class);
        verify(_conn).send_(pkt.capture(), eq(_pri));

        assertTrue(pkt.getValue().getHeader_().hasStream());
        assertEquals(BEGIN_STREAM, pkt.getValue().getHeader_().getStream().getType());
    }

    // FIXME: begin_ should only be called once

    @Test
    public void shouldOnlySendBeginStreamPacketOnceNoMatterHowManyTimesBeginIsCalled()
    {
        setupConnForControlMessage_();

        _stream.begin_();
        _stream.begin_();
        _stream.begin_();

        verify(_conn, times(1)).send_(any(OutgoingAeroFSPacket.class), any(Prio.class));
    }

    @Test
    public void shouldReturnTheSameFutureIfBeginCalledMultipleTimes()
    {
        setupConnForControlMessage_();

        ListenableFuture<?> future0 = _stream.begin_();
        ListenableFuture<?> future1 = _stream.begin_();
        ListenableFuture<?> future2 = _stream.begin_();

        assertEquals(future0, future1);
        assertEquals(future0, future2);
    }

    @Test
    public void shouldTriggerCloseFutureWithExceptionIfBeginStreamFails()
    {
        UncancellableFuture<Void> connSendFuture = UncancellableFuture.create();
        when(_conn.send_(any(OutgoingAeroFSPacket.class), any(Prio.class))).thenReturn(
                connSendFuture);

        final OutArg<Throwable> closeFutureThrowable = new OutArg<Throwable>(null);

        addCallback(_stream.begin_(), new FutureCallback<Void>()
        {
            @Override
            public void onSuccess(Void v)
            {
                fail("If the begin message cannot be sent the close future should fail with an error");
            }

            @Override
            public void onFailure(Throwable t)
            {
                closeFutureThrowable.set(t);
            }
        });

        final Exception sendException = new Exception("send failure from connection proxy");
        connSendFuture.setException(sendException);

        assertEquals(sendException, closeFutureThrowable.get());
    }

    @Test
    @Ignore("needs major changes in IOutgoingStream implementation to enforce this policy")
    public void shouldNotSendStreamChunksOverTheWireUntilBeginMessageSentOverWireSuccessfully()
    {
        // FIXME: this requires changes in stream, and I don't know what the right thing to do is

        // start the stream up

        // attempt to send multiple packets (you should get multiple futures back)

        // verify that there is only one call on connection proxy at this point
    }

    // FIXME: this might be too implementation specific
    @Test
    public void shouldSendFirstStreamChunkWithSeqnumSetToZero()
            throws IOException
    {
        when((_conn.send_(any(byte[][].class), any(Prio.class)))).thenReturn(
                UncancellableFuture.<Void>create());

        _stream.send_(new byte[]{0});

        ArgumentCaptor<OutgoingAeroFSPacket> payload = forClass(OutgoingAeroFSPacket.class);
        verify(_conn).send_(payload.capture(), eq(_pri));

        PBTPHeader hdr = payload.getValue().getHeader_();

        assertTrue(hdr.hasStream());
        assertTrue(hdr.getStream().hasSeqNum());
        assertEquals(0, hdr.getStream().getSeqNum());
    }

    // FIXME: this might be too implementation specific
    @Test
    public void shouldProperlySerializeStreamHeaderForSendingOverTheWire()
            throws IOException
    {
        when((_conn.send_(any(byte[][].class), any(Prio.class)))).thenReturn(
                UncancellableFuture.<Void>create());

        byte[] payloadBytes = new byte[]{0};
        _stream.send_(payloadBytes);

        ArgumentCaptor<OutgoingAeroFSPacket> payload = forClass(OutgoingAeroFSPacket.class);
        verify(_conn).send_(payload.capture(), eq(_pri));

        PBTPHeader hdr = payload.getValue().getHeader_();

        assertEquals(PBTPHeader.Type.STREAM, hdr.getType());

        assertTrue(hdr.hasSid());
        assertEquals(_sid, new SID(hdr.getSid()));

        assertTrue(hdr.hasStream());
        assertEquals(Transport.PBStream.Type.PAYLOAD, hdr.getStream().getType());
        assertEquals(_id, new StreamID(hdr.getStream().getStreamId()));

        assertEquals(payloadBytes, payload.getValue().getData_());
    }

    @Test
    public void shouldNotSetSendFutureUntilConnectionProxySetsItsOwnSendFuture()
    {
        UncancellableFuture<Void> connSendFuture = UncancellableFuture.create();
        when(_conn.send_(any(byte[][].class), any(Prio.class))).thenReturn(connSendFuture);

        ListenableFuture<Void> sendFuture = _stream.send_(new byte[]{0});
        assertFalse(sendFuture.isDone());

        connSendFuture.set(null);
        assertTrue(sendFuture.isDone());
    }

    @Test
    public void shouldSendAllStreamPacketsInOrderWithSequentiallyIncreasingSeqnumsWhenSendCalledMultipleTimesInARow()
            throws IOException
    {
        final int NUM_SEND_CALLS = 3;

        final List<OutgoingAeroFSPacket> argList = new ArrayList<OutgoingAeroFSPacket>(
                NUM_SEND_CALLS);

        when(_conn.send_(any(byte[][].class), any(Prio.class))).thenAnswer(new Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Object[] args = invocation.getArguments();
                argList.add((OutgoingAeroFSPacket) args[0]);
                return UncancellableFuture.<Void>create();
            }
        });

        for (int i = 0; i < NUM_SEND_CALLS; i++) {
            _stream.send_(new byte[]{0});
        }

        assertEquals(NUM_SEND_CALLS, argList.size());

        for (int sendCallSeqNum = 0; sendCallSeqNum < argList.size(); sendCallSeqNum++) {
            PBTPHeader hdr = argList.get(sendCallSeqNum).getHeader_();

            assertTrue(hdr.hasStream());
            assertTrue(hdr.getStream().hasSeqNum());
            assertEquals(sendCallSeqNum, hdr.getStream().getSeqNum());
        }
    }

    // FIXME: this may be testing implementation
    @Test
    public void shouldAlwaysSendStreamDataOverExecutorThreadEvenWhenCalledFromArbitraryThread()
            throws InterruptedException, ExStreamAlreadyExists
    {
        when(_conn.send_(any(OutgoingAeroFSPacket.class), any(Prio.class))).thenReturn(
                UncancellableFuture.<Void>createSucceeded(null));

        final Thread testRunnerThread = Thread.currentThread();

        final Object lockObject = new Object();
        final OutArg<Thread> sendExecutorThread = new OutArg<Thread>(null);

        when(_conn.send_(any(byte[][].class), any(Prio.class))).thenAnswer(new Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                synchronized (lockObject) {
                    sendExecutorThread.set(Thread.currentThread());
                    lockObject.notifyAll();
                }

                return UncancellableFuture.<Void>create();
            }
        });

        _stream.begin_();

        SimpleSingleThreadedExecutor simpleExecutor = new SimpleSingleThreadedExecutor();

        try {
            (new Thread(simpleExecutor)).start();

            final IOutgoingStream stream = createIOutgoingStream_(simpleExecutor);

            synchronized (lockObject) {
                stream.send_(new byte[]{0});
                lockObject.wait();
            }

            assertNotSame(testRunnerThread, sendExecutorThread.get());
        } finally {
            simpleExecutor.stop();
        }
    }

    @Test
    public void shouldTriggerCloseFutureWithExceptionIfSendFails()
    {
        UncancellableFuture<Void> connSendFuture = UncancellableFuture.create();
        when(_conn.send_(any(byte[][].class), any(Prio.class))).thenReturn(connSendFuture);

        ListenableFuture<Void> streamSendFuture = _stream.send_(new byte[]{0});
        assertFalse(streamSendFuture.isDone());

        Exception connSendException = new Exception("sending this packet failed");
        connSendFuture.setException(connSendException);

        assertTrue(streamSendFuture.isDone());

        Exception thrownException = null;
        try {
            streamSendFuture.get();
            fail("expecting a thrown exception");
        } catch (InterruptedException e) {
            fail("not expecting to be interrupted");
        } catch (ExecutionException e) {
            thrownException = (Exception) e.getCause();
        }

        assertEquals(connSendException, thrownException);
    }

    @Test
    @Ignore("in-flight packets cannot be cancelled")
    public void shouldAlsoTriggerAllOutstandingSendFuturesWithExceptionIfAbortIsCalledOnTheStreamWhileSendsAreOutstanding()
    {
        when(_conn.send_(any(byte[][].class), any(Prio.class))).thenReturn(
                UncancellableFuture.<Void>create());

        final int NUM_STREAM_SENDS = 3;
        List<ListenableFuture<Void>> sendFutures = new ArrayList<ListenableFuture<Void>>(
                NUM_STREAM_SENDS);

        for (int i = 0; i < NUM_STREAM_SENDS; i++) {
            ListenableFuture<Void> future = _stream.send_(new byte[]{0});
            assertFalse(future.isDone());
            sendFutures.add(future);
        }

        final InvalidationReason STREAM_ABORT_REASON = InvalidationReason.ENDED;
        ListenableFuture<Void> doneFuture = _stream.abort_(STREAM_ABORT_REASON);
        assertTrue(doneFuture.isDone());

        for (ListenableFuture<Void> sendFuture : sendFutures) {
            try {
                sendFuture.get();
                fail("expecting a thrown exception");
            } catch (InterruptedException e) {
                fail("not expecting to be interrupted");
            } catch (ExecutionException e) {
                ExStreamInvalid ex = (ExStreamInvalid) e.getCause();
                assertEquals(STREAM_ABORT_REASON, ex.getReason_());
            }
        }
    }

    @Test
    public void shouldTriggerCloseFutureListenerWithSuccessWhenEndCalled()
    {
        assertFalse(_stream.getCloseFuture_().isDone());

        _stream.end_();

        ExStreamInvalid exception = (ExStreamInvalid) getFutureThrowable(_stream.getCloseFuture_());
        assertEquals(InvalidationReason.ENDED, exception.getReason_());
    }

    @Test
    public void shouldTriggerCloseFutureListenerWithExceptionWhenAbortCalledByLocalPeer()
    {
        final OutArg<Throwable> closeFutureException = new OutArg<Throwable>(null);

        FutureUtil.addCallback(_stream.getCloseFuture_(), new FutureCallback<Void>()
        {
            @Override
            public void onSuccess(Void v)
            {
                fail("not expecting success callback");
            }

            @Override
            public void onFailure(Throwable t)
            {
                closeFutureException.set(t);
            }
        });

        assertNull(closeFutureException.get());

        final InvalidationReason STREAM_ABORT_REASON = InvalidationReason.INTERNAL_ERROR;

        _stream.abort_(STREAM_ABORT_REASON);

        assertNotNull(closeFutureException.get());
        ExStreamInvalid listenerException = (ExStreamInvalid) closeFutureException.get();
        assertEquals(STREAM_ABORT_REASON, listenerException.getReason_());
    }

    @Test
    public void shouldTriggerCloseFutureListenerWithExceptionWhenAbortCalledByRemotePeer()
    {
        final OutArg<Throwable> closeFutureException = new OutArg<Throwable>(null);

        FutureUtil.addCallback(_stream.getCloseFuture_(), new FutureCallback<Void>()
        {
            @Override
            public void onSuccess(Void v)
            {
                fail("not expecting success callback");
            }

            @Override
            public void onFailure(Throwable t)
            {
                closeFutureException.set(t);
            }
        });

        assertNull(closeFutureException.get());

        InvalidationReason STREAM_ABORT_REASON = InvalidationReason.OUT_OF_ORDER;

        _stream.abortByReceiver_(STREAM_ABORT_REASON);

        assertNotNull(closeFutureException.get());
        ExStreamInvalid listenerException = (ExStreamInvalid) closeFutureException.get();
        assertEquals(STREAM_ABORT_REASON, listenerException.getReason_());
    }

    @Test
    public void shouldSendAbortPacketWithCorrectParametersOverTheWireWhenAbortCalledByLocalPeer()
            throws IOException
    {
        setupConnForControlMessage_();

        final InvalidationReason STREAM_ABORT_REASON = InvalidationReason.INTERNAL_ERROR;

        _stream.abort_(STREAM_ABORT_REASON);

        ArgumentCaptor<OutgoingAeroFSPacket> sendCaptor = forClass(OutgoingAeroFSPacket.class);
        verify(_conn).send_(sendCaptor.capture(), eq(_pri));

        PBTPHeader hdr = sendCaptor.getValue().getHeader_();

        assertEquals(PBTPHeader.Type.STREAM, hdr.getType());
        assertTrue(hdr.hasSid());
        assertEquals(_sid, new SID(hdr.getSid()));

        assertTrue(hdr.hasStream());

        assertEquals(_id, new StreamID(hdr.getStream().getStreamId()));

        assertEquals(Transport.PBStream.Type.TX_ABORT_STREAM, hdr.getStream().getType());
        assertTrue(hdr.getStream().hasReason());
        assertEquals(STREAM_ABORT_REASON, hdr.getStream().getReason());
    }

    @Test
    public void shouldReturnFutureWithInvalidStreamExceptionImmediatelyWhenSendIsCalledAfterTheStreamWasEnded()
    {
        _stream.end_();

        ListenableFuture<Void> sendFuture = _stream.send_(new byte[]{0});
        assertTrue(sendFuture.isDone());

        Throwable t = getFutureThrowable(sendFuture);
        ExStreamInvalid ex = (ExStreamInvalid) t;
        assertEquals(_id, ex.getStreamId_());
    }

    @Test
    public void shouldReturnSetCloseFutureImmediatelyWhenEndIsCalledAfterTheStreamWasEnded()
            throws ExecutionException, InterruptedException
    {
        ListenableFuture<Void> closeFuture = _stream.getCloseFuture_();
        assertFalse(closeFuture.isDone());

        _stream.end_();

        ListenableFuture<Void> secondEndCallFuture = _stream.end_();
        assertTrue(secondEndCallFuture.isDone());

        assertEquals(closeFuture, secondEndCallFuture);

        assertThrows(secondEndCallFuture, ExStreamInvalid.class);
    }

    @Test
    public void shouldReturnSetCloseFutureImmediatelyWhenEndIsCalledAfterTheStreamWasAborted()
    {
        ListenableFuture<Void> closeFuture = _stream.getCloseFuture_();
        assertFalse(closeFuture.isDone());

        final InvalidationReason ABORT_REASON = InvalidationReason.ENDED;
        _stream.abort_(ABORT_REASON);

        ListenableFuture<Void> endFuture = _stream.end_();
        assertTrue(endFuture.isDone());

        assertEquals(closeFuture, endFuture);

        Throwable t = getFutureThrowable(endFuture);
        ExStreamInvalid ex = (ExStreamInvalid) t;
        assertEquals(ABORT_REASON, ex.getReason_());
    }

    @Test
    public void shouldReturnSetCloseFutureImmediatelyWhenAbortIsCalledAfterTheStreamWasEnded()
            throws ExecutionException, InterruptedException
    {
        ListenableFuture<Void> closeFuture = _stream.getCloseFuture_();
        assertFalse(closeFuture.isDone());

        _stream.end_();

        ListenableFuture<Void> abortFuture = _stream.abort_(InvalidationReason.ENDED);
        assertTrue(abortFuture.isDone());

        assertEquals(closeFuture, abortFuture);

        assertThrows(abortFuture, ExStreamInvalid.class);
    }

    @Test
    public void shouldReturnSetCloseFutureImmediatelyWhenAbortIsCalledAfterTheStreamWasAborted()
    {
        ListenableFuture<Void> closeFuture = _stream.getCloseFuture_();
        assertFalse(closeFuture.isDone());

        final InvalidationReason FIRST_ABORT_REASON = InvalidationReason.ENDED;
        final InvalidationReason SECOND_ABORT_REASON = InvalidationReason.INTERNAL_ERROR;

        _stream.abort_(FIRST_ABORT_REASON);

        ListenableFuture<Void> secondAbortCallFuture = _stream.abort_(SECOND_ABORT_REASON);
        assertTrue(secondAbortCallFuture.isDone());

        assertEquals(closeFuture, secondAbortCallFuture);

        Throwable t = getFutureThrowable(secondAbortCallFuture);
        ExStreamInvalid ex = (ExStreamInvalid) t;
        assertEquals(FIRST_ABORT_REASON, ex.getReason_());
    }

    @Test
    public void shouldAutomaticallyAbortTheStreamWhenConnectionCloseFutureIsTriggered()
    {
        ListenableFuture<Void> closeFuture = _stream.getCloseFuture_();
        assertFalse(closeFuture.isDone());

        final ExTransport CONN_CLOSE_EXCEPTION = new ExTransport("explicit stream disconnection");
        _connCloseFuture.setException(CONN_CLOSE_EXCEPTION);
        assertTrue(closeFuture.isDone());

        ExTransport ex = (ExTransport) getFutureThrowable(closeFuture);
        assertEquals(CONN_CLOSE_EXCEPTION, ex);
    }
}
