/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.streams;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.ImmediateInlineExecutor;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;
import com.aerofs.daemon.tng.ex.ExStreamInvalid;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.aerofs.testlib.FutureAssert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.aerofs.testlib.FutureAssert.getFutureThrowable;
import static org.junit.Assert.*;

public class TestIncomingStream extends AbstractTest
{
    private final UncancellableFuture<Void> _connCloseFuture = UncancellableFuture.create();
    private final IConnection _conn = mock(IConnection.class);
    private final StreamID _id = new StreamID(0);
    private final SID _sid = new SID(SID.ZERO);
    private final DID _did = new DID(DID.ZERO);
    private final Prio _pri = Prio.LO;
    private final IncomingStream _stream;

    private IncomingStream createIIncomingStream_(ISingleThreadedPrioritizedExecutor executor)
            throws ExStreamAlreadyExists
    {
        return IncomingStream.getInstance_(executor, _conn, _id, _did, _sid, _pri);
    }

    public TestIncomingStream()
            throws ExStreamAlreadyExists
    {
        when(_conn.getCloseFuture_()).thenReturn(_connCloseFuture);

        _stream = createIIncomingStream_(new ImmediateInlineExecutor());
    }

    @Test
    public void shouldAllowCallerToRecvMultipleStreamChunksInASingleRecvCallWhenTheyCallRecvOnlyAfterMultipleStreamChunksAreRecvdOnWire()
            throws ExecutionException, InterruptedException
    {
        final int NUM_CHUNKS_RECVD_ON_WIRE = 3;
        final int CHUNK_WIRELEN = 1;
        final byte[] CHUNK_CONTENT = new byte[]{0};

        for (int i = 0; i < NUM_CHUNKS_RECVD_ON_WIRE; i++) {
            _stream.onBytesReceived_(i, new ByteArrayInputStream(CHUNK_CONTENT), CHUNK_WIRELEN);
        }

        ListenableFuture<ImmutableList<Chunk>> recvFuture = _stream.receive_();
        assertTrue(recvFuture.isDone());

        ImmutableList<Chunk> chunks = recvFuture.get();
        assertEquals(NUM_CHUNKS_RECVD_ON_WIRE, chunks.size());
    }

    @Test
    public void shouldAllowCallerToRecvAStreamChunkEvenWhenTheyCallRecvBeforeStreamChunkActuallyRecvdOnWire()
            throws ExecutionException, InterruptedException
    {
        ListenableFuture<ImmutableList<Chunk>> recvFuture = _stream.receive_();
        assertFalse(recvFuture.isDone());

        _stream.onBytesReceived_(0, new ByteArrayInputStream(new byte[]{0}), 1);

        assertTrue(recvFuture.isDone());

        ImmutableList<Chunk> chunks = recvFuture.get();
        assertNotNull(chunks);
    }

    @Test
    // FIXME: merge with 1st test
    public void shouldDeliverMultipleStreamChunksToCallerInWireOrderWhenRecvCalled()
            throws ExecutionException, InterruptedException
    {
        final int NUM_CHUNKS_RECVD_ON_WIRE = 3;
        final int CHUNK_WIRELEN = 1;
        final byte[] CHUNK_CONTENT = new byte[]{0};

        for (int i = 0; i < NUM_CHUNKS_RECVD_ON_WIRE; i++) {
            _stream.onBytesReceived_(i, new ByteArrayInputStream(CHUNK_CONTENT), CHUNK_WIRELEN);
        }

        ListenableFuture<ImmutableList<Chunk>> recvFuture = _stream.receive_();
        assertTrue(recvFuture.isDone());

        ImmutableList<Chunk> chunks = recvFuture.get();
        assertEquals(NUM_CHUNKS_RECVD_ON_WIRE, chunks.size());

        int i = 0;
        for (Chunk c : chunks) {
            assertEquals(i, c.getSeqnum_());
            assertEquals(CHUNK_WIRELEN, c.getWirelen_());
            i++;
        }
    }

    @Test
    @Ignore("unimplemented")
    // FIXME: important - this multi-threaded test does not substitute for a code review!
    public void shouldGetAllStreamChunksInWireOrderEvenIfOneThreadIsCallingRecvAtTheSameTimeTheNetworkThreadRecvsAStreamChunkOverTheWire()
    {

    }

    @Test
    public void shouldOnlyReturnOneRecvFutureEvenIfCallerMakesMultipleRecvCallsBeforeAStreamChunkIsRecvdOverTheWire()
    {
        ListenableFuture<ImmutableList<Chunk>> recvFuture0 = _stream.receive_();
        ListenableFuture<ImmutableList<Chunk>> recvFuture1 = _stream.receive_();
        ListenableFuture<ImmutableList<Chunk>> recvFuture2 = _stream.receive_();

        assertEquals(recvFuture0, recvFuture1);
        assertEquals(recvFuture1, recvFuture2);
    }

    @Test
    // XXX: should I simulate multiple outstanding recv futures? (really I only return one)
    public void shouldTriggerOutstandingRecvFuturesWithExceptionIfStreamIsAborted()
    {
        ListenableFuture<ImmutableList<Chunk>> recvFuture = _stream.receive_();

        assertFalse(recvFuture.isDone());

        final InvalidationReason ABORT_REASON = InvalidationReason.OUT_OF_ORDER;
        _stream.abort_(ABORT_REASON);

        assertTrue(recvFuture.isDone());

        Throwable t = getFutureThrowable(recvFuture);
        ExStreamInvalid ex = (ExStreamInvalid) t;
        assertEquals(ABORT_REASON, ex.getReason_());
    }

    @Test
    public void shouldTriggerOutstandingRecvFuturesWithExStreamInvalidExceptionIfLocalPeerEndsTheStream()
    {
        ListenableFuture<ImmutableList<Chunk>> recvFuture = _stream.receive_();

        assertFalse(recvFuture.isDone());

        _stream.end_();

        assertTrue(recvFuture.isDone());

        ExStreamInvalid exception = (ExStreamInvalid) getFutureThrowable(recvFuture);
        assertEquals(InvalidationReason.ENDED, exception.getReason_());
    }

    @Test
    public void shouldTriggerCloseFutureWithExceptionIfStreamIsAborted()
    {
        ListenableFuture<Void> closeFuture = _stream.getCloseFuture_();
        assertFalse(closeFuture.isDone());

        final InvalidationReason ABORT_REASON = InvalidationReason.OUT_OF_ORDER;
        _stream.abort_(ABORT_REASON);

        assertTrue(closeFuture.isDone());

        Throwable t = getFutureThrowable(closeFuture);
        ExStreamInvalid ex = (ExStreamInvalid) t;
        assertEquals(ABORT_REASON, ex.getReason_());
    }

    @Test
    public void shouldTriggerCloseFutureWithExStreamInvalidExceptionIfLocalPeerEndsTheStream()
    {
        ListenableFuture<Void> closeFuture = _stream.getCloseFuture_();
        assertFalse(closeFuture.isDone());

        _stream.end_();

        assertTrue(closeFuture.isDone());

        ExStreamInvalid ex = (ExStreamInvalid) getFutureThrowable(closeFuture);
        assertEquals(InvalidationReason.ENDED, ex.getReason_());
    }

    @Test
    public void shouldReturnImmediatelyTriggeredFutureWithExceptionIfRecvIsCalledAfterTheStreamWasEnded()
    {
        _stream.end_();

        ListenableFuture<ImmutableList<Chunk>> recvFuture = _stream.receive_();

        assertTrue(recvFuture.isDone());

        Throwable t = getFutureThrowable(recvFuture);
        ExStreamInvalid ex = (ExStreamInvalid) t;
        assertEquals(_id, ex.getStreamId_());
    }

    @Test
    public void shouldReturnImmediatelyTriggeredFutureWithAbortCauseIfRecvIsCalledAfterTheStreamWasAborted()
    {
        final InvalidationReason ABORT_REASON = InvalidationReason.ENDED;
        _stream.abort_(ABORT_REASON);

        ListenableFuture<ImmutableList<Chunk>> recvFuture = _stream.receive_();

        assertTrue(recvFuture.isDone());

        Throwable t = getFutureThrowable(recvFuture);
        ExStreamInvalid ex = (ExStreamInvalid) t;
        assertEquals(ABORT_REASON, ex.getReason_());
    }

    private static List<ListenableFuture<Void>> makeFutureList(int size)
    {
        return new ArrayList<ListenableFuture<Void>>(size);
    }

    @Test
    public void shouldReturnImmediatelyTriggeredFutureWithFirstAbortCauseIfAbortIsCalledMultipleTimes()
    {
        final ImmutableList<InvalidationReason> ABORT_REASONS = ImmutableList.of(InvalidationReason.ENDED,
                InvalidationReason.INTERNAL_ERROR, InvalidationReason.STREAM_NOT_FOUND);

        final InvalidationReason VALID_ABORT_REASON = ABORT_REASONS.get(0);

        final List<ListenableFuture<Void>> abortFutures = makeFutureList(ABORT_REASONS.size());
        for (InvalidationReason reason : ABORT_REASONS) {
            abortFutures.add(_stream.abort_(reason));
        }

        ExStreamInvalid ex;
        for (ListenableFuture<Void> abortFuture : abortFutures) {
            ex = (ExStreamInvalid) getFutureThrowable(abortFuture);
            assertEquals(VALID_ABORT_REASON, ex.getReason_());

        }
    }

    @Test
    public void shouldReturnImmediatelyTriggeredFutureWithExStreamInvalidExceptionIfEndIsCalledMultipleTimes()
    {
        final int NUM_ENDS = 3;
        final List<ListenableFuture<Void>> endFutures = makeFutureList(NUM_ENDS);
        for (int i = 0; i < NUM_ENDS; i++) {
            endFutures.add(_stream.end_());
        }

        for (ListenableFuture<Void> endFuture : endFutures) {
            assertTrue(endFuture.isDone());
            assertThrows(endFuture, ExStreamInvalid.class);
        }
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