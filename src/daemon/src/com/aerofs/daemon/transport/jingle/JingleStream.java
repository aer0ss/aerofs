/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.exception.ExDeviceDisconnected;
import com.aerofs.daemon.transport.exception.ExReceiveFailed;
import com.aerofs.daemon.transport.exception.ExSendFailed;
import com.aerofs.j.StreamEvent;
import com.aerofs.j.StreamInterface;
import com.aerofs.j.StreamInterface_EventSlot;
import com.aerofs.j.StreamResult;
import com.aerofs.j.StreamState;
import com.aerofs.j.j;
import com.google.common.collect.Queues;
import org.jboss.netty.channel.MessageEvent;
import org.slf4j.Logger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aerofs.daemon.lib.DaemonParam.Jingle.QUEUE_LENGTH;
import static com.aerofs.j.StreamEvent.SE_CLOSE;
import static com.aerofs.j.StreamEvent.SE_OPEN;
import static com.aerofs.j.StreamEvent.SE_READ;
import static com.aerofs.j.StreamEvent.SE_WRITE;
import static com.aerofs.j.StreamResult.SR_BLOCK;
import static com.aerofs.j.StreamResult.SR_EOS;
import static com.aerofs.j.StreamResult.SR_ERROR;
import static com.aerofs.j.StreamResult.SR_SUCCESS;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.jboss.netty.channel.Channels.fireWriteComplete;

/**
 * Represents a bi-directional libjingle stream to a device
 * identified by {@code DID}. This object wraps a libjingle C++ {@code StreamInterface}.
 */
class JingleStream
{
    /**
     * Implemented by classes that want to be notified of events occurring to the libjingle
     */
    static interface IJingleStreamListener
    {
        /**
         * Invoked when the stream is connected and data can be written/read from it
         */
        void onJingleStreamConnected(JingleStream stream);

        /**
         * Invoked when some bytes are received from a device. These bytes may contain only part of a packet,
         * multiple packets, etc. In other words, it is up to the listener to perform packet reassembly.
         */
        void onIncomingMessage(DID did, byte[] data);

        /**
         * The stream was closed unexpectedly (either because the remote peer closed it, or an
         * exception was thrown). This callback is not invoked when {@link JingleStream#close(Throwable)}
         * is called on the stream since this would be an expected closure.
         */
        void onJingleStreamClosed(JingleStream stream);
    }

    private static final Logger l = Loggers.getLogger(JingleStream.class);

    private final StreamInterface _streamInterface;
    private final DID _did;
    private final boolean _incoming;
    private final IJingleStreamListener _listener;
    private final BlockingQueue<SendEvent> _sendQueue = Queues.newLinkedBlockingQueue(QUEUE_LENGTH);
    private final byte[] _readBuf = new byte[10 * C.KB];
    private final AtomicBoolean _isClosed = new AtomicBoolean(false);

    private volatile Exception _closeCause;
    private volatile boolean _writable;

    // keep a reference to prevent the slot being GC'ed (as there's no Java reference to this object otherwise)
    private final StreamInterface_EventSlot _slotEvent = new StreamInterface_EventSlot()
    {
        @Override
        public void onEvent(StreamInterface s, int event, int error)
        {
            try {
                onStreamEvent_(event, error);
            } catch (Exception e) {
                abort(e);
            }
        }
    };

    JingleStream(DID did, StreamInterface streamInterface, boolean incoming, IJingleStreamListener listener)
    {
        _streamInterface = streamInterface;
        _did = did;
        _incoming = incoming;
        _listener = checkNotNull(listener);

        _slotEvent.connect(_streamInterface);
        _writable = (_streamInterface.GetState() == StreamState.SS_OPEN);
    }

    private static boolean hasStreamFlag(int actualEvent, StreamEvent expectedEvent)
    {
        return (actualEvent & expectedEvent.swigValue()) != 0;
    }

    private void onStreamEvent_(int event, int error)
            throws ExDeviceDisconnected, ExReceiveFailed
    {
        if (hasStreamFlag(event, SE_WRITE) && hasStreamFlag(event, SE_READ) && hasStreamFlag(event, SE_OPEN)) {
            _listener.onJingleStreamConnected(JingleStream.this);
        }

        if (hasStreamFlag(event, SE_WRITE)) {
            _writable = true;
            write();
        }

        if (hasStreamFlag(event, SE_READ)) {
            read();
        }

        if (hasStreamFlag(event, SE_CLOSE)) {
            throw new ExDeviceDisconnected(String.format("%s: stream error (code:%d)", this, error));
        }
    }

    /**
     * Closes the stream and fails any pending writes
     * This method will <strong>NOT</strong> notifiy the listener that the stream
     * has been closed, since it is the result of a voluntary action. This method
     * <strong>MUST</strong> be called on the signal thread.
     *
     * @param cause reason for which the stream is closed
     */
    void close(Throwable cause)
    {
        if (_isClosed.getAndSet(true)) {
            return;
        }

        // NOTE: this is not atomic, but it doesn't matter
        // if _closeCause is null, we'll use the supplied exception, which isn't great, but OK
        // if it's not null, it'll never change, so we'll use the right value
        Throwable closeCause  = _closeCause == null ? cause : _closeCause;

        l.info("{}: close stream: cause:{}", this, cause);

        _streamInterface.Close(); // MUST be called on the signal thread

        // Drain the queue and notify the core that the packets failed to be sent
        l.info("{}: drain send queue", this);

        SendEvent event;
        while ((event = _sendQueue.poll()) != null) {
            event.getFuture().setFailure(closeCause);
        }
    }

    /**
     * Closes the stream and notifies the listener
     */
    private void abort(Exception cause)
    {
        l.warn("{}: abort stream", this, cause);

        _closeCause = cause;
        _listener.onJingleStreamClosed(this);
    }

    /**
     * Sends bytes to the remote device. <strong>MUST</strong> be
     * called on the signal thread.
     *
     * @param event {@link org.jboss.netty.channel.MessageEvent} with
     * the bytes to be sent
     */
    void send(MessageEvent event)
    {
        try {
            if (_isClosed.get()) {
                throw new ExSendFailed(String.format("%s: attempting write after close", this));
            }

            boolean success = _sendQueue.offer(new SendEvent(event));
            if (!success) throw new ExNoResource(String.format("%s: send queue full", this));
            if (_writable) write();

        } catch (Exception e) {
            l.warn("{}: fail send packet", this, e);
            event.getFuture().setFailure(e);
        }
    }

    /**
     * Enqueues all pending writes onto libjingle's queue until it tells us to block.
     * This method must be called from the signal thread.
     */
    private void write()
    {
        int[] written = { 0 };
        int[] error = { 0 };

        // Iterate through the send event queue, but only remove an event from the queue after we've
        // either successfully sent it, or we got an error.

        SendEvent event;
        while (!_isClosed.get() && (event = _sendQueue.peek()) != null) {

            StreamResult res = j.WriteAll(_streamInterface, event.buffer(), event.readIndex(), event.readableBytes(), written, error);

            if (written[0] > 0) {
                event.markRead(written[0]);
                fireWriteComplete(event.getChannel(), written[0]);
            }

            // Invariant: if StreamInterface::WriteAll() doesn't return SR_SUCCESS, then there are
            // still some bytes to write.
            checkState(res == SR_SUCCESS || event.readableBytes() > 0);

            if (res == SR_SUCCESS) {
                checkState(event.readableBytes() == 0);
                event.getFuture().setSuccess();
                _sendQueue.poll(); // remove the head of the queue

            } else if (res == SR_ERROR || res == SR_EOS) {
                String msg = String.format("%s: fail write: cause:%s (code:%d)", this, res, error[0]);
                l.warn(msg);
                event.getFuture().setFailure(new ExSendFailed(msg));
                _sendQueue.poll(); // remove the head of the queue

            } else if (res == SR_BLOCK) {
                _writable = false;
                return;

            } else {
                throw new IllegalStateException(String.format("%s: unknown StreamResult:%s", this, res));
            }
        }
    }

    /**
     * Read chunks out of libjingle and send them to the listener for processing.
     * This method <strong>MUST</strong> be called from the signal thread.
     *
     * @throws ExReceiveFailed if we couldn't read from the stream
     */
    private void read()
            throws ExReceiveFailed
    {
        int[] read = { 0 };
        int[] error = { 0 };
        StreamResult res = SR_SUCCESS;

        while (!_isClosed.get() && res != SR_BLOCK) {
            res = j.ReadAll(_streamInterface, _readBuf, 0, _readBuf.length, read, error);

            checkState(res == SR_SUCCESS || res == SR_BLOCK || res == SR_ERROR || res == SR_EOS, "%s: unknown StreamResult:%s", this, res);

            if (read[0] > 0) {
                byte[] bytes = new byte[read[0]];
                System.arraycopy(_readBuf, 0, bytes, 0, bytes.length);
                _listener.onIncomingMessage(_did, bytes);
            }

            if (res == SR_ERROR || res == SR_EOS) {
                throw new ExReceiveFailed(String.format("%s: fail read: cause:%s (code:%d)", this, res, error[0]));
            }
        }
    }

    /**
     * Delete the underlying C++ libjingle objects and
     * set their C++ pointers to null. <strong>MUST</strong> be
     * called on the signal thread.
     */
    void delete()
    {
        l.info("{}: delete stream", this);
        _streamInterface.delete();
        _slotEvent.delete();
    }

    @SuppressWarnings("FinalizeDoesntCallSuperFinalize")
    @Override
    public void finalize()
    {
        checkState(StreamInterface.getCPtr(_streamInterface) == 0); // check that delete() was called
    }

    @Override
    public String toString()
    {
        // IMPORTANT: DO NOT ACCESS _streamInterface or _slotEvent here because
        // this method may be called from arbitrary threads, and neither C++ object
        // is thread-safe
        return (_incoming ? "I" : "O") + _did;
    }
}
