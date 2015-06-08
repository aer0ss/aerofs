/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.lib.IPresenceLocationReceiver;
import com.aerofs.ids.DID;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.exceptions.ExTransport;
import com.aerofs.daemon.transport.lib.exceptions.ExTransportUnavailable;
import com.aerofs.lib.LibParam.Daemon;
import com.aerofs.lib.log.LogUtil;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Monitor the 'reachability' of devices on multicast, track the expected multicast state of
 * each device, and take required actions on state transitions.
 *
 * When a device becomes newly reachable, attempt to connect a channel for that device (do so
 * via a ChannelDirectory so we don't do anything if it's already connected).
 *
 * When a device is no longer reachable, note that fact but do not automatically tear down
 * the unicast connections for that device.
 *
 * When a device goes offline (unicast dropped), attempt to reconnect if the multicast state
 * indicates it _should_ be online.
 */
public class ChannelMonitor implements IMulticastListener, IDevicePresenceListener, IPresenceLocationReceiver
{
    private static Logger l = LoggerFactory.getLogger(ChannelMonitor.class);

    // FIXME: "Sets.newConcurrentHashSet" is in a later Guava release...
    private final Set<DID> knownOnMulticast = Sets.newSetFromMap(new ConcurrentHashMap<>());
    private final ChannelDirectory directory;
    private final Timer connectTimer;

    public ChannelMonitor(ChannelDirectory directory, Timer timer)
    {
        this.directory = directory;
        this.connectTimer = timer;
    }

    @Override public void onMulticastReady() {}
    @Override public void onMulticastUnavailable() { knownOnMulticast.clear(); }

    @Override
    public void onDeviceReachable(DID did)
    {
        if (knownOnMulticast.add(did)) {
            l.info("{} cm +", did);
            try {
                connectNewChannel(0, did);
            } catch (ExTransport etu) {
                l.info("{} cm failed", did, LogUtil.suppress(etu));
            }
        }
    }

    @Override
    public void onDeviceUnreachable(DID did)
    {
        l.info("{} cm -", did);
        knownOnMulticast.remove(did);
    }

    /**
     * When a channel goes down and takes a device offline, we opportunistically try to reconnect.
     * This goes on as long as the XMPPMulticast system says the device is still around.
     */
    @Override
    public void onDevicePresenceChanged(DID did, boolean isPotentiallyAvailable)
    {
        if (isPotentiallyAvailable) { return; }
        scheduleConnect(0, did);
    }

    public ImmutableSet<DID> allReachableDevices()
    {
        return ImmutableSet.copyOf(knownOnMulticast);
    }

    private void scheduleConnect(final int iters, final DID did)
    {
        connectTimer.newTimeout(timeout ->  connectNewChannel(iters, did),
                (iters == 0 ? Daemon.CHANNEL_RECONNECT_INITIAL_DELAY : Daemon.CHANNEL_RECONNECT_MAX_DELAY),
                TimeUnit.MILLISECONDS);
    }

    private void connectNewChannel(final int iters, final DID did)
            throws ExTransportUnavailable, ExDeviceUnavailable
    {
        if (knownOnMulticast.contains(did)) {

            directory.chooseActiveChannel(did).addListener(
                    channelFuture -> {
                        if (channelFuture.isSuccess()) {
                            l.info("{} cm:online", did);
                        } else {
                            l.info("{} cm:reconn", did);
                            scheduleConnect(iters + 1, did);
                        }
                    }
            );
        } else {
            l.info("{} cm:offline, stopping allocator", did);
        }
    }

    @Override
    public void onPresenceReceived(IPresenceLocation presenceLocation)
    {
        // TODO: check the connection and act as needed
    }
}
