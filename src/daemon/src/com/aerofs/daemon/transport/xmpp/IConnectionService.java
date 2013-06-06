/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.lib.IIdentifier;
import com.aerofs.daemon.transport.lib.IPipeDebug;
import com.aerofs.daemon.transport.lib.IUnicast;

import java.net.NetworkInterface;
import java.util.Set;

/**
 * Implemented by classes that provide a connected transport channel to a peer.
 * This is a meta-interface that combines {@link IIdentifier}, {@link IUnicast}
 * and {@link IPipeDebug}. Implementations have the following broad properties:
 * <ul>
 *     <li>Each can be identified, and there is a total rank ordering
 *         between this implementation and its siblings</li>
 *     <li>Each has to be explicitly 1) constructed, 2) initialized, and 3) started</li>
 *     <li>Callers have to <code>connect()</code> to a peer before they can
 *         <code>send()</code> to it</li>
 *     <li>Callers can explicitly <code>disconnect()</code> from a peer</li>
 * </ul>
 */
public interface IConnectionService extends IIdentifier, IUnicast, IPipeDebug
{
    /**
     * Sets up data structures for this <code>IConnectionService</code>. Implementations
     * <strong>SHOULD</strong> assume that by this phase all member variables
     * have been set.
     * @throws Exception on any setup error. If an exception is thrown this
     * <code>IConnectionService</code> is in an inconsistent state and cannot be used
     */
    public void init() throws Exception;

    /**
     * Starts the minimum set of threads necessary for this <code>IConnectionService</code>
     * to begin IO processing
     */
    public void start();

    /**
     * Checks if this <code>IConnectionService</code> can perform IO
     *
     * @return <code>true</code> if this instance can perform IO, <code>false</code>
     * if not
     */
    public boolean ready();

    /**
     * Connect to a peer
     *
     * @param did {@link DID} of the peer to connect to
     */
    public void connect(DID did);

    /**
     * Disconnect from a peer
     *
     * @param did {@link DID} of the peer to disconnect from
     * @param ex <code>Exception</code> to be delivered to waiting senders informing
     * them why the connection was closed
     */
    public void disconnect(DID did, Exception ex);

    /**
     * Call to indicate that the makeup of network link has changed.
     * By "network link" we mean the set of usable <em>network interfaces</em>
     * on the local machine.
     *
     * @param rem set of {@link NetworkInterface} instances removed in this change
     * @param cur set of {@link NetworkInterface} instances that remain (i.e. are current)
     * after this change. If this set is empty, this means that there is <em>no</em>
     * network connection, and that no new connections are possible. Existing
     * connections will be disrupted, and implementations may choose to terminate
     * them preemptively.
     */
    public void linkStateChanged(Set<NetworkInterface> rem, Set<NetworkInterface> cur);
}
