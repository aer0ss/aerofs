/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ITransport;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;

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

    // I'd like to know, based on a remove, whether the result set is now empty.
    // Multimap does not do this. Ditto the inverse (did adding this key require creating a set?)
    // Asking separately sucks. And if it's uncoordinated, so you can miss or duplicate edges.
    private final SortedSetMultimap<DID, Channel> channels
            = Multimaps.synchronizedSortedSetMultimap(TreeMultimap.<DID, Channel>create());

    // No public constructor
    ChannelDirectory(ITransport tp) { this.tp = tp; }

    /**
     * Register a channel instance for the given remote peer.
     */
    public void register(Channel channel, DID remotePeer)
    {
        // FIXME(jP): enhancement: better atomicity required here
        addChannelCloseFuture(remotePeer, channel);
        channels.put(remotePeer, channel);

        l.info("register t:d:c {}:{}:{}", tp.id(), remotePeer, channel.getId() );
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
     * Set the unicast listener instance to be called on device unavailable.
     * This is required to be called before use of the channel directory.
     */
    public void setUnicastListener(IUnicastListener unicastListener)
    {
        this.unicastListener = unicastListener;
    }

    /**
     * Remove the channel (which was previously registered for the given remote peer).
     */
    void remove(Channel channel, DID remotePeer)
    {
        boolean deviceDisconnected = false;
        l.info("unregister t:d:c {}:{}:{}", tp.id(), remotePeer, channel);

        // The monitor is used only to coordinate the remove() and containskey() so we have
        // a consistent view. Calling any affected listeners is done outside this lock, which
        // means you could generate an EIPresence Down event _after_ a new channel has been added
        // to the directory. Which feels alarming. Previously this was fully uncoordinated.

        synchronized (channels) {
            if (channels.remove(remotePeer, channel)) {
                l.info("{} remove connection", remotePeer);

                // "If this device now has zero unicast channels for this transport..."
                if (!channels.containsKey(remotePeer)) {
                    deviceDisconnected = true;
                }
            }
        }
        if (deviceDisconnected) { unicastListener.onDeviceDisconnected(remotePeer); }
    }

    /**
     * Add a future that will perform directory management when a channel goes offline.
     */
    private void addChannelCloseFuture(final DID did, final Channel channel)
    {
        Preconditions.checkNotNull(unicastListener);

        channel.getCloseFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception
            {
                ChannelDirectory.this.remove(channel, did);
            }
        });
    }
}
