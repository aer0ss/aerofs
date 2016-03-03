package com.aerofs.daemon.transport.lib;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.aerofs.proto.Transport.PBTPHeader;
import com.google.common.base.Throwables;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;
import static com.google.common.base.Preconditions.checkState;


/**
 * Simple synchronous and blocking wrapper for sending chunks of data multiplexed over a Netty
 * channel (asynchronous, non-blocking)
 *
 * NB: core assumptions
 *   - a single core thread will write data
 *   - control callbacks from a single IO thread
 *
 * TODO: use ChunkedInput to directly read file content from Netty io thread
 */
public class OutgoingStream implements ChannelFutureListener, AutoCloseable {
    private static final Logger l = Loggers.getLogger(OutgoingStream.class);

    private final StreamManager _sm;
    private final StreamKey _sk;
    private final Channel _channel;
    private final long _timeout;

    // avoid pushing too many chunks into the channel
    // TODO: leverage channel writable bit (w/ virtual channel per stream to reflect paused bit)
    private final static int LO_WATERMARK = 10;
    private final static int HI_WATERMARK = 20;

    int _seq;

    enum State {
        STREAMING,
        FAILED,
        CLOSED
    }

    private volatile State _state;
    private Throwable _cause;
    private InvalidationReason _reason;

    private volatile boolean _paused;
    private AtomicInteger _queued = new AtomicInteger();

    OutgoingStream(StreamManager sm, StreamKey sk, Channel channel, long timeout) {
        _sm = sm;
        _sk = sk;
        _seq = -1;
        _channel = channel;
        _state = State.STREAMING;
        _timeout = timeout;
    }

    @Override
    public void close() {
        _sm.removeOutgoingStream(_sk);
    }

    public synchronized void pause() {
        if (_paused) return;
        _paused = true;
        l.debug("{} pause", _sk);
    }

    public synchronized void resume() {
        if (!_paused) return;
        _paused = false;
        l.debug("{} resume", _sk);
        notify();
    }

    public void write(byte[] payload) throws IOException
    {
        ElapsedTimer timer = new ElapsedTimer();
        while (_paused || _queued.get() > HI_WATERMARK) {
            if (timer.elapsed() >= _timeout) throw new IOException("stream timeout");
            synchronized (this) {
                try {
                    wait(_timeout);
                } catch (InterruptedException e) {
                    throw new ExStreamInvalid(InvalidationReason.INTERNAL_ERROR);
                }
            }
        }
        checkState(_state != State.CLOSED);
        if (_state == State.FAILED) throw new ExStreamInvalid(_reason);
        if (_cause != null) Throwables.propagateIfPossible(_cause, IOException.class);
        _queued.getAndIncrement();
        _channel.write(TransportProtocolUtil.newStreamPayload(_sk.strmid, ++_seq, payload))
                .addListener(this);
    }

    // receiver abort
    public synchronized void fail(InvalidationReason reason) {
        _state = State.FAILED;
        _reason = reason;
    }

    // sender abort
    public void abort(InvalidationReason reason)
    {
        if (_state != State.STREAMING) return;

        PBTPHeader h = PBTPHeader.newBuilder()
                .setType(STREAM)
                .setStream(PBStream
                        .newBuilder()
                        .setType(PBStream.Type.TX_ABORT_STREAM)
                        .setStreamId(_sk.strmid.getInt())
                        .setReason(reason))
                .build();

        _channel.write(TransportProtocolUtil.newControl(h));
    }

    @Override
    public void operationComplete(ChannelFuture cf) throws Exception {
        if (!cf.isSuccess() && _cause == null) {
            _cause = cf.getCause();
        }
        if (_queued.getAndDecrement() < LO_WATERMARK && !_paused) {
            synchronized (this) { notify(); }
        }
    }
}
