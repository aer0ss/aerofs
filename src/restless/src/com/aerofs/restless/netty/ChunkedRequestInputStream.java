package com.aerofs.restless.netty;

import com.aerofs.base.Loggers;
import com.google.common.base.Preconditions;
import com.google.common.collect.Queues;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;

/**
 * A simple {@link InputStream} implementation that operates on a queue of {@link ChannelBuffer}
 * populated asynchronously from a Netty {@link Channel}
 *
 * This class can be safely accessed from any thread but may only be used by a single consumer
 * at any given time.
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
        FAILED
    }

    private volatile State _state;

    private final Channel _channel;

    private ChannelBuffer _current;
    private final Queue<ChannelBuffer> _content = Queues.newConcurrentLinkedQueue();

    ChunkedRequestInputStream(Channel channel)
    {
        _channel = channel;
        _state = State.STREAMING;
    }

    @Override
    public int read() throws IOException
    {
        return hasMore_() ? _current.readByte() : -1;
    }

    @Override
    public int read(byte b[]) throws IOException
    {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException
    {
        int n = 0;
        while (n < len && hasMore_()) {
            int avail = Math.min(len - n, _current.readableBytes());
            _current.readBytes(b, off + n, avail);
            n += avail;
        }
        return n == 0 && len != 0 ? -1 : n;
    }

    private boolean hasMore_() throws IOException
    {
        if (isEmpty(_current)) {
            while (isEmpty(_current = _content.poll())) {
                synchronized (this) {
                    if (_state == State.AT_END) return false;
                    if (_state == State.FAILED) throw new IOException();
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
        Preconditions.checkState(_state == State.STREAMING);
        _content.offer(content);
        synchronized (this) { notify(); }
        _channel.setReadable(_content.size() < MAX_QUEUED_CHUNKS);
    }

    public synchronized void fail()
    {
        _state = State.FAILED;
        l.info("fail");
        notify();
    }

    public synchronized void end()
    {
        Preconditions.checkState(_state == State.STREAMING);
        _state = State.AT_END;
        notify();
    }
}
