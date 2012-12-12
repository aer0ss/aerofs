package com.aerofs.daemon.transport.xmpp.zephyr.client.netty;

import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.tng.xmpp.zephyr.Constants;
import com.aerofs.daemon.transport.lib.IIdentifier;
import com.aerofs.daemon.transport.lib.INetworkStats;
import com.aerofs.daemon.transport.lib.IPipeController;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.xmpp.ISignalledPipe;
import com.aerofs.daemon.transport.xmpp.ISignallingChannel;
import com.aerofs.daemon.transport.xmpp.zephyr.client.netty.exception.ExInvalidZephyrChannelAction;
import com.aerofs.daemon.transport.xmpp.zephyr.client.netty.exception.ExZephyrAlreadyBound;
import com.aerofs.daemon.transport.xmpp.zephyr.client.netty.exception.ExZephyrChannelNotBound;
import com.aerofs.daemon.transport.xmpp.zephyr.client.netty.handler.ZephyrClientPipelineFactory;
import com.aerofs.daemon.transport.xmpp.zephyr.client.netty.message.ZephyrBindRequest;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.id.DID;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.proto.Transport;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.aerofs.proto.Transport.PBZephyrCandidateInfo;
import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.SocketAddress;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents connections to a Zephyr relay server, implemented using
 * an event-driven system. Calls to {@link #connect_(DID)}, {@link #disconnect_(DID, Exception)},
 * {@link #linkStateChanged_(Set, Set)}, {@link #onChannelConnected_(Channel)}, {@link #onChannelDisconnected_(Channel)},
 * {@link #onChannelException_(Channel, Exception)}, {@link #onMessageReceivedFromChannel_(Channel, ChannelBuffer)},
 * {@link #onChannelRegisteredWithZephyr_(Channel, int)}, {@link #processSignallingMessage_(DID, PBTPHeader)},
 * {@link #sendSignallingMessageFailed_(DID, PBTPHeader, Exception)}, {@link #signallingChannelConnected_()}
 * {@link #signallingChannelDisconnected_()} do not block. The results of these calls
 * are surfaced to the owner of this pipe, an {@link IPipeController} and the
 * signalling channel, {@link ISignallingChannel} asynchronously.
 * <p>
 * Calls to debug methods like {@link #dumpStat(PBDumpStat, com.aerofs.proto.Files.PBDumpStat.Builder)} and
 * {@link #getBytesRx(DID)} block, and so must not be called on the same thread as
 * this {@link ZephyrClientPipe} executes its event-loop.
 */
public class ZephyrClientPipe extends AbstractEventLoop<IZephyrEvent>
        implements ISignalledPipe, IZephyrIOEventSink
{
    private static Logger l = com.aerofs.lib.Util.l(ZephyrClientPipe.class);

    private final IIdentifier _id;
    private final DID _localDID;
    private final SocketAddress _zephyrAddress;
    private final ClientBootstrap _bootstrap;

    private final IPipeController _controller;
    private final ISignallingChannel _signallingChannel;
    private final INetworkStats _networkStats;

    private final AtomicBoolean _isRunning;

    private final ChannelGroup _channelGroup;

    // Maintains a mapping from Channels to ZephyrClientContexts.
    // FIXME: Next release of Netty will support attachments on Channels, so this
    // mapping will not be necessary.
    private final Map<Channel, ZephyrClientContext> _channelToContext;

    // Maintains a mapping from DIDs to Channels.
    private final Map<DID, Channel> _didToChannel;

    /**
     * Constructs a {@link ZephyrClientPipe} given a {@link ChannelFactory}. All connections
     * to the Zephyr relay server will be made using channels constructed by
     * the given {@link ChannelFactory}.
     * <p>
     * The {@link ZephyrClientPipe} is responsible for receiving requests to perform
     * I/O over Zephyr and carrying out those requests, along with receiving
     * payloads and responses from remote Zephyr clients.
     * @param id The {@link IIdentifier} that identifies this {@link IPipe}
     * @param localDID The DID of this local Peer
     * @param controller The {@link IPipeController} to which this class communicates
     * @param signallingChannel The {@link ISignallingChannel} to which zids are sent and received
     * @param networkStats The {@link INetworkStats} object in which network read and write counts are recorded
     * @param channelFactory The {@link ChannelFactory} to use when constructing channels.
     * @param zephyrAddress The socket address of the Zephyr relay server
     * @param proxy The {@link Proxy} through which to connect to the Zephyr relay
     */
    public ZephyrClientPipe(IIdentifier id,
            DID localDID,
            IPipeController controller,
            ISignallingChannel signallingChannel,
            INetworkStats networkStats,
            ChannelFactory channelFactory,
            SocketAddress zephyrAddress,
            Proxy proxy)
    {
        super(DaemonParam.QUEUE_LENGTH_DEFAULT);

        assert id != null : ("IIdentifier is null");
        assert localDID != null : ("Local DID is null");
        assert controller != null : ("IPipeController is null");
        assert signallingChannel != null : ("ISignallingChannel is null");
        assert networkStats != null : ("INetworkStats is null");
        assert channelFactory != null : ("ChannelFactory is null");
        assert zephyrAddress != null : ("Zephyr address is null");
        assert proxy != null : ("No proxy defined, direct or not");

        _id = id;
        _localDID = localDID;

        _zephyrAddress = zephyrAddress;
        _bootstrap = new ClientBootstrap(channelFactory);
        _bootstrap.setPipelineFactory(
                new ZephyrClientPipelineFactory(this, networkStats, proxy));

        _controller = controller;
        _signallingChannel = signallingChannel;
        _networkStats = networkStats;

        _isRunning = new AtomicBoolean(false);

        _channelToContext = new HashMap<Channel, ZephyrClientContext>();
        _didToChannel = new HashMap<DID, Channel>();

        _channelGroup = new DefaultChannelGroup();
    }

    /**
     * Creates all necessary mappings and contexts for a channel when
     * connecting
     * @param did The DID to associate the channel with
     * @param channel The channel connecting to the remote DID
     */
    private void createContextEntry_(DID did, Channel channel)
    {
        assert !_didToChannel.containsKey(did)
                && !_channelToContext.containsKey(channel)
                        : ("DID or Channel already exists");

        assert !_channelGroup.contains(channel) : ("Channel already exists");

        // Create the ZephyrClientContext object for this connection
        final ZephyrClientContext ctx = new ZephyrClientContext(
                _localDID, did, channel);

        // Add mapping entries
        _channelToContext.put(channel, ctx);
        _didToChannel.put(did, channel);

        // Add channel to group for easy shutdown
        _channelGroup.add(channel);
    }

    /**
     * Removes all links between this DID and any contexts/channels
     * <p>
     * both onChannelException and onChannelDisconnected
     * @param did The DID to purge
     */
    private void cleanupContextEntry_(DID did)
    {
        final ZephyrClientContext ctx = contextFromDID(did);

        // The context may be null if we first get an exception event that
        // closes the channel, followed by a proper channelDisconnected event.
        // The first event deletes the context, so the second must gracefully
        // noop.
        if (ctx != null) {
            _didToChannel.remove(did);
            _channelToContext.remove(ctx.getChannel());
        }
    }

    private ZephyrClientContext contextFromDID(DID did)
    {
        assertEventThread();

        Channel channel = _didToChannel.get(did);
        if (channel != null) {
            ZephyrClientContext ctx = _channelToContext.get(channel);
            assert ctx != null : ("DID -> Channel exists, but no Channel -> ZCC");
            return ctx;
        }

        return null;
    }

    @Override
    public void init_() throws Exception
    {
        // Register for signalling channel events, such as when a remote peer
        // sends us a zid to connect to, or when the signalling channel goes down
        _signallingChannel.registerSignallingClient_(
                Type.ZEPHYR_CANDIDATE_INFO, this);
    }

    /**
     * This method creates a new thread and starts running the event loop
     * for the ZephyrClientManager
     */
    @Override
    public void start_()
    {
        new Thread(this).start();
    }

    /**
     * Signals the event thread to shutdown. This call does not block
     */
    public void shutdown_()
    {
        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_() throws Exception
            {
                _isRunning.set(false);
                logInfo("Received shutdown request");
            }

        }, Prio.LO);
    }

    @Override
    public boolean ready()
    {
        return _isRunning.get();
    }

    @Override
    public void connect_(final DID did)
    {
        assertNotEventThread();

        enqueueEvent(new IZephyrEvent()
        {
            @Override
            public void handle_() throws Exception
            {
                connectImpl_(did);
            }

        }, Prio.LO);
    }

    /**
     * Enqueues a connection request to the remote peer represented by did.
     * If it is known what zid to bind to, remoteZid represents that zid value
     * @param did The DID to connect to, via the Zephyr relay
     */
    private void connectImpl_(DID did)
    {
        assertEventThread();

        final ZephyrClientContext ctx = contextFromDID(did);
        if (ctx != null) {
            // The connection exists

            if (ctx.isBound_()) {
                // The connection should be disconnected before connecting to
                // a new remote zid
                logWarn(ctx.getChannel(), "Connection already exists " + ctx);
                return;
            } else if (ctx.isRegistered_()) {
                // Since the local zid is broadcasted after registration,
                // it needs to be retransmitted now that the connection is
                // being attempted again
                logWarn(ctx.getChannel(), "Channel registered but not connected " + ctx);
                sendZidToPeer_(ctx);
                return;
            } else {
                // The connection may have timed-out, so kill/clean up and
                // drop into connecting
                disconnectImpl_(did, new Exception("Timeout"), false);
            }
        }

        // Initiate the connection
        ChannelFuture future = _bootstrap.connect(_zephyrAddress);

        // Create a ZephyrClientContext and map the DID and channel to it
        createContextEntry_(did, future.getChannel());
    }

    @Override
    public void disconnect_(final DID did, final Exception ex)
    {
        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_()
            {
                disconnectImpl_(did, ex, true);
            }

        }, Prio.LO);
    }

    /**
     * Disconnects all current connections cleanly, allowing them to
     * notify the IPipeController of the disconnection
     * @param ex The exception to propagate to all clients sending to the channel
     */
    private void disconnectAll_(final Exception ex)
    {
        assertEventThread();

        for (ZephyrClientContext ctx : _channelToContext.values()) {
            disconnectImpl_(ctx.getRemoteDID_(), ex, true);
        }
    }

    /**
     * The actual implementation of disconnecting a channel. This is run
     * on the event thread
     * <p>
     * FIXME: Exception passed in is actually ignored. Netty will throw its
     * own exception
     * <p>
     * FIXME: Investigate the issue of this method being called from
     * @param did The DID representing the channel to disconnect
     * @param ex The exception to propagate to anyone doing I/O on the channel
     * @param notifyController True if the IPipeController should be notified
     */
    private void disconnectImpl_(DID did, Exception ex, boolean notifyController)
    {
        assertEventThread();

        final ZephyrClientContext ctx = contextFromDID(did);
        if (ctx == null) {
            logWarn("No channel to " + did + "exists: " + ex);
            return;
        }

        // Since a context for this DID exists, assume the connection
        // exists as well and prepare to terminate it
        ChannelFuture future = ctx.getChannel().close();

        logInfo(ctx.getChannel(), ctx + " disconnecting from relay");

        future.addListener(new ChannelFutureListener()
        {

            @Override
            public void operationComplete(ChannelFuture future)
                throws Exception
            {
                // multiple channels to the same DID, use sequence numbers
                logInfo(future.getChannel(), "Disconnected");
            }

        });

        cleanupContextEntry_(did);

        if (notifyController) {
            // Notify the IPipeController of a disconnection
            // Sometimes we do not want to notify the controller, like in the
            // case of multiple connect_() calls due to timeouts
            _controller.peerDisconnected(did, this);
        }
    }

    /**
     * Sends a bind request to the Zephyr relay server.
     * If the connection to the remote DID is not registered or established yet,
     * the bind request will be executed once registration is complete
     * @param did The DID to bind the remote ZID with
     * @param remoteZid The ZID of the connection
     */
    private void bindWithZephyr_(DID did, int remoteZid)
            throws ExZephyrAlreadyBound
    {
        assertEventThread();

        final ZephyrClientContext ctx = contextFromDID(did);

        assert ctx != null : ("No channel to " + did + " exists");

        if (ctx.isRegistered_()) {
            // Since the channel is registered with Zephyr, binding can
            // happen now

            final Channel channel = ctx.getChannel();

            // If binding is happening to a different Zid than already
            // set, throw an error
            if (ctx.isBound_()) {
                if (ctx.getRemoteZid_() != remoteZid) {
                    throw new ExZephyrAlreadyBound(
                            channel, ctx.getRemoteZid_(), remoteZid);
                }
            }

            channel.write(new ZephyrBindRequest(remoteZid));
        }

        ctx.setRemoteZid_(remoteZid);
    }

    @Override
    public Object send_(final DID did, IResultWaiter wtr, Prio pri,
            final byte[][] bss, Object cke) throws Exception
    {
        final ChannelFutureListener listener = (wtr == null) ? null :
                new ChannelFutureResultWaiterAdapterListener(wtr);

        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_() throws Exception
            {
                assertEventThread();

                final ZephyrClientContext ctx = contextFromDID(did);

                assert ctx != null : ("No channel to " + did + " exists");

                // Can only send data to bound channels
                if (!ctx.isBound_()) {
                    if (listener != null) {
                        ChannelFuture future = new DefaultChannelFuture(
                                ctx.getChannel(), false);
                        future.addListener(listener);
                        future.setFailure(new ExZephyrChannelNotBound(did));
                    }
                    return;
                }

                final ChannelBuffer payload = ChannelBuffers.copiedBuffer(bss);

                ChannelFuture future = ctx.getChannel().write(payload);
                if (listener != null) {
                    // Only install a callback if there is a listener defined
                    future.addListener(listener);
                }
            }

        }, pri);

        return null;
    }

   /**
     * Called when a channel has successfully connected to the Zephyr relay
     * server. No communication with the remote peer can happen yet, as
     * this channel must still register and bind with the remote peer
     * @param channel The channel which connected
     */
    @Override
    public void onChannelConnected_(final Channel channel)
    {
        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_()
            {
                assertEventThread();

                final ZephyrClientContext ctx = _channelToContext.get(channel);

                if (ctx == null) {
                    // This connection must have been deleted, so kill it silently
                    channel.close();
                    logWarn(channel, "Killing existing channel");
                    return;
                }

                // There is nothing really to do in here, since the Zephyr
                // relay server initiates registration, not us
                logInfo(channel, "Connected to relay " + ctx);
            }

        }, Prio.LO);
    }

    /**
     * Called when a channel has disconnected, either by closing the channel
     * on this end or by the Zephyr relay.
     * @param channel The channel that disconnected
     */
    @Override
    public void onChannelDisconnected_(final Channel channel)
    {
        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_()
            {
                assertEventThread();

                final ZephyrClientContext ctx = _channelToContext.get(channel);

                // Channel may have already been disconnected, so check that
                // there is something to delete
                if (ctx != null) {
                    disconnectImpl_(ctx.getRemoteDID_(), new Exception("Disconnected"), true);
                }
            }

        }, Prio.LO);
    }

    /**
     * Called when the Zephyr relay server sends the Zephyr client a zid
     * @param channel The channel which is registered
     * @param zid the id of the channel. Used to tell other peers how to connect
     */
    @Override
    public void onChannelRegisteredWithZephyr_(final Channel channel, final int zid)
    {
        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_() throws Exception
            {
                assertEventThread();

                final ZephyrClientContext ctx = _channelToContext.get(channel);

                if (ctx == null) {
                    // The channel must have been discarded. Close silently
                    channel.close();
                    return;
                }

                assert !ctx.isRegistered_() : ("Double registration for " + ctx);

                // Record the zid from the registration process
                ctx.setLocalZid_(zid);

                // Report to the above layer (IPipeController) that the connection
                // is readable (can receive data)
                _controller.peerConnected(
                        ctx.getRemoteDID_(),
                        ConnectionType.READABLE,
                        ZephyrClientPipe.this);

                // Send this client's Zid to the peer
                sendZidToPeer_(ctx);

                if (ctx.getRemoteZid_() != Constants.ZEPHYR_INVALID_CHAN_ID) {
                    // The remote Zephyr Id of the peer to connect to has been
                    // set before registration, meaning a bind was attempted but
                    // could not proceed due to registration not being finished.
                    // Initiate binding now.
                    bindWithZephyr_(ctx.getRemoteDID_(), ctx.getRemoteZid_());
                }

                logInfo(channel, "Channel registered with relay " + ctx);
            }

        }, Prio.LO);
    }

    /**
     * Called when the bind request successfully completes. The Zephyr protocol
     * has no acknowledgment besides regular TCP ACK.
     * @param channel The channel that is successfully bound
     */
    @Override
    public void onChannelBoundWithZephyr_(final Channel channel)
    {
        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_()
            {
                assertEventThread();

                final ZephyrClientContext ctx = _channelToContext.get(channel);

                assert ctx != null : ("No ZephyrClientContext exists for channel");

                ctx.setBound_(true);

                logInfo(channel, "Channel bound " + ctx);

                // Notify the above layer(pipe controller) of a writable peer-to-peer connection
                _controller.peerConnected(
                        ctx.getRemoteDID_(),
                        ConnectionType.WRITABLE,
                        ZephyrClientPipe.this);
            }

        }, Prio.LO);
    }

    /**
     * Called when an exception is raised in I/O code or otherwise.
     * @param channel The channel which generated the exception
     * @param e The exception raised
     */
    @Override
    public void onChannelException_(final Channel channel, final Exception e)
    {
        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_()
            {
                onExceptionRaisedImpl_(channel, e);
            }

        }, Prio.LO);
    }

    private void onExceptionRaisedImpl_(Channel channel, Exception e)
    {
        assertEventThread();

        final ZephyrClientContext ctx = _channelToContext.get(channel);

        if (ctx != null) {
            // Exceptions are bad, so just disconnect this channel
            disconnectImpl_(ctx.getRemoteDID_(), e, true);
        }

        logWarn(channel, "Exception raised in channel " + ctx + ": " + e);
    }

    /**
     * Called when a message is received from a remote client on the other side
     * of the Zephyr relay.
     * @param channel The channel which received the data
     * @param data The data
     */
    @Override
    public void onMessageReceivedFromChannel_(final Channel channel, final ChannelBuffer data)
    {
        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_() throws Exception
            {
                assertEventThread();

                final ZephyrClientContext ctx = _channelToContext.get(channel);

                assert ctx != null : ("No ZephyrClientContext exists for channel");

                try {
                    // Convert the ChannelBuffer into a ByteArrayInputStream

                    byte[] dataBytes = new byte[data.readableBytes()];
                    data.readBytes(dataBytes);
                    ByteArrayInputStream bais = new ByteArrayInputStream(dataBytes);

                    // Determine from the data type, whether the data is payload
                    // data or control data
                    Transport.PBTPHeader transhdr = TPUtil.processUnicastHeader(bais);
                    if (TPUtil.isPayload(transhdr)) {
                        _controller.processUnicastPayload(ctx.getRemoteDID_(), transhdr, bais, dataBytes.length);
                    } else {
                         _controller.processUnicastControl(ctx.getRemoteDID_(), transhdr);
                    }

                    // Record the bytes read
                    ctx.incrementBytesRead_(data.readableBytes());
                } catch (IOException e) {
                    throw new ExInvalidZephyrChannelAction(ctx.getChannel(), "Exception in " + ctx);
                }
            }

        }, Prio.LO);
    }

    @Override
    public void onChannelSendComplete_(final Channel channel, final long length)
    {
        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_() throws Exception
            {
                assertEventThread();

                final ZephyrClientContext ctx = _channelToContext.get(channel);
                if (ctx != null) {
                    ctx.incrementBytesWritten_(length);
                } else {
                    assert !channel.isOpen();
                }
            }

        }, Prio.LO);
    }

    @Override
    public void linkStateChanged_(final Set<NetworkInterface> removed,
            Set<NetworkInterface> current)
    {
        assertNotEventThread();

        if (!_isRunning.get()) {
            // The pipe hasn't started, so no connections exist. Nothing to do
            return;
        }

        if (current.isEmpty()) {
            // No available network devices, so all connections are disconnected
            enqueueEvent(new IZephyrEvent() {

                @Override
                public void handle_()
                {
                    disconnectAll_(new IOException("Links reconfigured"));
                }

            }, Prio.LO);
            return;
        }

        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_() throws Exception
            {
                assertEventThread();

                logWarn("Links reconfiguring...");

                // Build up a list of removed inet addresses
                Set<InetAddress> goneAddresses = new HashSet<InetAddress>();
                for (NetworkInterface networkInterface : removed) {
                    Enumeration<InetAddress> addresses =
                            networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        goneAddresses.add(addresses.nextElement());
                    }
                }

                // Find and close the connections that relied on the removed devices
                for (Channel channel : _channelGroup) {
                    InetSocketAddress socketAddress =
                            (InetSocketAddress)channel.getLocalAddress();
                    InetAddress address = socketAddress.getAddress();
                    if (goneAddresses.contains(address)) {
                        ZephyrClientContext ctx = _channelToContext.get(channel);
                        if (ctx != null) {
                            // If there is a ZephyrClientContext for this channel
                            // disconnect it properly
                            disconnectImpl_(ctx.getRemoteDID_(),
                                    new IOException("Links reconfigured"), true);

                            logInfo("Address " + address + " gone");
                        } else {
                            // Ghost connection
                            logWarn(channel, "Should be closed but is lingering");
                        }
                    }
                }

            }

        }, Prio.LO);
    }

    @Override
    public void signallingChannelConnected_() throws ExNoResource
    {
        // Noop
    }

    @Override
    public void signallingChannelDisconnected_() throws ExNoResource
    {
        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_() throws Exception
            {
                assertEventThread();

                disconnectAll_(new IOException("SignallingChannel Disconnected"));
            }

        }, Prio.LO);
    }

    @Override
    public void processSignallingMessage_(final DID did, final PBTPHeader msg)
        throws ExNoResource
    {
        assert msg.getType() == Type.ZEPHYR_CANDIDATE_INFO :
                ("Signalling Channel gave ZephyrClientManager a non-zephyr-candidate-info message");

        assert msg.getZephyrInfo().hasSourceZephyrId() : ("ZephyrCandidateInfo doesn't have Zid");

        final int zid = msg.getZephyrInfo().getSourceZephyrId();

        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_() throws Exception
            {
                assertEventThread();

                final ZephyrClientContext ctx = contextFromDID(did);

                if (ctx == null) {
                    // No such connection exists to this peer, so create one
                    connectImpl_(did);
                }

                bindWithZephyr_(did, zid);
            }

        }, Prio.LO);
    }

    @Override
    public void sendSignallingMessageFailed_(final DID did, final PBTPHeader failedmsg,
            final Exception failex) throws ExNoResource
    {
        enqueueEvent(new IZephyrEvent() {

            @Override
            public void handle_() throws Exception
            {
                assertEventThread();

                final ZephyrClientContext ctx = contextFromDID(did);

                assert ctx != null : ("No channel to " + did + " exists");

                onExceptionRaisedImpl_(ctx.getChannel(), failex);
            }

        }, Prio.LO);
    }

    /**
     * Sends this connection's zid to the intended peer, represented by a
     * ZephyrClientContext
     * @param ctx The ZephyrClientContext which holds the DID to send the zid to
     */
    private void sendZidToPeer_(ZephyrClientContext ctx)
    {
        assertEventThread();

        assert ctx.getLocalZid_() != Constants.ZEPHYR_INVALID_CHAN_ID
                : ("Invalid Zid");

        PBTPHeader message = PBTPHeader.newBuilder()
                .setType(Type.ZEPHYR_CANDIDATE_INFO)
                .setZephyrInfo(
                        PBZephyrCandidateInfo.newBuilder()
                                             .setSourceZephyrId(ctx.getLocalZid_()))
                .build();

        _signallingChannel.sendMessageOnSignallingChannel(
                ctx.getRemoteDID_(), message, this);
    }

    @Override
    public String id()
    {
        return _id.id();
    }

    @Override
    public int pref()
    {
        return _id.pref();
    }

    @Override
    public long getBytesRx(final DID did)
    {
        final OutArg<Long> bytes = new OutArg<Long>(-1L);

        try {
            enqueueSynchronousEvent(new IZephyrEvent() {

                @Override
                public void handle_() throws Exception
                {
                    assertEventThread();

                    final ZephyrClientContext ctx = contextFromDID(did);
                    if (ctx == null) {
                        logWarn("DID " + did + " does not exist for getBytesRx");
                    } else {
                        bytes.set(ctx.getBytesRead_());
                    }
                }

            }, Prio.HI);
        } catch (InterruptedException e) {
            logWarn("Interrupted getBytesRx");
        }
        return bytes.get();
    }

    @Override
    public void dumpStat(PBDumpStat template, final PBDumpStat.Builder builder)
            throws Exception
    {
        final PBTransport tpTemplate = template.getTp(0);

        assert tpTemplate != null : ("ZephyrPipeDebug: Invalid dumpstat template");

        try {
            enqueueSynchronousEvent(new IZephyrEvent() {

                @Override
                public void handle_() throws Exception
                {
                    assertEventThread();

                    PBTransport.Builder tpBuilder = PBTransport.newBuilder();

                    tpBuilder.setBytesIn(_networkStats.getBytesRx());
                    tpBuilder.setBytesOut(_networkStats.getBytesTx());

                    if (tpTemplate.hasName()) {
                        tpBuilder.setName(id());
                    }

                    if (tpTemplate.getConnectionCount() != 0) {
                        for (ZephyrClientContext c : _channelToContext.values()) {
                            tpBuilder.addConnection(c.toString());
                        }
                    }

                    if (tpTemplate.hasDiagnosis()) {
                        String endl = System.getProperty("line.separator");

                        StringBuilder strbuilder = new StringBuilder(1024);
                        for (ZephyrClientContext c : _channelToContext.values()) {
                            strbuilder.append(c.getDebugString_());
                            strbuilder.append(endl);
                        }

                        tpBuilder.setDiagnosis(strbuilder.toString());
                    }

                    builder.addTp(tpBuilder);
                }

            }, Prio.HI);
        } catch (InterruptedException e) {
            logWarn("Interrupted dumpStat");
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
        throws Exception
    {
        logInfo("dumpstatmisc");
        ps.println(indent + "disp:ready");
    }

    private void logInfo(Channel channel, String message)
    {
        logInfo("[ch " + channel.getId() + "] " + message);
    }

    private void logInfo(String message)
    {
        l.info(_id.id() + ": " + message);
    }

    private void logWarn(Channel channel, String message)
    {
        logWarn("[ch " + channel.getId() + "] " + message);
    }

    private void logWarn(String message)
    {
        l.warn(_id.id() + ": " + message);
    }

    /**
     * Enqueues an event to be dispatched, blocking the caller until the event
     * is processed by the Event loop.
     * @important This method <strong>MUST NOT</strong> be called from the
     * Event loop thread, or deadlock will occur.
     * @param event The event to run synchronously
     * @param priority The priority at which to schedule the event to be processed
     * @throws InterruptedException
     */
    protected void enqueueSynchronousEvent(final IZephyrEvent event, Prio priority)
            throws InterruptedException
    {
        assertNotEventThread();

        final Object waitLock = new Object();
        synchronized (waitLock) {
            enqueueEvent(new IZephyrEvent() {

                @Override
                public void handle_() throws Exception
                {
                    assertEventThread();

                    synchronized (waitLock) {
                        try {
                            // Process the event
                            event.handle_();
                        } finally {
                            // Wake up the thread waiting on the result
                            waitLock.notifyAll();
                        }
                    }

                }

            }, priority);

            // Wait for async event to finish
            waitLock.wait();
        }
    }

    @Override
    public void run() {
        _isRunning.set(true);

        // Run the event loop
        super.run();

        // Close channels and wait for operations to finish
        _channelGroup.close().awaitUninterruptibly();

        // Clear mappings
        _didToChannel.clear();
        _channelToContext.clear();

        // Release any threads and resources
        _bootstrap.releaseExternalResources();
    }

    @Override
    protected boolean onEvent(IZephyrEvent event)
    {
        assertEventThread();

        try {
            event.handle_();
        } catch (ExZephyrAlreadyBound e) {
            // Report the exception for the related channel
            onExceptionRaisedImpl_(e.getChannel(), e);
        } catch (Exception e) {
            e.printStackTrace();
            assert false : ("Unknown exception in ZephyrClientPipe");
            return true;
        }

        if (!_isRunning.get()) {
            return true;
        }

        return false;
    }

   /**
     * Wraps an IResultWaiter in a ChannelFutureListener and calls
     * {@link IResultWaiter#okay()} when the listener is called and the
     * operation was successful, or {@link IResultWaiter#error(Exception)} if
     * the operation failed
     */
    private class ChannelFutureResultWaiterAdapterListener implements
            ChannelFutureListener
    {

        private final IResultWaiter _waiter;

        public ChannelFutureResultWaiterAdapterListener(IResultWaiter waiter)
        {
            _waiter = waiter;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception
        {
            if (future.isSuccess()) {
                _waiter.okay();
            } else {
                _waiter.error(new Exception(future.getCause()));
            }
        }

    }

}
