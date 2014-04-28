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
 * Pass 1: consolidate users of MultiMap in Unicast.
 * Pass 2: One instance of ChannelDirectory to serve all instances of Unicast.
 * Pass 3: make best-channel-available decisions from here.
 */
public class ChannelDirectory
{
    private static final Logger l = Loggers.getLogger(ChannelDirectory.class);

    /* No public constructor */
    ChannelDirectory(ITransport tp) { _tp = tp; }

    /**
     * Register a channel instance for the given remote peer.
     */
    public void register(Channel channel, DID remotePeer)
    {
        // FIXME(jP): enhancement: better atomicity required here
        _channels.put(remotePeer, channel);
        addChannelCloseFuture(remotePeer, channel);

        l.info("register t:d:c {}:{}:{}", _tp.id(), remotePeer, channel.getId() );
    }

    /**
     * Remove the channel (which was previously registered for the given remote peer).
     */
    void remove(Channel channel, DID remotePeer)
    {
        l.info("unregister t:d:c {}:{}:{}", _tp.id(), remotePeer, channel);

        // Remove only if its still the same client in the map.
        // Hmm, when is that not true? and more importantly, when did someone pre-remove me?
        // I don't like this logic.
        // FIXME(jP) BUG? remove and containsKey on the same key, no coordination.
        if (_channels.remove(remotePeer, channel)) {
            l.info("{} remove connection", remotePeer);

            // "If this device now has zero unicast channels for this transport..."
            if (!_channels.containsKey(remotePeer)) {
                // NOTE: this _may_ cause an EIPresence event to be sent up to the core!
                _unicastListener.onDeviceDisconnected(remotePeer);
            }
        }
    }

    /**
     * Build a snapshot of the active device list.
     */
    public ImmutableSet<DID> getActiveDevices()
    {
        // FIXME: BUG? can't use the view returned by _channels.get() without synchronizing on _channels. See Google apidoc.
        return ImmutableSet.copyOf(_channels.keySet());
    }

    /**
     * Return an immutable copy of the set of channels for the given Device at a given moment.
     */
    public ImmutableSet<Channel> getSnapshot(DID did)
    {
        // FIXME: BUG? can't use the view returned by _channels.get() without synchronizing on _channels. See Google apidoc.
        return ImmutableSet.copyOf(_channels.get(did));
    }

    /**
     * Set the unicast listener instance to be called on device unavailable.
     * This is required to be called before use of the channel directory.
     */
    public void setUnicastListener(IUnicastListener unicastListener)
    {
        _unicastListener = unicastListener;
    }

    /**
     * Add a future that will perform directory management when a channel goes offline.
     */
    private void addChannelCloseFuture(final DID did, final Channel channel)
    {
        Preconditions.checkNotNull(_unicastListener);

        channel.getCloseFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception
            {
                remove(channel, did);
            }
        });
    }

    /**
     * Right now the Transport instance is purely for logging/debugging.
     * In the future this param will go away, and the Transport will be a parameter of the
     * register / unregister calls. You know, for multi-indexing.
     */
    private final ITransport _tp;
    private IUnicastListener _unicastListener;

    // I'd like to know, based on a remove, whether the result set is now empty.
    // Multimap does not do this. Ditto the inverse (did adding this key require creating a set?)
    // Asking separately sucks. And if it's uncoordinated, so you can miss or duplicate edges.
    private final SortedSetMultimap<DID, Channel> _channels
            = Multimaps.synchronizedSortedSetMultimap(TreeMultimap.<DID, Channel>create());
}
