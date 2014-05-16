/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.proto.Diagnostics.ChannelState;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.SucceededChannelFuture;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Presence: The New Hotness.
 *
 * A simple interface to encapsulate putting channels into an indexed data structure, and
 * later query that structure for channels.
 */
/* Notes: In the future, this should automatically detect edges (zero to any, any to zero members
 * of a particular set). Also the ability to select members of this set in different ways would
 * be super super great.
 *
 * Next Pass: One instance of ChannelDirectory to serve all instances of Unicast.
 *
 * Next next pass: make best-channel-available decisions from here.
 */
public class ChannelDirectory
{
    private static final Logger l = Loggers.getLogger(ChannelDirectory.class);
    private final ITransport tp;
    private IUnicastListener unicastListener;
    private IUnicastConnector channelCreator;
    private Random random = new Random();
    // channels is a mapping, per-transport, of devices to channels. It cannot distinguish
    // or sort the Channel instances on any kind of cost. It's used to detect device up/down
    // transitions on a per-transport basis, and also to handle Transport-down events (down all
    // the channels registered for a given Transport)
    private final SortedSetMultimap<DID, Channel> channels = Multimaps.synchronizedSortedSetMultimap(
            TreeMultimap.<DID, Channel>create());

    // No public constructor
    ChannelDirectory(ITransport tp, IUnicastConnector channelCreator)
    {
        this.tp = tp;
        this.channelCreator = channelCreator;
    }

    /**
     * Register a channel instance for the given remote peer.
     */
    public void register(Channel channel, DID remotePeer)
    {
        // FIXME(jP): enhancement: better atomicity required here
        ChannelCost cost = new ChannelCost(channel, tp);
        channels.put(remotePeer, channel);
        addChannelCloseFuture(remotePeer, cost, channel);

        l.info("{} register t:c {}:{}", remotePeer, tp.id(), TransportUtil.hexify(channel));
    }

    /**
     * Build a snapshot of the active device list.
     */
    public ImmutableSet<DID> getActiveDevices()
    {
        synchronized (channels) {
            return ImmutableSet.copyOf(channels.keySet());
        }
    }

    /**
     * Return an immutable copy of the set of channels for the given Device at a given moment.
     */
    public ImmutableSet<Channel> getSnapshot(DID did)
    {
        synchronized (channels) {
            return ImmutableSet.copyOf(channels.get(did));
        }
    }

    /**
     * Set the unicast listener instance to be called on device unavailable. This is required to be
     * called before use of the channel directory.
     */
    public void setUnicastListener(IUnicastListener unicastListener)
    {
        this.unicastListener = unicastListener;
    }

    /**
     * Return any Channel that is active for the given DID.
     * <p/>
     * If no Channel instances exist for the given DID, this will attempt to create one.
     */
    public ChannelFuture chooseActiveChannel(DID did)
            throws ExTransportUnavailable, ExDeviceUnavailable
    {
        // Sucks to acquire the monitor solely for the zero-to-one transition. Use atomics?
        ImmutableSet<Channel> active;
        synchronized (channels) {
            if (!channels.containsKey(did)) {
                return createChannel(did);
            }
            active = ImmutableSet.copyOf(channels.get(did));
        }
        return new SucceededChannelFuture(chooseFrom(active, did));
    }

    private Channel chooseFrom(ImmutableSet<Channel> active, DID did)
    {
        assert !active.isEmpty() : "impossible, did::empty set";
        if (active.size() == 1) { return active.iterator().next(); }

        // prefer Verified channels; to do so, we scan the set first.
        Set<Channel> verified = new HashSet<Channel>(active.size());
        for (Channel chan : active) {
            if (TransportUtil.getChannelState(chan).equals(ChannelState.VERIFIED)) {
                verified.add(chan);
            }
        }

        Channel retval = chooseRandomlyFrom(verified.isEmpty() ? active : verified);
        l.info("{} cdir ret c:{}(_/{}) s:{}", did,
                TransportUtil.hexify(retval), active.size(),
                TransportUtil.getChannelState(retval).name());
        return retval;
    }

    /**
     * Remove the channel (which was previously registered for the given remote peer).
     */
    void remove(ChannelCost cost, Channel channel, DID remotePeer)
    {
        // Note: deviceDisconnected is per-transport (at least right now)
        boolean deviceDisconnected = false;
        l.info("{} unregister t:c {}:{}", remotePeer, tp.id(), TransportUtil.hexify(channel));

        // The channels monitor is used only to coordinate the remove() and containskey() so we have
        // a consistent view. Calling any affected listeners is done outside this lock, which
        // means you could generate an EIPresence Down event _after_ a new channel has been added
        // to the directory. Which feels alarming. Previously this was fully uncoordinated.

        synchronized (channels) {
            if (channels.remove(remotePeer, channel)) {

                // "If this device now has zero unicast channels for this transport..."
                if (!channels.containsKey(remotePeer)) {
                    deviceDisconnected = true;
                }
            }
        }
        // FIXME: see that comment just above. This is the line that could generate an EIPresence.
        if (deviceDisconnected) { unicastListener.onDeviceDisconnected(remotePeer); }
    }

    /**
     * get a random member from a set.
     */
    private Channel chooseRandomlyFrom(Set<Channel> channels)
    {
        Preconditions.checkArgument(!channels.isEmpty());
        return channels.toArray(new Channel[0])[random.nextInt(channels.size())];
    }

    /**
     * Add a future that will perform directory management when a channel goes offline.
     */
    private void addChannelCloseFuture(final DID did, final ChannelCost cost, final Channel channel)
    {
        Preconditions.checkNotNull(unicastListener);

        channel.getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
                    throws Exception
            {
                ChannelDirectory.this.remove(cost, channel, did);
            }
        });
    }

    /**
     * FIXME: I don't like this method. It only exists due to reuseChannels, which in turn is
     * only used to whitebox some unit test stuff. Usage within this class can be inlined.
     */
    ChannelFuture createChannel(DID did) throws ExTransportUnavailable, ExDeviceUnavailable
    {
        ChannelFuture future = channelCreator.newChannel(did);
        register(future.getChannel(), did);
        return future;
    }

    private class ChannelCost implements Comparable<ChannelCost>
    {
        ITransport tp;
        Channel channel;

        ChannelCost(Channel channel, ITransport tp) { this.channel = channel; this.tp = tp; }

        /** Use Transport rank to compare two ChannelCost instances */
        // TODO: use channel state as well; prefer VERIFIED over CONNECTED
        @Override
        public int compareTo(ChannelCost other)
        {
            int delta = this.tp.rank() - other.tp.rank();
            return (delta == 0) ? this.channel.compareTo(other.channel) : delta;
        }
    }
}
