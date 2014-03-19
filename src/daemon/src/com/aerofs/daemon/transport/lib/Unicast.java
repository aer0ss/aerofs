package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import com.aerofs.daemon.transport.lib.handlers.CNameVerifiedHandler;
import com.aerofs.daemon.transport.lib.handlers.MessageHandler;
import com.aerofs.daemon.transport.lib.handlers.ShouldKeepAcceptedChannelHandler;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.proto.Transport.PBTPHeader;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import com.google.protobuf.Message;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

public final class Unicast implements ILinkStateListener, IUnicastInternal, IIncomingChannelListener
{
    private static final Logger l = Loggers.getLogger(Unicast.class);

    private final SortedSetMultimap<DID, Channel> channels = Multimaps.synchronizedSortedSetMultimap(TreeMultimap.<DID, Channel>create());
    private final IAddressResolver addressResolver;

    private ServerBootstrap serverBootstrap;
    private ClientBootstrap clientBootstrap;
    private IUnicastListener unicastListener;

    private Channel serverChannel;
    private volatile boolean paused;
    private volatile boolean running;
    private volatile boolean reuseChannels = true;

    public Unicast(IAddressResolver addressResolver)
    {
        this.addressResolver = addressResolver;
    }

    // NOTE: these fields cannot be final due to a
    // circular dependency between the TCP and the underlying handlers
    // FIXME (AG): consider using the UnicastProxy to resolve this
    public void setBootstraps(ServerBootstrap serverBootstrap, ClientBootstrap clientBootstrap)
    {
        this.serverBootstrap = serverBootstrap;
        this.clientBootstrap = clientBootstrap;
    }

    private boolean isServiceAvailable()
    {
        return running && !paused;
    }

    public void setUnicastListener(IUnicastListener unicastListener)
    {
        this.unicastListener = unicastListener;
    }

    public void start(SocketAddress address)
    {
        synchronized (this) {
            if (running) return;

            checkState(serverChannel == null);
            serverChannel = serverBootstrap.bind(address);

            running = true;
        }

        unicastListener.onUnicastReady();
    }

    public synchronized void stop()
    {
        if (!running) return;

        pauseAccept();
        running = false;

        disconnectAll(new ExTransportUnavailable("transport shutting down"));

        serverBootstrap.releaseExternalResources();
        clientBootstrap.releaseExternalResources();
    }

    @Override
    public void onLinkStateChanged(
            ImmutableSet<NetworkInterface> previous,
            ImmutableSet<NetworkInterface> current,
            ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed)
    {
        if (!running) return;

        boolean becameLinkDown = !previous.isEmpty() && current.isEmpty();

        if (becameLinkDown) {
            synchronized (this) {
                pauseAccept();
            }

            disconnectAll(new ExDeviceUnavailable("link down"));
        }

        if (previous.isEmpty() && !current.isEmpty()) {
            synchronized (this) {
                resumeAccept();
            }
        }
    }

    private void disconnectAll(Exception cause)
    {
        ImmutableSet<DID> dids = ImmutableSet.copyOf(channels.keySet());
        for (DID did : dids) {
            disconnect(did, cause);
        }
    }

    private void pauseAccept()
    {
        unicastListener.onUnicastUnavailable();
        enableChannelAccept(false);
        paused = true;
        l.info("pause unicast accept");
    }

    private void resumeAccept()
    {
        paused = false;
        enableChannelAccept(true);
        unicastListener.onUnicastReady();
        l.info("resume unicast accept");
    }

    private void enableChannelAccept(boolean enabled)
    {
        serverChannel.setReadable(enabled);

        ChannelHandler handler = serverBootstrap.getParentHandler();
        if (handler != null) {
            ((ShouldKeepAcceptedChannelHandler) handler).enableAccept(enabled);
        }
    }

    /**
     * Called by the IncomingChannelHandler when the server has accepted a connection form a remote client
     */
    @Override
    public void onIncomingChannel(final DID did, Channel channel)
    {
        addChannelCloseFuture(did, channel);
        channels.put(did, channel);

        // if we've paused syncing or have stopped the system, disconnect
        // NOTE: I do this after setting the close future above so that
        // all the presence and cleanup logic is reused
        // I realize that this means that extra work is done, but it's safer than
        // trying to replicate the necessary steps
        if (!isServiceAvailable()) {
            channel.close();
        }
    }

    /**
     * Disconnects from a remote peer.
     */
    @Override
    public void disconnect(DID did, Exception cause)
    {
        l.info("{} disconnect", did, LogUtil.suppress(cause));

        // IMPORTANT: we do _not_ want to remove all the channels
        // for the DID here. This is because the close future handlers
        // use the presence of the DID, Channel pair in the map
        // to determine whether to notify listeners of a disconnection
        // or not
        Set<Channel> active = getActiveChannels(did);
        for (Channel channel : active) {
            channel.getPipeline().get(MessageHandler.class).setDisconnectReason(cause);
            channel.close();
        }
    }

    /**
     * @return address on which the server can accept incoming connections.
     */
    public SocketAddress getListeningAddress()
    {
        return serverChannel.getLocalAddress();
    }

    /**
     * Reuse an existing channel for outgoing messages if one exists.
     * This should <em>only</em> be used in unit tests.
     *
     * @param reuseChannels set to true if channels should be reused, false otherwise.
     */
    void setReuseChannels(boolean reuseChannels)
    {
        this.reuseChannels = reuseChannels;
    }

    @Override
    public Object send(final DID did, final @Nullable IResultWaiter wtr, Prio pri, byte[][] bss, @Nullable Object cookie)
        throws ExTransportUnavailable, ExDeviceUnavailable
    {
        // Use the MessageHandler as the cookie to send the packet if the cookie is present.
        // This is to bind an outgoing stream to a particular connection, needed for the
        // following scenario:
        //
        // 1. A and B have two or more ethernet links connected to each other.
        // 2. A receives B's pong message from one link, and add the IP address to its ARP
        // 3. A starts sending a stream to B
        // 4. A receives B's pong from another link, and in turn update the APR
        // 5. the rest of the chunks in the stream will be sent via the latter link, which violates
        // streams' guarantee of in-order delivery.

        Channel channel = null;
        synchronized (this) {
            if (cookie != null) {
                channel = (Channel) cookie; // use the channel that was used before
            } else { // apparently the caller has no preference on the channel to be used
                if (reuseChannels) { // we should pick an existing channel if there is one
                    // check if we've any channels
                    // to this did and simply pick one
                    Set<Channel> active = getActiveChannels(did);
                    if (!active.isEmpty()) {
                        channel = active.iterator().next();
                    }
                }
            }

            // no cookie was specified and no active channel exists
            // let's create a channel for them
            // FWIW, it's _definitely_ possible for this channel to be null - don't believe the IDE
            // noinspection ConstantConditions
            if (channel == null) {
                channel = newChannel(did);
            }
        }

        ChannelFuture writeFuture = channel.write(bss);
        writeFuture.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
                    throws Exception
            {
                if (channelFuture.isSuccess()) {
                    if (wtr != null) {
                        wtr.okay();
                    }
                } else {
                    if (wtr != null) {
                        Throwable throwable = channelFuture.getCause();
                        if (throwable == null) {
                            throwable = new ExDeviceUnavailable("write failed");
                        }

                        if (throwable instanceof Exception) {
                            wtr.error((Exception) throwable);
                        } else {
                            SystemUtil.fatal(throwable);
                        }
                    }
                }
            }
        });

        return channel;
    }

    private Set<Channel> getActiveChannels(DID did)
    {
        // take a copy to avoid a ConcurrentModificationException
        return ImmutableSet.copyOf(channels.get(did));
    }

    public void sendControl(DID did, PBTPHeader h)
            throws ExTransportUnavailable, ExDeviceUnavailable
    {
        send(did, null, Prio.LO, TransportProtocolUtil.newControl(h), null);
    }

    /**
     * Creates a new client connected to the specified did
     *
     * @throws ExTransportUnavailable if the unicast service is paused or has already been shut down
     * @throws ExDeviceUnavailable if we cannot find a remote address for the device
     */
    private Channel newChannel(final DID did)
            throws ExTransportUnavailable, ExDeviceUnavailable
    {
        if (!isServiceAvailable()) {
            throw new ExTransportUnavailable("transport unavailable running:" + running + " paused:" + paused);
        }

        SocketAddress remoteAddress = addressResolver.resolve(did);
        final Channel channel = clientBootstrap.connect(remoteAddress).getChannel();
        addChannelCloseFuture(did, channel);

        l.debug("{} created new channel", did);

        CNameVerifiedHandler verifiedHandler = channel.getPipeline().get(CNameVerifiedHandler.class);
        verifiedHandler.setExpectedRemoteDID(did);
        channels.put(did, channel);

        return channel;
    }

    private void addChannelCloseFuture(final DID did, final Channel channel)
    {
        channel.getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
                    throws Exception
            {
                // Remove only if its still the same client in the map.
                if (channels.remove(did, channel)) {
                    l.info("{} remove connection", did);

                    if (!channels.containsKey(did)) {
                        unicastListener.onDeviceDisconnected(did);
                    }
                }
            }
        });
    }

    public Collection<Message> getChannelDiagnostics(DID did)
    {
        List<Message> diagnostics = Lists.newArrayList();

        for (Channel channel : getActiveChannels(did)) {
            IChannelDiagnosticsHandler handler = channel.getPipeline().get(IChannelDiagnosticsHandler.class);
            if (handler != null) {
                diagnostics.add(handler.getDiagnostics(channel));
            }
        }

        return diagnostics;
    }
}
