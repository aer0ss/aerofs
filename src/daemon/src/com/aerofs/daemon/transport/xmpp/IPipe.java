/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp;

import java.net.NetworkInterface;
import java.util.Set;

import com.aerofs.daemon.transport.lib.IIdentifier;
import com.aerofs.daemon.transport.lib.IPipeDebug;
import com.aerofs.daemon.transport.lib.IUnicast;
import com.aerofs.lib.id.DID;

/**
 * Implemented by classes that provide a connected transport channel to a peer.
 * This is a meta-interface that combines {@link IIdentifier}, {@link IUnicast}
 * and {@link IPipeDebug}. Implementations have the following broad properties:
 * <ul>
 *     <li>Each can be identified, and there is a total rank ordering
 *         between this implementation and its siblings</li>
 *     <li>Each has to be explicitly 1) constructed, 2) initialized, and 3) started</li>
 *     <li>Callers have to <code>connect_()</code> to a peer before they can
 *         <code>send_()</code> to it</li>
 *     <li>Callers can explicitly <code>disconnect_()</code> from a peer</li>
 * </ul>
 */
public interface IPipe extends IIdentifier, IUnicast, IPipeDebug
{
    /**
     * Represents the different connection types an {@link IPipe} can be in.
     * <code>READABLE</code> represents an {@link IPipe} that can only receive
     * data and <code>WRITABLE</code> represents an {@link IPipe} that can
     * both receive and write data.
     */
    public enum ConnectionType
    {
        READABLE,
        WRITABLE,
    }

    /**
     * Sets up data structures for this <code>IPipe</code>. Implementations
     * <strong>SHOULD</strong> assume that by this phase all member variables
     * have been set.
     * @throws Exception on any setup error. If an exception is thrown this
     * <code>IPipe</code> is in an inconsistent state and cannot be used
     */
    public void init_() throws Exception;

    /**
     * Starts the minimum set of threads necessary for this <code>IPipe</code>
     * to begin IO processing
     */
    public void start_();

    /**
     * Checks if this <code>IPipe</code> can perform IO
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
    public void connect_(DID did);

    /**
     * Disconnect from a peer
     *
     * @param did {@link DID} of the peer to disconnect from
     * @param ex <code>Exception</code> to be delivered to waiting senders informing
     * them why the connection was closed
     */
    public void disconnect_(DID did, Exception ex);

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
    // FIXME: This only applies to network transports. If we have a non-network transport we have to add another level to the hierarchy
    public void linkStateChanged_(Set<NetworkInterface> rem, Set<NetworkInterface> cur);
}
