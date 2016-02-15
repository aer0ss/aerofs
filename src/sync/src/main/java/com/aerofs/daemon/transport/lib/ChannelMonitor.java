/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.transport.lib.PresenceLocations.DeviceLocations;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocationReceiver;
import com.aerofs.ids.DID;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.exceptions.ExTransportUnavailable;
import com.aerofs.lib.ClientParam;
import com.google.common.collect.ImmutableSet;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
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
public class ChannelMonitor implements IMulticastListener, IDevicePresenceListener,
        IPresenceLocationReceiver
{
    private static Logger l = LoggerFactory.getLogger(ChannelMonitor.class);

    private final ChannelDirectory directory;
    private final IUnicastConnector connector;
    private final PresenceLocations locations;
    private final Timer connectTimer;

    public ChannelMonitor(IUnicastConnector connector, PresenceLocations locations,
                          ChannelDirectory directory, Timer timer)
    {
        this.connector = connector;
        this.locations = locations;
        this.directory = directory;
        this.connectTimer = timer;
    }

    @Override public void onMulticastReady() {}

    @Override public void onMulticastUnavailable() {
        locations.clear();
    }

    @Override
    public void onDeviceReachable(DID did) {}

    @Override
    public void onDeviceUnreachable(DID did) {}

    /**
     * When a channel goes down and takes a device offline, we opportunistically try to reconnect.
     * This goes on as long as the multicast system says the device is still around.
     */
    @Override
    public void onDevicePresenceChanged(DID did, boolean isPotentiallyAvailable)
    {
        if (isPotentiallyAvailable) return;
        scheduleConnect(0, did);
    }

    public ImmutableSet<DID> allReachableDevices()
    {
        return ImmutableSet.copyOf(locations.getAll());
    }

    protected void scheduleConnect(final int iters, final DID did)
    {
        connectTimer.newTimeout(timeout ->  connectNewChannel(iters, did),
                (iters == 0 ? ClientParam.Daemon.CHANNEL_RECONNECT_INITIAL_DELAY : ClientParam.Daemon.CHANNEL_RECONNECT_MAX_DELAY),
                TimeUnit.MILLISECONDS);
    }

    private void connectNewChannel(final int iters, final DID did)
            throws ExTransportUnavailable, ExDeviceUnavailable
    {
        if (locations.has(did)) {
            directory.chooseActiveChannel(did).addListener(cf -> {
                if (cf.isSuccess()) {
                    l.info("{} cm:online", did);
                } else {
                    l.info("{} cm:reconn", did);
                    scheduleConnect(iters + 1, did);
                }
            });
        } else {
            l.info("{} cm:offline, stopping allocator", did);
        }
    }

    @Override
    public void onPresenceReceived(DID did, Set<IPresenceLocation> newLocations) {
        if (newLocations.isEmpty()) {
            l.info("{} down", did);
            locations.remove(did);
            return;
        }
        DeviceLocations locs = locations.get(did);
        Set<IPresenceLocation> diff = locs.setCandidates(newLocations);
        if (!locations.has(did)) {
            throw new IllegalStateException();
        }
        tryConnect(did, locs, diff);
    }

    protected void tryConnect(DID did, DeviceLocations locs, Set<IPresenceLocation> diff) {
        for (IPresenceLocation loc : diff) {
            // FIXME: avoid creating duplicate channel for location
            try {
                connector.newChannel(did, loc).addListener(cf -> {
                    if (!cf.isSuccess()) {
                        l.info("failed location {}", loc);
                        locs.removeCandidate(loc);
                        return;
                    }
                    l.info("verified location {}", loc);
                    locs.addVerified(loc);
                    cf.getChannel().getCloseFuture().addListener(ff -> {
                        locs.removeVerified(loc);
                    });
                });
            } catch (ExTransportUnavailable e) {
                l.info("presence notif for disabled transport {}", loc);
                locs.removeCandidate(loc);
            }
        }
    }
}
