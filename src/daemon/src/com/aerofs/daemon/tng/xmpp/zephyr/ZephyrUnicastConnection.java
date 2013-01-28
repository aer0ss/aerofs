/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr;

import com.aerofs.base.id.DID;
import com.aerofs.base.net.ZephyrConstants;
import com.aerofs.daemon.core.net.link.ILinkStateListener;
import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.base.IUnicastConnection;
import com.aerofs.daemon.tng.base.WireData;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.daemon.tng.xmpp.ISignallingClient;
import com.aerofs.daemon.tng.xmpp.ISignallingService;
import com.aerofs.daemon.tng.xmpp.ISignallingService.SignallingMessage;
import com.aerofs.daemon.tng.xmpp.zephyr.handler.ZephyrFrameDecoder;
import com.aerofs.daemon.tng.xmpp.zephyr.handler.ZephyrFrameEncoder;
import com.aerofs.daemon.tng.xmpp.zephyr.handler.ZephyrProtocolHandler;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrBindRequest;
import com.aerofs.lib.Util;
import com.aerofs.base.async.FailedFutureCallback;
import com.aerofs.base.async.FutureUtil;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.aerofs.proto.Transport.PBZephyrCandidateInfo;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class ZephyrUnicastConnection
        implements IUnicastConnection, ISignallingClient, ILinkStateListener,
        IZephyrUnicastEventSink
{
    private static final Logger l = Util.l(ZephyrUnicastConnection.class);

    private final ISingleThreadedPrioritizedExecutor _executor;

    private final DID _localDID;
    private final DID _remoteDID;
    private final InetSocketAddress _zephyrAddress;
    private final ISignallingService _signallingService;
    private final ChannelFactory _channelFactory;
    private final ChannelPipeline _pipeline;

    private final AtomicBoolean _connectionStarted = new AtomicBoolean(false);
    private final UncancellableFuture<Void> _connectFuture = UncancellableFuture.create();
    private final UncancellableFuture<Void> _closeFuture = UncancellableFuture.createCloseFuture();

    private Channel _channel = null;

    private UncancellableFuture<ImmutableList<WireData>> _pendingReceive = null;
    private final List<WireData> _incomingData = new LinkedList<WireData>();

    private int _localZid = ZephyrConstants.ZEPHYR_INVALID_CHAN_ID;
    private int _remoteZid = ZephyrConstants.ZEPHYR_INVALID_CHAN_ID;

    public static ZephyrUnicastConnection getInstance_(ISingleThreadedPrioritizedExecutor executor,
            DID localDID, DID remoteDID, InetSocketAddress zephyrAddress,
            ChannelFactory channelFactory, ChannelPipeline pipeline,
            final ILinkStateService networkLinkStateService, ISignallingService service)
    {
        final ZephyrUnicastConnection connection = new ZephyrUnicastConnection(executor, localDID,
                remoteDID, zephyrAddress, channelFactory, pipeline, service);

        // Add the correct encoders and decoders to convert logical messages
        // to ChanneBuffers
        pipeline.addLast("encoder", new ZephyrFrameEncoder());
        pipeline.addLast("decoder", new ZephyrFrameDecoder());

        // Add the protocol handler
        pipeline.addLast("zephyr", new ZephyrProtocolHandler(connection, connection._closeFuture));

        // handle the link-state-event changes
        networkLinkStateService.addListener_(connection, executor);

        connection.getCloseFuture_().addListener(new Runnable()
        {
            @Override
            public void run()
            {
                networkLinkStateService.removeListener_(connection);
            }
        }, executor);

        return connection;
    }

    private ZephyrUnicastConnection(ISingleThreadedPrioritizedExecutor executor, DID localDID,
            DID remoteDID, InetSocketAddress zephyrAddress, ChannelFactory channelFactory,
            ChannelPipeline pipeline, ISignallingService service)
    {
        _executor = executor;

        _localDID = localDID;
        _remoteDID = remoteDID;
        _zephyrAddress = zephyrAddress;
        _signallingService = service;
        _channelFactory = channelFactory;
        _pipeline = pipeline;

        // When the connection closes, be sure to tell anyone listening
        FutureUtil.addCallback(_closeFuture, new FailedFutureCallback()
        {
            @Override
            public void onFailure(Throwable t)
            {
                l.warn(ZephyrUnicastConnection.this + " disconnected: " + t);

                // Close a pending receive request
                if (_pendingReceive != null) {
                    _pendingReceive.setException(t);
                    _pendingReceive = null;
                }

                // This will do nothing if the _connectFuture already succeeded
                _connectFuture.setException(t);

                // Deregister this client
                _signallingService.deregisterSignallingClient_(ZephyrUnicastConnection.this);
            }

        }, _executor);
    }

    /**
     * Initiates a connection to the remote peer, returning a {@link ListenableFuture} which will
     * hold the result of the connection upon completion or failure.
     */
    @Override
    public ListenableFuture<Void> connect_()
    {
        l.info("Starting to connect to " + _remoteDID);

        boolean started = _connectionStarted.getAndSet(true);
        assert !started;

        _executor.execute(new Runnable()
        {

            @Override
            public void run()
            {
                connectImpl_();
            }

        });
        return _connectFuture;
    }

    /**
     * Same as {@link com.aerofs.daemon.tng.xmpp.zephyr.ZephyrUnicastConnection#connect_()} but with
     * added knowledge of the remote peer.
     *
     * @param info The Zephyr Protobuf message that holds information about the remote peer we're
     * trying to connect to
     */
    public ListenableFuture<Void> connect_(PBZephyrCandidateInfo info)
    {
        assert info.hasSourceZephyrId();

        _remoteZid = info.getSourceZephyrId();

        return connect_();
    }

    private void connectImpl_()
    {
        // Create the Channel
        _channel = _channelFactory.newChannel(_pipeline);

        // Connect the Channel to Zephyr
        final ChannelFuture future = _channel.connect(_zephyrAddress);

        // Attach a listener to the ChannelFuture that will set this connection
        // as succeeded when it is bound with a peer over Zephyr
        // This future will execute on the given executor
        future.addListener(new ExecutorChannelFutureListener(_executor)
        {

            @Override
            public void operationCompleteImpl(ChannelFuture future)
            {
                if (future.isSuccess()) {
                    // Notify listeners of a successful connection
                    _connectFuture.set(null);
                }

                // If the connection attempt fails, the closeFuture will get
                // set and trigger the _connectFuture
            }

        });
    }

    @Override
    public ListenableFuture<Void> send_(final byte[][] bss, Prio pri)
    {
        assert _connectionStarted.get();

        final UncancellableFuture<Void> sendFuture = UncancellableFuture.create();
        _executor.execute(new Runnable()
        {

            @Override
            public void run()
            {
                if (!_connectFuture.isDone()) {
                    // If the connection has not been attempted, set the ExNotConnected exception
                    sendFuture.setException(
                            new IllegalStateException("send_() called after connection closed"));
                    return;
                }

                if (_closeFuture.isDone()) {
                    // If the connection has closed, return the same exception
                    sendFuture.chain(_closeFuture);
                    return;
                }

                ChannelFuture future = _channel.write(ChannelBuffers.copiedBuffer(bss));
                future.addListener(new ChannelFutureListenerAdapter(sendFuture));
            }

        }, pri);

        return sendFuture;
    }

    @Override
    public ListenableFuture<ImmutableList<WireData>> receive_()
    {
        assert _connectionStarted.get();

        final UncancellableFuture<ImmutableList<WireData>> recvFuture = UncancellableFuture.create();
        _executor.execute(new Runnable()
        {

            @Override
            public void run()
            {
                if (!_connectFuture.isDone()) {
                    // If the connection has not been attempted, set an exception
                    recvFuture.setException(
                            new IllegalStateException("receive_() called after connection closed"));
                    return;
                }

                if (_closeFuture.isDone()) {
                    // If the connection has closed, return the same exception
                    FutureUtil.addCallback(_closeFuture, new FailedFutureCallback()
                    {
                        @Override
                        public void onFailure(Throwable throwable)
                        {
                            recvFuture.setException(throwable);
                        }

                    }, _executor);
                    return;
                }

                if (_pendingReceive != null) {
                    // There is already a pending receive occurring, so just chain the result
                    // of the existing future with this new future
                    recvFuture.chain(_pendingReceive);
                    return;
                }

                // Update the pendingReceive request's future to the new future
                _pendingReceive = recvFuture;

                // Allow us to read from the channel
                _channel.setReadable(true);
            }

        });

        return recvFuture;
    }

    @Override
    public ListenableFuture<Void> disconnect_(final Exception ex)
    {
        l.info("Disconnect requested (" + ex + "): " + this);

        assert _connectionStarted.get();
        assert ex != null;

        _executor.execute(new Runnable()
        {

            @Override
            public void run()
            {
                // Close the channel by throwing an exception
                Channels.fireExceptionCaught(_channel, ex);
            }

        });

        // If the connection is already disconnected, then this future will
        // already be completed
        return _closeFuture;
    }

    private void sendZephyrKnowledgeMessage_()
    {
        assert _localZid != ZephyrConstants.ZEPHYR_INVALID_CHAN_ID;

        // Construct the Zephyr message
        PBZephyrCandidateInfo.Builder zephyrMessageBuilder = PBZephyrCandidateInfo.newBuilder();
        zephyrMessageBuilder.setSourceZephyrId(_localZid);

        if (_remoteZid != ZephyrConstants.ZEPHYR_INVALID_CHAN_ID) {
            zephyrMessageBuilder.setDestinationZephyrId(_remoteZid);
        }

        // Construct the full message
        PBTPHeader message = PBTPHeader.newBuilder()
                .setType(Type.ZEPHYR_CANDIDATE_INFO)
                .setZephyrInfo(zephyrMessageBuilder)
                .build();

        // Send the message
        final ListenableFuture<Void> signalFuture = _signallingService.sendSignallingMessage_(
                new SignallingMessage(_remoteDID, message));

        // Add a failure callback to be executed on the same executor as all
        // calls of this IUnicastConnection
        FutureUtil.addCallback(signalFuture, new FailedFutureCallback()
        {
            @Override
            public void onFailure(Throwable t)
            {
                // On failure sending the zid, fail the connection
                Channels.fireExceptionCaught(_channel, t);
            }

        }, _executor);
    }

    /*
     * This method may be called before _connectFuture is completed or even
     * assigned. This is because this method is part of the connecting
     * process and so it must be called before _connectFuture can be set
     */
    @Override
    public void onChannelRegisteredWithZephyr_(final int zid)
    {
        _executor.execute(new Runnable()
        {

            @Override
            public void run()
            {
                assert zid >= 0 && zid != ZephyrConstants.ZEPHYR_INVALID_CHAN_ID;

                // Store our local ZID
                _localZid = zid;

                // Register our predicate with this
                _signallingService.registerSignallingClient_(ZephyrUnicastConnection.this,
                        new SignallingMessagePredicate(_remoteDID, _localZid));

                // Send our knowledge of the system to the remote peer
                sendZephyrKnowledgeMessage_();
            }

        });
    }

    private static WireData createWireData(ChannelBuffer buffer)
    {
        int len = buffer.readableBytes();
        byte[] data = new byte[len];
        buffer.readBytes(data);

        // Wirelen must represent the entire payload with headers
        return new WireData(data, len + ZephyrConstants.ZEPHYR_CLIENT_HDR_LEN);
    }

    @Override
    public void onDataReceivedFromChannel_(ChannelBuffer buffer)
    {
        final WireData data = createWireData(buffer);
        _executor.execute(new Runnable()
        {

            @Override
            public void run()
            {
                assertConnected();

                // We should not be getting data if no one is
                // asking for it
                if (_pendingReceive == null) {
                    l.warn("Incoming data but no one is requesting");
                }

                // Add the received data to the incoming buffer
                _incomingData.add(data);

                // Turn off reading from the channel. Once this operation completes,
                // send the buffered data to the pendingReceive future
                ChannelFuture readableFuture = _channel.setReadable(false);
                readableFuture.addListener(new ExecutorChannelFutureListener(_executor)
                {

                    @Override
                    public void operationCompleteImpl(ChannelFuture future)
                    {
                        if (future.isSuccess() && _pendingReceive != null) {
                            _pendingReceive.set(ImmutableList.copyOf(_incomingData));
                            _incomingData.clear();
                            _pendingReceive = null;
                        }
                    }
                });
            }

        });
    }

    @Override
    public void onLinkStateChanged_(ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed, ImmutableSet<NetworkInterface> current,
            ImmutableSet<NetworkInterface> previous)
    {
        for (NetworkInterface nif : current) {
            Enumeration<InetAddress> addresses = nif.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address.equals(_zephyrAddress.getAddress())) {
                    return;
                }
            }
        }

        disconnect_(new ExTransport("LinkStateChanged"));
    }

    /**
     * Answers whether the connection is currently connected
     *
     * @return true if connected, false if not
     */
    private boolean isConnected()
    {
        return _connectFuture.isDone() && !_closeFuture.isDone();
    }

    private void assertConnected()
    {
        assert isConnected();
    }

    @Override
    public ListenableFuture<Void> getCloseFuture_()
    {
        return _closeFuture;
    }

    @Override
    public String toString()
    {
        return "zephyr[" + _localDID + " (" + _localZid + ") -> " + _remoteDID + " (" + _remoteZid +
                ")]";
    }

    @Override
    public void signallingChannelConnected_()
    {
        // Noop
    }

    @Override
    public void signallingChannelDisconnected_()
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                if (!_connectFuture.isDone()) {
                    disconnect_(new ExTransport("signalling channel disconnected"));
                }
            }
        });
    }

    @Override
    public void processSignallingMessage_(SignallingMessage message)
    {
        assert message.message.getType() == Type.ZEPHYR_CANDIDATE_INFO;
        assert message.message.hasZephyrInfo();
        assert message.message.getZephyrInfo().hasSourceZephyrId();

        final PBZephyrCandidateInfo remoteInfo = message.message.getZephyrInfo();
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                boolean stable = true;
                boolean signal = false;

                String messageStr = "Received message: LOCAL_ZID=";
                if (remoteInfo.hasDestinationZephyrId()) {
                    messageStr += remoteInfo.getDestinationZephyrId();
                } else {
                    messageStr += -1;
                }
                messageStr += ", REMOTE_ZID=" + remoteInfo.getSourceZephyrId();

                l.info(messageStr);
                l.info("Current state: LOCAL_ZID=" + _localZid + ", REMOTE_ZID=" + _remoteZid);

                if (remoteInfo.getSourceZephyrId() != _remoteZid) {
                    // Our knowledge of the remote peer has changed, so we need to signal
                    // the peer with what we think the zid's are
                    signal = true;
                    _remoteZid = remoteInfo.getSourceZephyrId();
                }

                if (_localZid == ZephyrConstants.ZEPHYR_INVALID_CHAN_ID) {
                    // If we haven't registered with the relay, then there is no need
                    // to check the destination zephyr id since the peer can't possibly know it
                    // We return here because we have no _localZid to send back yet
                    return;
                }

                if (!remoteInfo.hasDestinationZephyrId()) {
                    // Because this message has only one id set, the remote peer has
                    // no knowledge of our zid
                    stable = false;
                    signal = true;
                } else if (remoteInfo.getDestinationZephyrId() != _localZid) {
                    // The remote's knowledge of us is incorrect
                    stable = false;
                    signal = true;
                }

                if (signal) {
                    // Send out a message with what this peer thinks the zid's are
                    sendZephyrKnowledgeMessage_();
                }

                if (stable) {
                    assert _channel != null;
                    assert _localZid != ZephyrConstants.ZEPHYR_INVALID_CHAN_ID;
                    assert _remoteZid != ZephyrConstants.ZEPHYR_INVALID_CHAN_ID;

                    // The zid's have stablizied and each remote peer knows which zid to bind to,
                    // so we can finish the connection process by binding
                    _channel.write(new ZephyrBindRequest(_remoteZid));
                }
            }
        });
    }

    private static final class SignallingMessagePredicate implements Predicate<SignallingMessage>
    {
        private final DID _remoteDID;
        private final int _localZid;

        public SignallingMessagePredicate(DID remoteDID, int localZid)
        {
            _remoteDID = remoteDID;
            _localZid = localZid;
        }

        @Override
        public boolean apply(@Nullable SignallingMessage signallingMessage)
        {
            // Once the local zid is set to something other than the ZEPHYR_INVALID_CHAN_ID,
            // it will never be changed.
            assert _localZid != ZephyrConstants.ZEPHYR_INVALID_CHAN_ID;

            if (!signallingMessage.did.equals(_remoteDID)) {
                l.info("predicate(" + _localZid + ") not my remote DID");
                return false;
            }

            if (signallingMessage.message.getType() != Type.ZEPHYR_CANDIDATE_INFO) {
                return false;
            }

            if (!signallingMessage.message.hasZephyrInfo()) {
                return false;
            }

            PBZephyrCandidateInfo info = signallingMessage.message.getZephyrInfo();
            if (!info.hasDestinationZephyrId()) {
                l.info("predicate(" + _localZid + ") no destination");
                return false;
            }

            if (info.getDestinationZephyrId() != _localZid) {
                l.info("predicate(" + _localZid + ") destination " + info.getDestinationZephyrId() +
                        " not mine");
                return false;
            }

            l.info("predicate(" + _localZid + ") accepting");
            return true;
        }
    }

    /**
     * Adapter that sets a SettableFuture when a ChannelFutureListener is invoked
     */
    private class ChannelFutureListenerAdapter implements ChannelFutureListener
    {
        private final UncancellableFuture<Void> _future;

        public ChannelFutureListenerAdapter(UncancellableFuture<Void> f)
        {
            assert f != null;
            _future = f;
        }

        @Override
        public void operationComplete(ChannelFuture future)
                throws Exception
        {
            if (future.isSuccess()) {
                _future.set(null);
            } else {
                _future.setException(future.getCause());
            }
        }
    }

    private abstract class ExecutorChannelFutureListener implements ChannelFutureListener
    {
        private final Executor _executor;

        public ExecutorChannelFutureListener(Executor executor)
        {
            _executor = executor;
        }

        @Override
        final public void operationComplete(final ChannelFuture future)
                throws Exception
        {
            _executor.execute(new Runnable()
            {

                @Override
                public void run()
                {
                    operationCompleteImpl(future);
                }

            });
        }

        public abstract void operationCompleteImpl(ChannelFuture future);
    }

}
