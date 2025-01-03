package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.daemon.transport.presence.TCPPresenceLocation;
import com.aerofs.ids.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.exceptions.ExTransportUnavailable;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.handlers.CNameVerifiedHandler;
import com.aerofs.daemon.transport.lib.handlers.MessageHandler;
import com.aerofs.daemon.transport.lib.handlers.ShouldKeepAcceptedChannelHandler;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.protobuf.Message;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;

import static com.aerofs.daemon.transport.lib.TransportUtil.newExTransportOrFatalOnError;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class Unicast implements ILinkStateListener, IUnicast, IUnicastConnector, ChannelRegisterer
{
    private static final Logger l = Loggers.getLogger(Unicast.class);

    private final IAddressResolver addressResolver;

    private ServerBootstrap serverBootstrap;
    private ClientBootstrap clientBootstrap;
    private IUnicastStateListener unicastStateListener;
    private ChannelDirectory directory;

    private Channel serverChannel;
    private volatile boolean paused;
    private volatile boolean running;
    private volatile boolean reuseChannels = true;

    public Unicast(IAddressResolver addressResolver, ITransport transport)
    {
        this.addressResolver = addressResolver;
        this.directory = new ChannelDirectory(transport, this);
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

    public void setUnicastStateListener(IUnicastStateListener unicastStateListener)
    {
        this.unicastStateListener = unicastStateListener;
    }

    public void setDeviceConnectionListener(IDeviceConnectionListener deviceConnectionListener)
    {
        // FIXME(jP): ugh, spreading state. How do we make this go away?
        directory.setDeviceConnectionListener(deviceConnectionListener);
    }

    public void start(SocketAddress address)
    {
        synchronized (this) {
            if (running) return;

            checkState(serverChannel == null);
            serverChannel = serverBootstrap.bind(address);

            running = true;
        }

        unicastStateListener.onUnicastReady();
    }

    public synchronized void stop()
    {
        if (!running) return;

        pauseAccept();
        running = false;

        directory.disable(Unicast::disconnect);

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
        // more edge detection: zero to any links, any to zero links.

        if (becameLinkDown) {
            synchronized (this) {
                pauseAccept();
            }

            directory.disable(Unicast::disconnect);
        }

        if (previous.isEmpty() && !current.isEmpty()) {
            synchronized (this) {
                resumeAccept();
            }

            directory.enable();
        }
    }

    private void pauseAccept()
    {
        paused = true;
        enableChannelAccept(false);
        unicastStateListener.onUnicastUnavailable();
        l.info("pause unicast accept");
    }

    private void resumeAccept()
    {
        paused = false;
        enableChannelAccept(true);
        unicastStateListener.onUnicastReady();
        l.info("resume unicast accept");
    }

    private void enableChannelAccept(boolean enabled)
    {
        ChannelHandler handler = serverBootstrap.getParentHandler();
        ((ShouldKeepAcceptedChannelHandler) handler).enableAccept(enabled);
    }

    /**
     * Called by the IncomingChannelHandler when the server has accepted a connection form a remote client
     */
    @Override
    public boolean registerChannel(Channel channel, final DID did)
    {
        checkNotNull(directory);
        return directory.registerChannel(channel, did);
    }

    /**
     * Disconnects from a remote peer.
     */
    private static void disconnect(Channel channel)
    {
        channel.getPipeline().get(MessageHandler.class)
                .setDisconnectReason(new ExTransportUnavailable("tp down"));
        channel.close();
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
     * @param reuseChannels set to true if channels should be reused, false otherwise.
     */
    void setReuseChannels(boolean reuseChannels)
    {
        this.reuseChannels = reuseChannels;
    }

    @Override
    public Object send(final DID did, byte[][] bss, final @Nullable IResultWaiter wtr)
            throws ExTransportUnavailable, ExDeviceUnavailable {
        Channel channel;
        synchronized (this) {
            if (reuseChannels) {
                channel = directory.chooseActiveChannel(did).getChannel();
            } else { // TODO: remove this case, used in unit tests only :(
                channel = newChannel(did).getChannel();
            }
        }

        send(channel, bss, wtr);
        return channel;
    }

    @Override
    public void send(@Nonnull Object cookie, byte[][] bss, final @Nullable IResultWaiter wtr) {
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

        send((Channel) cookie, bss, wtr);
    }

    private void send(Channel channel, byte[][] bss, @Nullable IResultWaiter wtr)
    {
        ChannelFuture writeFuture = channel.write(bss);
        if (wtr == null) return;
        writeFuture.addListener(cf -> {
            if (cf.isSuccess()) {
                wtr.okay();
            } else {
                DID did;
                try {
                    did = TransportUtil.getChannelData(channel).getRemoteDID();
                } catch (Throwable t) { did = null; }
                wtr.error(newExTransportOrFatalOnError("fail send packet to " + did, cf.getCause()));
            }
        });
    }

    /**
     * Creates a new client connected to the specified did
     *
     * @throws ExTransportUnavailable if the unicast service is paused or has already been shut down
     * @throws ExDeviceUnavailable if we cannot find a remote address for the device
     */
    @Override
    public ChannelFuture newChannel(final DID did) throws ExTransportUnavailable, ExDeviceUnavailable
    {
        if (!isServiceAvailable()) {
            throw new ExTransportUnavailable("transport unavailable running:" + running + " paused:" + paused);
        }
        SocketAddress remoteAddress = addressResolver.resolve(did);
        return newChannel(did, remoteAddress);
    }

    @Override
    public ChannelFuture newChannel(DID did, IPresenceLocation location) throws ExTransportUnavailable
    {
        if (!isServiceAvailable()) {
            throw new ExTransportUnavailable("transport unavailable running:" + running + " paused:" + paused);
        }
        Preconditions.checkArgument(location instanceof TCPPresenceLocation);
        return newChannel(did, ((TCPPresenceLocation)location).socketAddress());
    }

    private ChannelFuture newChannel(DID did, SocketAddress remoteAddress)
    {
        ChannelFuture channelFuture = clientBootstrap.connect(remoteAddress);
        Channel channel = channelFuture.getChannel();

        l.debug("{} created new channel {}", did, TransportUtil.hexify(channel));

        CNameVerifiedHandler verifiedHandler = channel.getPipeline().get(CNameVerifiedHandler.class);
        verifiedHandler.setExpectedRemoteDID(did);

        return channelFuture;
    }

    public ChannelDirectory getDirectory() { return directory; }

    public Collection<Message> getChannelDiagnostics(DID did)
    {
        List<Message> diagnostics = Lists.newArrayList();

        for (Channel channel : directory.getSnapshot(did)) {
            IChannelDiagnosticsHandler handler = channel.getPipeline().get(IChannelDiagnosticsHandler.class);
            if (handler != null) {
                diagnostics.add(handler.getDiagnostics(channel));
            }
        }

        return diagnostics;
    }
}
