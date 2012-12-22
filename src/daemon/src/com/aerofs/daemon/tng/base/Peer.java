/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IStateContainer;
import com.aerofs.daemon.tng.base.pulse.StartPulseMessage;
import com.aerofs.daemon.tng.base.streams.NewOutgoingStream;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.async.FailedFutureCallback;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.base.id.SID;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

import static com.aerofs.daemon.lib.DaemonParam.CONNECT_TIMEOUT;
import static com.aerofs.daemon.lib.DaemonParam.QUEUE_LENGTH_DEFAULT;
import static com.aerofs.daemon.lib.Prio.LO;
import static com.aerofs.lib.async.FutureUtil.addCallback;

class Peer implements IPeer
{
    private static final Logger l = Util.l(Peer.class);
    private static final int MAX_NUM_CONNECTIONS = 2;

    private final DID _did;
    private final ISingleThreadedPrioritizedExecutor _executor;
    private final PeerConnectionFactory _connectionFactory;
    private final BlockingPrioQueue<Pending<?, ?>> _pending = createQueue();

    // Maintain concrete type here because we need to call destroy_(), which
    // isn't part of the interface for IStateContainer
    private final PeerStateContainer _peerState = new PeerStateContainer();

    private boolean _alive = true;

    @Nullable
    private PeerConnection _pendingConnection;
    private final LinkedList<PeerConnection> _connections = new LinkedList<PeerConnection>();

    private static BlockingPrioQueue<Pending<?, ?>> createQueue()
    {
        return new BlockingPrioQueue<Pending<?, ?>>(QUEUE_LENGTH_DEFAULT);
    }

    static Peer getInstance_(DID did, ISingleThreadedPrioritizedExecutor executor,
            PeerConnectionFactory connectionFactory)
    {
        return new Peer(did, executor, connectionFactory);
    }

    private Peer(DID did, ISingleThreadedPrioritizedExecutor executor,
            PeerConnectionFactory connectionFactory)
    {
        this._did = did;
        this._executor = executor;
        this._connectionFactory = connectionFactory;
    }

    void destroy_(Exception ex)
    {
        _alive = false;

        _peerState.destroy_();

        if (_pendingConnection != null) {
            // Keep a local reference to the pending connection, because
            // handleConnectFailed_() sets _pendingConnection to null
            final PeerConnection connection = _pendingConnection;
            handleConnectFailed_(ex);
            connection.disconnect_(ex);
        }

        // Destroy all connections, if they exist
        for (PeerConnection connection : _connections) {
            connection.disconnect_(ex);
        }
        _connections.clear();

        l.info("destroyed peer");
    }

    private void assertIsAlive()
    {
        assert _alive;
    }

    ListenableFuture<Void> sendDatagram_(SID sid, byte[] payload, Prio pri)
    {
        assertIsAlive();

        OutgoingUnicastPacket out = new OutgoingUnicastPacket(sid, payload);

        if (!_connections.isEmpty()) {
            return _connections.getFirst().send_(out, pri);
        } else {
            return queueUntilConnected_(new PendingPacket(out, pri));
        }
    }

    private ListenableFuture<IOutgoingStream> sendStream_(final NewOutgoingStream out, Prio pri)
    {
        assert !_connections.isEmpty();

        ListenableFuture<Void> sendFuture = _connections.getFirst().send_(out, pri);

        addCallback(sendFuture, new FailedFutureCallback()
        {
            @Override
            public void onFailure(Throwable t)
            {
                out.getStreamCreationFuture_().setException(t);
            }
        }, _executor);

        return out.getStreamCreationFuture_();
    }

    ListenableFuture<IOutgoingStream> beginStream_(StreamID id, SID sid, Prio pri)
    {
        assertIsAlive();

        NewOutgoingStream out = new NewOutgoingStream(id, sid);

        if (!_connections.isEmpty()) {
            return sendStream_(out, pri);
        } else {
            return queueUntilConnected_(new PendingStream(out, pri));
        }
    }

    ListenableFuture<Void> pulse_(Prio pri)
    {
        assertIsAlive();

        StartPulseMessage message = new StartPulseMessage();

        final ListenableFuture<Void> requestFuture;
        if (!_connections.isEmpty()) {
            requestFuture = _connections.getFirst().send_(message, pri);
        } else {
            requestFuture = queueUntilConnected_(new PendingPulse(message, pri));
        }

        // We only want to fail the pulse future when the start pulse message
        // fails. If the start pulse message succeeds, it doesn't mean the
        // pulse also succeeded
        message.getPulseFuture().chainException(requestFuture);

        return message.getPulseFuture();
    }

    private <FutureType, SendType> ListenableFuture<FutureType> queueUntilConnected_(
            Pending<FutureType, SendType> pending)
    {
        assertIsAlive();
        assert _connections.isEmpty();

        if (_pendingConnection == null) {
            OutArg<Prio> op = new OutArg<Prio>(LO);
            assert _pending.tryDequeue(op) == null;

            connect_();
        }

        try {
            l.info("enqueueing outgoing packet");
            _pending.enqueueThrows(pending, pending.getPri_());
        } catch (ExNoResource e) {
            l.error("too many pending packets while waiting to connect");
            pending.getFuture_().setException(new ExTransport("operation failed"));
        }

        return pending.getFuture_();
    }

    void onIncomingConnection_(IUnicastConnection unicast)
    {
        l.info("Incoming connection for " + _did);
        PeerConnection newConnection = _connectionFactory.createConnection_(this, unicast);
        addReadyConnection_(newConnection);
    }

    /**
     * Adds a connection to the connection list and attaches the necessary callbacks to have it
     * clean itself up. Also starts the receive loop for the connection. If this connection was the
     * first to enter the list, then send any queued packets
     *
     * @param connection The connection to add to the connection list
     */
    private void addReadyConnection_(final PeerConnection connection)
    {
        assert connection != null;

        // Insert the connection into the connection list
        _connections.add(connection);
        assert _connections.size() <= MAX_NUM_CONNECTIONS;

        // Add the callback that removes this connection on disconnection
        addCallback(connection.getCloseFuture_(), new FailedFutureCallback()
        {
            @Override
            public void onFailure(Throwable throwable)
            {
                // Remove this connection from the array
                _connections.remove(connection);
            }
        }, _executor);

        // Start the receive loop
        connection.startReceiveLoop_();

        // Check if this connection just became the primary connection
        if (_connections.getFirst() == connection) {
            // This means this connection is the first to be writable, so send pending requests
            sendPendingRequests_();
        }
    }

    /**
     * Creates a new connection and starts the connect process. When this connection succeeds
     * connecting, it will add itself to the connection list
     */
    private void connect_()
    {
        // no need to connect if primary connection exists
        assert _connections.isEmpty();
        assert _pendingConnection == null;

        final PeerConnection newConnection = _connectionFactory.createConnection_(this);
        _pendingConnection = newConnection;
        addCallback(newConnection.connect_(), new FutureCallback<Void>()
        {
            @Override
            public void onSuccess(Void v)
            {
                l.info("connected");

                if (_pendingConnection != newConnection || !_alive) {
                    // We timed-out or the peer was destroyed. Do nothing here because
                    // we're effectively gone from the system already
                    l.info("Dropping connection");
                    return;
                }

                handleConnectSucceeded_();
            }

            @Override
            public void onFailure(Throwable t)
            {
                l.warn("connect failed err:" + t);
                if (_pendingConnection != newConnection || !_alive) {
                    // We timed-out or the peer was destroyed. Do nothing here because
                    // we're effectively gone from the system already
                    return;
                }

                handleConnectFailed_(t);
            }
        }, _executor);

        scheduleConnectTimeout_(newConnection);
    }

    private void scheduleConnectTimeout_(final PeerConnection connection)
    {
        assert connection != null;

        _executor.executeAfterDelay(new Runnable()
        {
            @Override
            public void run()
            {
                if (!Peer.this._alive) {
                    l.warn("peer not alive");
                    return;
                } else if (connection != Peer.this._pendingConnection) {
                    l.info("timeout has been invalidated");
                    return;
                }

                Exception cause = new ExTransport("Timed out");
                handleConnectFailed_(cause);
                connection.disconnect_(cause);
            }
        }, CONNECT_TIMEOUT);
    }

    private void sendPendingRequests_()
    {
        assert !_connections.isEmpty();

        OutArg<Prio> op = new OutArg<Prio>(LO);
        Pending<?, ?> pendingOperation;

        List<Pending<?, ?>> succeededOperations = new LinkedList<Pending<?, ?>>();
        while ((pendingOperation = _pending.tryDequeue(op)) != null) {
            succeededOperations.add(pendingOperation);
            l.info("send queued");
        }

        // We call onConnectSucceeded here instead of while dequeuing from the
        // blocking queue so that any code inside onConnectSucceeded that enqueues
        // on _pending doesn't cause an infinite loop
        for (Pending<?, ?> succeededOperation : succeededOperations) {
            succeededOperation.onConnectSucceeded_(_connections.getFirst());
        }
    }

    private void handleConnectSucceeded_()
    {
        assert _pendingConnection != null;

        final PeerConnection connection = _pendingConnection;
        _pendingConnection = null;

        addReadyConnection_(connection);
    }

    private void handleConnectFailed_(Throwable cause)
    {
        assert _pendingConnection != null;

        _pendingConnection = null;
        failPendingRequests_(cause);
    }

    private void failPendingRequests_(Throwable cause)
    {
        OutArg<Prio> op = new OutArg<Prio>(LO);
        Pending<?, ?> pendingOperation;

        List<Pending<?, ?>> failedOperations = new LinkedList<Pending<?, ?>>();
        while ((pendingOperation = _pending.tryDequeue(op)) != null) {
            failedOperations.add(pendingOperation);
            l.warn("drop queued");
        }

        // We call onConnectFailed here instead of while dequeuing from the
        // blocking queue so that any code inside onConnectFailed that enqueues
        // on _pending doesn't cause an infinite loop
        for (Pending<?, ?> failedOperation : failedOperations) {
            failedOperation.onConnectFailed_(cause);
        }
    }

    @Override
    public DID getDID_()
    {
        return _did;
    }

    @Override
    public IStateContainer getPeerStateContainer_()
    {
        return _peerState;
    }

    //
    // helper classes for the connect process
    //

    private abstract class Pending<FutureType, SendType>
    {
        protected final UncancellableFuture<FutureType> _returned;
        protected final SendType _out;
        protected final Prio _pri;

        protected Pending(UncancellableFuture<FutureType> returned, SendType out, Prio pri)
        {
            this._returned = returned;
            this._out = out;
            this._pri = pri;
        }

        abstract void onConnectSucceeded_(IConnection connection);

        abstract void onConnectFailed_(Throwable cause);

        UncancellableFuture<FutureType> getFuture_()
        {
            return _returned;
        }

        public SendType getOut_()
        {
            return _out;
        }

        Prio getPri_()
        {
            return _pri;
        }
    }

    private class PendingPacket extends Pending<Void, OutgoingUnicastPacket>
    {
        PendingPacket(OutgoingUnicastPacket out, Prio pri)
        {
            super(UncancellableFuture.<Void>create(), out, pri);
        }

        @Override
        void onConnectSucceeded_(IConnection connection)
        {
            _returned.chain(connection.send_(getOut_(), getPri_()));
        }

        @Override
        void onConnectFailed_(Throwable cause)
        {
            _returned.setException(cause);
        }
    }

    private class PendingStream extends Pending<IOutgoingStream, NewOutgoingStream>
    {
        public PendingStream(NewOutgoingStream out, Prio pri)
        {
            super(out.getStreamCreationFuture_(), out, pri);
        }

        @Override
        void onConnectSucceeded_(IConnection connection)
        {
            sendStream_(getOut_(), getPri_());
        }

        @Override
        void onConnectFailed_(Throwable cause)
        {
            _returned.setException(cause);
        }
    }

    private class PendingPulse extends Pending<Void, StartPulseMessage>
    {
        private PendingPulse(StartPulseMessage out, Prio pri)
        {
            super(UncancellableFuture.<Void>create(), out, pri);
        }

        @Override
        void onConnectSucceeded_(IConnection connection)
        {
            connection.send_(getOut_(), getPri_());
        }

        @Override
        void onConnectFailed_(Throwable cause)
        {
            // A pulse must happen, so re-enqueue the pulse for the next connection
            if (_alive) {
                queueUntilConnected_(this);
            } else {
                getOut_().getPulseFuture().setException(cause);
            }
        }
    }
}
