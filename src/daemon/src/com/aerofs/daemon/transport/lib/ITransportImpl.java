/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;

import java.net.NetworkInterface;
import java.util.Set;

/**
 * Interface for use within the transport subsytem. Includes many accessors and
 * other methods that should not be visible to the core, only to the transport
 * subsystem itself
 */
public interface ITransportImpl extends ITransport
{
    /**
     * Factory function for creating event handlers
     *
     * @return an {@link com.aerofs.daemon.event.IEventHandler} for handling
     * {@link EOTpStartPulse} events
     */
    HdPulse<EOTpStartPulse> sph();

    //
    // accessors
    // FIXME: all these accessors should be removed in the future
    //

    /**
     * Return the transport's dispatcher
     *
     * @return {@link EventDispatcher} for this transport
     */
    EventDispatcher disp();

    /**
     * Return the transport's scheduler
     *
     * @return {@link Scheduler} for this transport
     */
    Scheduler sched();

    /**
     * Return the transport's connection handler
     *
     * @return {@link IUnicast} for this transport
     */
    IUnicast ucast();

    /**
     * Return the transport's multicast connection handler
     *
     * @return {@link IMaxcast} for this transport
     */
    IMaxcast mcast();

    /**
     * Return the transport's pulse manager
     *
     * @return {@link PulseManager} for this transport
     */
    PulseManager pm();

    /**
     * Return the transport's stream manager
     *
     * @return {@link StreamManager} for this transport
     */
    StreamManager sm();

    /**
     * Return the transport's diagnostic-state object (for in-progress pings, floods, etc.)
     *
     * @return {@link TransportDiagnosisState} for this transport
     */
    TransportDiagnosisState tds();

    //
    // connection/disconnection functions
    //

    /**
     * Disconnect a peer
     *
     * @param did {@link com.aerofs.base.id.DID} to disconnect
     * @throws ExNoResource if the disconnection cannot be performed due to
     * resource constraints
     */
    void disconnect_(DID did) throws ExNoResource; // FIXME: I don't like this throws signature - perhaps it should be Exception

    //
    // event handler functions
    //

    /**
     * Update the list of stores available via this transport
     *
     * @param saddrsAdded list of store addresses that are newly available via this transport
     * @param saddrsRemoved list of store addresses that are no longer available via this transport
     */
    void updateStores_(SID[] sidAdded, SID[] sidRemoved);

    /**
     * Utility function to be called by handlers when the network link changes
     * i.e. interfaces are added, removed, etc.
     *
     * @param removed set of network interfaces that were removed
     * @param added set of network interfaces that were added
     * @param prev full set of previous network interfaces (includes <code>removed</code>)
     * @param current full set of current network interfaces (includes <code>added</code>)
     * @throws ExNoResource if we can't process notification of a link-state change
     * due to resource constraints
     */
    void linkStateChanged_(
        Set<NetworkInterface> removed,
        Set<NetworkInterface> added,
        Set<NetworkInterface> prev,
        Set<NetworkInterface> current) throws ExNoResource;
}
