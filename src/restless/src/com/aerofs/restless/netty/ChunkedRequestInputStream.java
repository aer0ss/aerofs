package com.aerofs.restless.netty;

import com.aerofs.base.Loggers;
import com.google.common.collect.Queues;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;

/**
 * A simple {@link InputStream} implementation that operates on a queue of {@link ChannelBuffer}
 * populated asynchronously from a Netty {@link Channel}
 *
 * This class can be safely accessed from any thread but may only be used by a single consumer
 * at any given time.
 *
 * Closing the stream will discard any currently buffered packet immediately and any new packet
 * as they arrive.
 */
public class ChunkedRequestInputStream extends InputStream
{
    private final static Logger l = Loggers.getLogger(ChunkedRequestInputStream.class);

    // limit number of queued chunks to avoid OOM
    // NB: the limit is enforced by disabling reads on the underlying Channel
    private final static int MAX_QUEUED_CHUNKS = 10;

    private enum State
    {
        STREAMING,
        AT_END,
        FAILED,
        CLOSED
    }

    private volatile State _state;

    private final Channel _channel;

    private AtomicBoolean _sendContinue;
    private ChannelBuffer _current;
    private final Queue<ChannelBuffer> _content = Queues.newConcurrentLinkedQueue();

    ChunkedRequestInputStream(Channel channel, boolean sendContinue)
    {
        _sendContinue = new AtomicBoolean(sendContinue);
        _channel = channel;
        _state = State.STREAMING;
    }

    @Override
    public int read() throws IOException
    {
        return hasMore_() ? _current.readByte() : -1;
    }

    @Override
    public int read(@Nonnull byte b[]) throws IOException
    {
        return read(b, 0, b.length);
    }

    @Override
    public int read(@Nonnull byte b[], int off, int len) throws IOException
    {
        int n = 0;
        while (n < len && hasMore_()) {
            int avail = Math.min(len - n, _current.readableBytes());
            _current.readBytes(b, off + n, avail);
            n += avail;
        }
        return n == 0 && len != 0 ? -1 : n;
    }

    @Override
    public synchronized void close()
    {
        _state = State.CLOSED;
        _current = null;
        _content.clear();
        _channel.setReadable(true);
        notify();
    }

    private boolean hasMore_() throws IOException
    {
        if(_state == State.CLOSED) throw new IOException("stream closed");
        // send 100 Continue on first attempt to read from buffer (if request expects it)
        if (_sendContinue.compareAndSet(false, true)) {
            _channel.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.CONTINUE));
        }
        if (isEmpty(_current)) {
            while (isEmpty(_current = _content.poll())) {
                synchronized (this) {
                    if (_state == State.CLOSED) throw new IOException("stream closed");
                    if (_state == State.AT_END) return false;
                    if (_state == State.FAILED) throw new IOException("stream failed");
                    try {
                        _channel.setReadable(true);
                        wait();
                    } catch (InterruptedException e) {
                        throw new IOException(e.getCause());
                    }
                }
            }
        }
        return true;
    }

    private static boolean isEmpty(ChannelBuffer buf)
    {
        return buf == null || !buf.readable();
    }

    public void offer(ChannelBuffer content)
    {
        if (_state == State.CLOSED) return;
        checkState(_state == State.STREAMING);
        _content.offer(content);
        synchronized (this) { notify(); }
        _channel.setReadable(_content.size() < MAX_QUEUED_CHUNKS);
    }

    public synchronized void fail()
    {
        if (_state == State.CLOSED) return;
        _state = State.FAILED;
        l.info("fail");
        notify();
    }

    public synchronized void end()
    {
        if (_state == State.CLOSED) return;
        checkState(_state == State.STREAMING);
        _state = State.AT_END;
        notify();
    }
}
