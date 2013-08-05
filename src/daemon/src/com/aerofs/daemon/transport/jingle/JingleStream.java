/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import com.aerofs.j.StreamEvent;
import com.aerofs.j.StreamInterface;
import com.aerofs.j.StreamInterface_EventSlot;
import com.aerofs.j.StreamResult;
import com.aerofs.j.StreamState;
import com.aerofs.j.j;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.ex.ExJingle;
import com.google.common.collect.Queues;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.slf4j.Logger;

import java.util.Queue;
import java.util.concurrent.Semaphore;

import static com.aerofs.daemon.lib.DaemonParam.Jingle.QUEUE_LENGTH;
import static com.aerofs.j.StreamEvent.SE_CLOSE;
import static com.aerofs.j.StreamEvent.SE_OPEN;
import static com.aerofs.j.StreamEvent.SE_READ;
import static com.aerofs.j.StreamEvent.SE_WRITE;
import static com.google.common.base.Preconditions.checkState;

// A JingleStream object represents a bidirectional jingle stream
//
// N.B. all the methods of this class must be called within the signal thread.
//
public class JingleStream implements IProxyObjectContainer
{
    private static final Logger l = Loggers.getLogger(JingleStream.class);

    static interface IJingleStreamListener
    {
        void onJingleStreamConnected(JingleStream stream);

        void onIncomingMessage(DID did, byte[] packet);

        /**
         * The stream was closed unexpectedly (either because the remote peer closed it, or an
         * exception was thrown).
         * This callback is not invoked when close() is called on the stream since this would be an
         * expected closure.
         */
        void onJingleStreamClosed(JingleStream stream);
    }

    private final StreamInterface _streamInterface;
    private final DID _did;
    private final boolean _incoming;
    private final IJingleStreamListener _listener;
    private final Queue<SendEvent> _sendQueue = Queues.newLinkedBlockingQueue(QUEUE_LENGTH);
    private final Thread _readThread;
    private final byte[] _readBuf = new byte[10 * C.KB];
    private final Semaphore _readSemaphore = new Semaphore(0);

    private volatile boolean _writable;
    private volatile boolean _isClosed;

    // keep a reference to prevent the slot being GC'ed (as there's no Java reference to this object otherwise)
    private final StreamInterface_EventSlot _slotEvent = new StreamInterface_EventSlot()
    {
        @Override
        public void onEvent(StreamInterface s, int event, int error)
        {
            try {
                onStreamEvent_(event, error);
            } catch (ExJingle e) {
                abort(e);
            }
        }
    };

    JingleStream(StreamInterface streamInterface, DID did, boolean incoming,
            IJingleStreamListener listener)
    {
        _streamInterface = streamInterface;
        _did = did;
        _incoming = incoming;
        _listener = listener;

        _slotEvent.connect(_streamInterface);
        _writable = (_streamInterface.GetState() == StreamState.SS_OPEN);

        _readThread = ThreadUtil.startDaemonThread("j-read-" + toString(), new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    _readSemaphore.acquire(); // Wait until libjingle tells us we can start reading
                    read();
                } catch (ExJingle e) {
                    abort(e);
                } catch (InterruptedException e) {
                    // ignore
                }

                l.info("j: read thread terminated: {}", JingleStream.this);
            }
        });
    }

    private static boolean hasStreamFlag(int actualEvent, StreamEvent expectedEvent)
    {
        return (actualEvent & expectedEvent.swigValue()) != 0;
    }

    private void onStreamEvent_(int event, int error)
        throws ExJingle
    {
        if (hasStreamFlag(event, SE_WRITE) && hasStreamFlag(event, SE_READ) && hasStreamFlag(event, SE_OPEN)) {
            if (_listener != null) _listener.onJingleStreamConnected(JingleStream.this);
        }

        if (hasStreamFlag(event, SE_WRITE)) {
            _writable = true;
            write_();
        }

        if (hasStreamFlag(event, SE_READ)) {
            _readSemaphore.release();
        }

        if (hasStreamFlag(event, SE_CLOSE)) {
            throw new ExJingle("jds: stream err:" + error);
        }
    }

    /**
     * Closes the stream and fails any pending writes
     * This method is thread-safe.
     * Note: this method will *not* notifiy the listener that the stream has been closed, since it
     * is the result of a voluntary action.
     */
    void close(Exception reason)
    {
        l.info("close jingle stream {} reason: {}", this, reason);

        _isClosed = true;

        _readThread.interrupt();

        _streamInterface.Close();

        // Drain the queue and notify the core that the packets failed to be sent
        SendEvent event;
        while ((event = _sendQueue.poll()) != null) {
            event.getFuture().setFailure(reason);
        }
    }

    /**
     * Closes the stream and notifies the listener
     */
    private void abort(ExJingle e)
    {
        close(e);
        if (_listener != null) _listener.onJingleStreamClosed(this);
    }


    void send_(MessageEvent event)
    {
        try {
            boolean success = _sendQueue.offer(new SendEvent(event));
            if (!success) throw new ExNoResource("jingle q full");
            if (_writable) write_();

        } catch (Exception e) {
            l.warn("drop packet d:{}", _did, e);
            event.getFuture().setFailure(e);
        }
    }

    /**
     * Enqueues all pending writes onto libjingle's queue until it tells us to block.
     *
     * This method must be called from the signal thread. It actually doesn't matter since libjingle's
     * stream API is thread-safe, however, using a dedicated thread doesn't bring any speed
     * improvement.
     *
     * @throws ExJingle if libjingle's tells us there was an error while writing to the stream
     */
    private void write_() throws ExJingle
    {
        int[] written = { 0 };
        int[] error = { 0 };

        // Iterate through the send event queue, but only remove an event from the queue after we've
        // either successfully sent it, or we got an error.

        SendEvent event;
        while (!_isClosed && (event = _sendQueue.peek()) != null) {

            StreamResult res = j.WriteAll(_streamInterface, event.buffer(), event.readIndex(),
                    event.readableBytes(), written, error);

            if (written[0] > 0) {
                event.markRead(written[0]);
                Channels.fireWriteComplete(event.getChannel(), written[0]);
            }

            // Invariant: if StreamInterface::WriteAll() doesn't return SR_SUCCESS, then there are
            // still some bytes to write.
            checkState(res == StreamResult.SR_SUCCESS || event.readableBytes() > 0);

            if (res == StreamResult.SR_SUCCESS) {
                checkState(event.readableBytes() == 0);
                event.getFuture().setSuccess();
                _sendQueue.poll(); // remove the head of the queue

            } else if (res == StreamResult.SR_ERROR || res == StreamResult.SR_EOS) {
                String msg = "write returns " + res + " error " + error[0] + " str:" + toString();
                l.warn(msg);
                event.getFuture().setFailure(new ExJingle(msg));
                _sendQueue.poll(); // remove the head of the queue

            } else if (res == StreamResult.SR_BLOCK) {
                _writable = false;
                return;

            } else {
                throw new IllegalStateException("unknown stream result: " + res);
            }
        }
    }

    /**
     * Read chunks out of libjingle and send them to the listener for processing.
     * This method runs in its own thread.
     * @throws ExJingle if we couldn't read from the stream
     */
    private void read() throws ExJingle
    {
        int[] read = { 0 };
        int[] error = { 0 };
        StreamResult res;

        while (!_isClosed) {
            res = j.ReadAll(_streamInterface, _readBuf, 0, _readBuf.length, read, error);

            if (read[0] > 0) {
                byte[] bytes = new byte[read[0]];
                System.arraycopy(_readBuf, 0, bytes, 0, bytes.length);
                _listener.onIncomingMessage(_did, bytes);
            }

            if (res == StreamResult.SR_ERROR || res == StreamResult.SR_EOS) {
                throw new ExJingle("read payload returns " + res + " error " + error[0]);
            } else if (res == StreamResult.SR_BLOCK) {
                try {
                    _readSemaphore.acquire();
                } catch (InterruptedException e) {
                    // continue the while loop
                }
            } else {
                checkState(res == StreamResult.SR_SUCCESS);
            }
        }
    }

    @Override
    public void delete_()
    {
        _streamInterface.delete();
        _slotEvent.delete();
    }

    @Override
    public void finalize()
    {
        // delete_() must have been called
        checkState(StreamInterface.getCPtr(_streamInterface) == 0);
    }

    @Override
    public String toString()
    {
        return (_incoming ? "I" : "O") + _did;
    }
}
