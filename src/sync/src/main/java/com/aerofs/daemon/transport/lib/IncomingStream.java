package com.aerofs.daemon.transport.lib;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.newControl;
import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.newPauseIncomingStreamHeader;
import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.newResumeIncomingStreamHeader;

/**
 * InputStream (synchronous, blocking) wrapper for reading chunks of data multiplexed over a Netty
 * channel (asynchronous, non-blocking)
 *
 * When a remote peer initiates a stream, StreamManager will create an IncomingStream object.
 * Incoming stream chunks are buffered here and the core can be read synchronously from the core.
 *
 * Flow control is based on a watermark system to prevent unbounded accumulation of buffered data.
 * When the buffer grows too large, the remote peer is asked to temporarily suspend writes.
 * If the sender keeps writing and a second watermark is reached, the receiver will abort the
 * stream.
 *
 * NB: core assumptions
 *   - a single IO thread will offer data
 *   - a single core thread will read data
 *
 * TODO: use a Sink concept to directly pipe file content to a file from Netty io thread
 */
public final class IncomingStream extends InputStream {
    private final static Logger l = Loggers.getLogger(IncomingStream.class);

    private final static int LO_WATERMARK = 64;
    private final static int HI_WATERMARK = 128;
    private final static int BRK_WATERMARK = 512;

    private static final AtomicLongFieldUpdater<IncomingStream> STATE_UPDATER =
            AtomicLongFieldUpdater.newUpdater(IncomingStream.class, "_state");

    private static final AtomicLongFieldUpdater<IncomingStream> QUEUE_UPDATER =
            AtomicLongFieldUpdater.newUpdater(IncomingStream.class, "_queueSize");

    private final long NONE = 0;
    private final long STREAMING = 1;
    private final long FAILED = 2;
    private final long CLOSED = 3;

    private volatile long _state;
    private volatile InvalidationReason _reason;

    private final StreamKey _sk;
    final Channel _channel;
    private final long _timeout;

    private int _seq;

    private ChannelBuffer _head;

    private volatile boolean _paused;
    // linked queue doesn't keep track of size efficiently...
    private volatile long _queueSize;
    private final Queue<ChannelBuffer> _data = new ConcurrentLinkedQueue<>();

    public IncomingStream(StreamKey sk, Channel channel, long timeout) {
        _sk = sk;
        _channel = channel;
        _state = NONE;
        _timeout = timeout;
        _queueSize = 0;
    }

    // for testing only
    public boolean hasBegun()
    {
        return _state > NONE;
    }

    // for testing only
    public long bufferedChunkCount() {
        return _queueSize;
    }

    public boolean begin()
    {
        return STATE_UPDATER.compareAndSet(this, NONE, STREAMING);
    }

    // NB: call from io thread
    public final void offer(ChannelBuffer s) throws ExStreamInvalid
    {
        long state = _state;
        if (state != STREAMING) {
            throw new ExStreamInvalid(state == CLOSED ? InvalidationReason.ENDED : _reason);
        }
        _data.offer(s);
        if (QUEUE_UPDATER.getAndIncrement(this) > HI_WATERMARK) increaseBackpressure();
        synchronized (this) { notify(); }
    }

    // NB: call from io thread
    public void offer(int seq, ChannelBuffer s) throws ExStreamInvalid
    {
        if (seq != ++_seq) {
            l.error("{} seq mismatch {} != {}", _channel, seq, _seq);
            fail(InvalidationReason.OUT_OF_ORDER);
        } else {
            l.trace("{} recv {}", _channel, seq);
            offer(s);
        }
    }

    public synchronized void fail(InvalidationReason reason)
    {
        if (!STATE_UPDATER.compareAndSet(this, STREAMING, FAILED)) return;
        _reason = reason;
        l.debug("{} fail", _channel);
        notify();
    }

    @Override
    public synchronized void close()
    {
        if (!STATE_UPDATER.compareAndSet(this, STREAMING, CLOSED)) return;
        _head = null;
        _data.clear();
        QUEUE_UPDATER.set(this, 0);
        if (_paused) _channel.getPipeline().execute(this::relieveBackpressure);
        notify();
    }

    // NB: call from io thread
    private void relieveBackpressure()
    {
        if (_paused && _queueSize < LO_WATERMARK) {
            l.debug("{} resume stream {} {}", _channel, _sk, _queueSize);
            _channel.write(newControl(newResumeIncomingStreamHeader(_sk.strmid)));
            _paused = false;
        }
    }

    // NB: call from io thread
    private void increaseBackpressure()
    {
        if (_queueSize > BRK_WATERMARK) {
            l.warn("{} stream queue overflow {} {}", _channel, _sk, _queueSize);
            fail(InvalidationReason.CHOKE_ERROR);
        } else if (!_paused) {
            l.debug("{} pause stream {} {}", _channel, _sk, _queueSize);
            _channel.write(newControl(newPauseIncomingStreamHeader(_sk.strmid)));
            _paused = true;
        }
    }

    @Override
    public int read() throws IOException {
        byte[] d = new byte[1];
        if (read(d, 0, 1) != 1) {
            throw new IOException("internal stream error");
        }
        return d[0] & 0xff;
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException
    {
        if (_state == CLOSED) throw new IOException("stream closed");
        if (len == 0) return 0;
        int total = 0;
        while (len > 0 && (!isEmpty(_head) || fetchMore(total == 0))) {
            int n = Math.min(len, _head.readableBytes());
            _head.readBytes(b, off, n);
            total += n;
            off += n;
            len -= n;
        }
        if (total <= 0) {
            l.warn("bad stream {} {} {} {}", _sk, _channel, _state, _head);
            throw new IOException("internal stream error");
        }
        l.trace("{} read {}", _channel, total);
        return total;
    }

    private static boolean isEmpty(@Nullable ChannelBuffer buf)
    {
        return buf == null || !buf.readable();
    }

    private boolean fetchMore(boolean blocking) throws IOException
    {
        if ((_head = _data.poll()) == null) {
            if (!blocking) return false;
            waitForMoreData();
        }
        if (QUEUE_UPDATER.getAndDecrement(this) < LO_WATERMARK && _paused) {
            _channel.getPipeline().execute(this::relieveBackpressure);
        }
        return true;
    }

    private void waitForMoreData() throws IOException
    {
        ElapsedTimer timer = new ElapsedTimer();
        while ((_head = _data.poll()) == null) {
            if (timer.elapsed() >= _timeout) throw new IOException("stream timeout");
            synchronized (this) {
                if (_state == CLOSED) throw new IOException("stream closed");
                if (_state == FAILED) throw new IOException("stream failed: " + _reason);
                if (_paused) {
                    _channel.getPipeline().execute(this::relieveBackpressure);
                }
                try {
                    l.trace("{} wait for more data", _channel);
                    wait(_timeout);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        }
    }
}
