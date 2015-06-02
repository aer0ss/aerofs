/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.exceptions.ExTransportUnavailable;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.proto.Diagnostics.ChannelState;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.SucceededChannelFuture;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

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
 *
 * FIXME: The ChannelDirectory should have a pruning service that looks for redundant channels
 * and asks them to stop being channels. The goal is to improve efficiency when multiple callers
 * wind up creating multiple channels to a single Device.
 *
 * TODO: Think about a random cost factor being added to volunteer channels; the goal is to make
 * sure we don't consider two channel instances for the same device _exactly_ equal. Doing so
 * lets us do an intelligent job pruning later.
 */
public class ChannelDirectory
{
    private static final Logger l = Loggers.getLogger(ChannelDirectory.class);
    private final ITransport tp;
    private IDeviceConnectionListener deviceConnectionListener;
    private IUnicastConnector channelCreator;
    // channels is a mapping, per-transport, of devices to channels. It cannot distinguish
    // or sort the Channel instances on any kind of cost. It's used to detect device up/down
    // transitions on a per-transport basis, and also to handle Transport-down events (down all
    // the channels registered for a given Transport)
    private final SortedSetMultimap<DID, Channel> channels = Multimaps.synchronizedSortedSetMultimap(
            TreeMultimap.<DID, Channel>create());

    public ChannelDirectory(ITransport tp, IUnicastConnector channelCreator)
    {
        this.tp = tp;
        this.channelCreator = channelCreator;
    }

    /**
     * Register a channel instance for the given remote peer.
     *
     * Do not hold a lock on the channels monitor here; the close future might be called
     * synchronously, which will try to lock something elsewhere. The channels lock should always
     * be the last one taken.
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
    public ImmutableList<Channel> getSnapshot(DID did)
    {
        synchronized (channels) {
            return ImmutableList.copyOf(channels.get(did));
        }
    }

    /**
     * Return an immutable copy of all channels known to this transport.
     */
    public ImmutableSet<Channel> getAllChannels()
    {
        synchronized (channels) {
            return ImmutableSet.copyOf(channels.values());
        }
    }

    /**
     * Return an immutable copy of all entries (DID->Channel) known for this transport.
     */
    public ImmutableSet<Map.Entry<DID, Channel>> getAllEntries()
    {
        synchronized (channels) {
            return ImmutableSet.copyOf(channels.entries());
        }
    }

    /**
     * Set the device presence listener instance to be called on device unavailable.
     * This is required to be called before use of the channel directory.
     */
    public void setDeviceConnectionListener(IDeviceConnectionListener deviceConnectionListener)
    {
        this.deviceConnectionListener = deviceConnectionListener;
    }

    /**
     * Return any Channel that is active for the given DID.
     * <p/>
     * If no Channel instances exist for the given DID, this will attempt to create one.
     * <p/>
     * NOTE: This method does not strictly guarantee a transition from zero channels to exactly 1.
     * In other words, if multiple threads call here simultaneously it is possible that each
     * thread will create a new channel and add it to the directory. The final result is that you
     * may have N channels after chooseActiveChannel() completes.
     */
    public ChannelFuture chooseActiveChannel(DID did)
            throws ExTransportUnavailable, ExDeviceUnavailable
    {
        ImmutableList<Channel> active = getSnapshot(did);
        return active.isEmpty() ?
            createChannel(did) : new SucceededChannelFuture(chooseFrom(active, did));
    }

    /**
     * Synchronously remove all channels known for the given Device. This returns the set of
     * removed channels; the set may be empty if the device was already offline.
     *
     * This method causes Device Presence update if the device was online.
     *
     * NOTE: It is the caller's responsibility to dispose of the channels (close them) correctly!
     */
    public SortedSet<Channel> detach(DID did)
    {
        SortedSet<Channel> detached = channels.removeAll(did);
        l.info("{} detach", did);
        if (!detached.isEmpty()) {
            deviceConnectionListener.onDeviceDisconnected(did);
        }
        return detached;
    }

    private Channel chooseFrom(ImmutableList<Channel> active, DID did)
    {
        assert !active.isEmpty() : "impossible, did::empty set";
        if (active.size() == 1) { return active.iterator().next(); }

        // prefer Verified channels; to do so, we scan the set first.
        List<Channel> verified = new ArrayList<>(active.size());
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
            } else {
                // indicates two attempts to unregister the same channel?
                l.info("{} unreg !rem {}", remotePeer, TransportUtil.hexify(channel));
            }
        }
        // FIXME: see that comment just above. This is the line that could generate an EIPresence.
        if (deviceDisconnected) { deviceConnectionListener.onDeviceDisconnected(remotePeer); }
    }

    /**
     * get a random member from a set.
     */
    private Channel chooseRandomlyFrom(List<Channel> channels)
    {
        Preconditions.checkArgument(!channels.isEmpty());
        return channels.get(ThreadLocalRandom.current().nextInt(channels.size()));
    }

    /**
     * Add a future that will perform directory management when a channel goes offline.
     */
    private void addChannelCloseFuture(final DID did, final ChannelCost cost, final Channel channel)
    {
        Preconditions.checkNotNull(deviceConnectionListener);

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
     *
     * NOTE: This should not be called with the channels monitor held. Creating a channel may
     * involve a long chain of events that includes reaching back to the Presence notifiers...
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
