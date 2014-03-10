/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExIOFailed;
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

import static com.aerofs.daemon.lib.DaemonParam.QUEUE_LENGTH_DEFAULT;
import static com.aerofs.j.StreamEvent.SE_CLOSE;
import static com.aerofs.j.StreamEvent.SE_OPEN;
import static com.aerofs.j.StreamEvent.SE_READ;
import static com.aerofs.j.StreamEvent.SE_WRITE;
import static com.aerofs.j.StreamResult.SR_BLOCK;
import static com.aerofs.j.StreamResult.SR_EOS;
import static com.aerofs.j.StreamResult.SR_ERROR;
import static com.aerofs.j.StreamResult.SR_SUCCESS;
import static com.google.common.base.Preconditions.checkState;
import static org.jboss.netty.channel.Channels.fireWriteComplete;

/**
 * Represents a bi-directional libjingle stream to a device
 * identified by {@code DID}. This object wraps a libjingle C++ {@code StreamInterface}.
 * <br/>
 * All methods <strong>must</strong> be called on the
 * {@link com.aerofs.daemon.transport.jingle.SignalThread}.
 */
class JingleStream
{
    /**
     * Implemented by classes that want to be notified of events
     * that occur on a {@link com.aerofs.daemon.transport.jingle.JingleStream}.
     */
    static interface IJingleStreamListener
    {
        /**
         * Invoked when the stream is connected and data can be written/read from it.
         */
        void onJingleStreamConnected(JingleStream stream);

        /**
         * Invoked when some bytes are received from a device. These bytes may contain only part of a packet,
         * multiple packets, etc. In other words, it is up to the listener to perform packet reassembly.
         */
        void onIncomingMessage(DID did, byte[] data);

        /**
         * The stream was closed unexpectedly (either because the remote device closed it, or an
         * exception was thrown). This callback is not invoked when {@link JingleStream#close(Throwable)}
         * is called on the stream since this would be an expected closure.
         */
        void onJingleStreamClosed(JingleStream stream);
    }

    private static final Logger l = Loggers.getLogger(JingleStream.class);

    private final DID remotedid;
    private final boolean incoming;
    private final SignalThread signalThread; // only use to assert that methods are called on the SignalThread
    private final StreamInterface streamInterface;
    private final IJingleStreamListener listener;
    private final BlockingQueue<SendEvent> sendQueue = Queues.newLinkedBlockingQueue(QUEUE_LENGTH_DEFAULT);
    private final byte[] readBuffer = new byte[10 * C.KB];

    private boolean streamClosed;
    private boolean objectsDeleted;
    private Exception closeCause;
    private boolean writable;

    // keep a reference to prevent the slot being GC'ed (as there's no Java reference to this object otherwise)
    private final StreamInterface_EventSlot slotEvent = new StreamInterface_EventSlot()
    {
        @Override
        public void onEvent(StreamInterface s, int event, int error)
        {
            try {
                onStreamEvent(event, error);
            } catch (Exception e) {
                abort(e);
            }
        }
    };

    JingleStream(DID remotedid, boolean incoming, SignalThread signalThread, StreamInterface streamInterface, IJingleStreamListener listener)
    {
        this.remotedid = remotedid;
        this.incoming = incoming;
        this.signalThread = signalThread;
        this.streamInterface = streamInterface;
        this.listener = listener;

        this.slotEvent.connect(this.streamInterface);
        this.writable = (this.streamInterface.GetState() == StreamState.SS_OPEN);
    }

    private static boolean hasStreamFlag(int actualEvent, StreamEvent expectedEvent)
    {
        return (actualEvent & expectedEvent.swigValue()) != 0;
    }

    private void onStreamEvent(int event, int error)
            throws ExDeviceUnavailable, ExIOFailed
    {
        if (streamClosed) {
            l.warn("{}: dropping StreamEvent because stream is closed", this);
            return;
        }

        if (hasStreamFlag(event, SE_WRITE) && hasStreamFlag(event, SE_READ) && hasStreamFlag(event, SE_OPEN)) {
            listener.onJingleStreamConnected(JingleStream.this);
        }

        if (hasStreamFlag(event, SE_WRITE)) {
            writable = true;
            write();
        }

        if (hasStreamFlag(event, SE_READ)) {
            read();
        }

        if (hasStreamFlag(event, SE_CLOSE)) {
            throw new ExDeviceUnavailable(String.format("%s: stream closed: error (code:%d)", this, error));
        }
    }

    /**
     * Closes the stream and fails any pending writes
     * This method will <strong>NOT</strong> notifiy the listener that the stream
     * has been closed, since it is the result of a voluntary action.
     *
     * @param cause reason for which the stream is closed
     */
    void close(Throwable cause)
    {
        signalThread.assertSignalThread();

        if (streamClosed) {
            l.warn("{}: stream already closed", this);
            return;
        }

        streamClosed = true;

        Throwable closeCause  = this.closeCause == null ? cause : this.closeCause;

        l.info("{}: close stream", this, cause);

        checkState(!objectsDeleted, "%s: C++ objects already deleted", this);
        streamInterface.Close();

        l.debug("{}: drain and fail", this);

        SendEvent event;
        while ((event = sendQueue.poll()) != null) {
            event.getWriteFuture().setFailure(closeCause);
        }
    }

    /**
     * Closes the stream and notifies the listener
     */
    private void abort(Exception cause)
    {
        l.warn("{}: abort stream", this, cause);

        closeCause = cause;
        close(cause);

        listener.onJingleStreamClosed(this);
    }

    /**
     * Sends bytes to the remote device.
     *
     * @param event {@link org.jboss.netty.channel.MessageEvent} with
     * the bytes to be sent
     */
    void send(MessageEvent event)
    {
        signalThread.assertSignalThread();

        try {
            if (streamClosed) {
                throw new ExIOFailed(String.format("%s: attempting write after close", this));
            }

            boolean success = sendQueue.offer(new SendEvent(event));
            if (!success) throw new ExNoResource(String.format("%s: send queue full", this));
            if (writable) write();

        } catch (Exception e) {
            l.warn("{}: fail send packet", this, e);
            event.getFuture().setFailure(e);
        }
    }

    /**
     * Writes as many bytes as possible onto the underlying
     * {@link com.aerofs.j.StreamInterface} until it blocks.
     */
    private void write()
    {
        int[] written = { 0 };
        int[] error = { 0 };

        // Iterate through the send event queue, but only remove an event from the queue after we've
        // either successfully sent it, or we got an error.

        SendEvent event;
        while (!streamClosed && (event = sendQueue.peek()) != null) {

            StreamResult res = j.WriteAll(streamInterface, event.buffer(), event.readIndex(), event.readableBytes(), written, error);

            if (written[0] > 0) {
                event.markRead(written[0]);
                fireWriteComplete(event.getChannel(), written[0]);
            }

            // Invariant: if StreamInterface::WriteAll() doesn't return SR_SUCCESS, then there are
            // still some bytes to write.
            checkState(res == SR_SUCCESS || event.readableBytes() > 0);

            if (res == SR_SUCCESS) {
                checkState(event.readableBytes() == 0);
                event.getWriteFuture().setSuccess();
                sendQueue.poll(); // remove the head of the queue

            } else if (res == SR_ERROR || res == SR_EOS) {
                String msg = String.format("%s: fail write: cause:%s (code:%d)", this, res, error[0]);
                l.warn(msg);
                event.getWriteFuture().setFailure(new ExIOFailed(msg));
                sendQueue.poll(); // remove the head of the queue

            } else if (res == SR_BLOCK) {
                writable = false;
                return;

            } else {
                throw new IllegalStateException(String.format("%s: unknown StreamResult:%s", this, res));
            }
        }
    }

    /**
     * Read chunks out of libjingle and send them to the listener for processing.
     *
     * @throws com.aerofs.daemon.transport.ExIOFailed if we couldn't read from the stream
     */
    private void read()
            throws ExIOFailed
    {
        int[] read = { 0 };
        int[] error = { 0 };
        StreamResult res = SR_SUCCESS;

        while (!streamClosed && res != SR_BLOCK) {
            res = j.ReadAll(streamInterface, readBuffer, 0, readBuffer.length, read, error);

            checkState(res == SR_SUCCESS || res == SR_BLOCK || res == SR_ERROR || res == SR_EOS, "%s: unknown StreamResult:%s", this, res);

            if (read[0] > 0) {
                byte[] bytes = new byte[read[0]];
                System.arraycopy(readBuffer, 0, bytes, 0, bytes.length);
                listener.onIncomingMessage(remotedid, bytes);
            }

            if (res == SR_ERROR || res == SR_EOS) {
                throw new ExIOFailed(String.format("%s: fail read: cause:%s (code:%d)", this, res, error[0]));
            }
        }
    }

    /**
     * Delete the underlying C++ libjingle objects and
     * set their C++ pointers to null.
     */
    void delete()
    {
        signalThread.assertSignalThread();

        checkState(streamClosed, "%s: stream not closed first", this);

        if (objectsDeleted) {
            l.warn("{}: C++ objects already deleted", this);
            return;
        }

        l.debug("{}: deleting C++ objects", this);

        streamInterface.delete();
        slotEvent.delete();

        objectsDeleted = true;
    }

    @Override
    public void finalize()
            throws Throwable
    {
        checkState(StreamInterface.getCPtr(streamInterface) == 0, "underlying streamInterface was not deleted");
        super.finalize();
    }

    @Override
    public String toString()
    {
        // IMPORTANT: DO NOT ACCESS streamInterface or slotEvent here because
        // this method may be called from arbitrary threads, and neither C++ object
        // is thread-safe
        return (incoming ? "I" : "O") + remotedid;
    }
}
