/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.id.DID;
import com.aerofs.base.ex.ExNoResource;

import static com.aerofs.proto.Transport.PBTPHeader;

/**
 * Implemented by classes that want to send and receive messages via an
 * out-of-band signalling (or control) channel. This interface is the dual of
 * {@link ISignallingChannel} and represents the <em>client</em> of the
 * <code>ISignallingChannel</code> provider.
 * <br/>
 * <br/>
 * <strong>IMPORTANT:</strong> The methods in this interface can all throw
 * <code>ExNoResource</code>, indicating that the notification or task cannot
 * be carried out by the implementation due to resource constraints. Although
 * the resource constraint may be a transient condition, callers must decide
 * on a case-by-case basis how to proceed. It is <strong>incorrect</strong> to
 * blindly reschedule the notification or task to run later. Consider the
 * following sequence of events:
 * <ol>
 *     <li><code>signallingChannelDisconnected_()</code>
 *         (throws <code>ExNoResource</code>)</li>
 *     <li>caller reschedules the <code>signallingChannelDisconnected_()</code></li>
 *     <li>resource constrained state is cleared up</li>
 *     <li><code>signallingChannelConnected_()</code> is called (no throw occurs)</li>
 *     <li>rescheduled <code>signallingChannelDisconnected_()</code>
 *         runs (no throw occurs)</li>
 * </ol>
 * This sequence of events ends up with the <code>ISignallingClient</code>
 * believing that the signalling channel is <em>disconnected</em> when in fact it
 * is <em>connected</em>. This indicates that callers must understand under
 * which circumstances they can safely reschedule to work around a transient
 * resource-constrained condition and when they cannot.
 */
public interface ISignallingClient
{
    /**
     * Called when a connection is established to the out-of-band signalling
     * channel
     *
     * @throws ExNoResource if the implementation cannot process this notification
     * at this time. This <em>may</em> be a transient, recoverable error.
     */
    public void signallingChannelConnected_() throws ExNoResource;

    /**
     * Called when the connection to the out-of-band signalling channel is broken.
     * The signalling channel is unusable until the client is signalled via
     * <code>signallingChannelConnected_</code> that the connection has been
     * re-established.
     *
     * @throws ExNoResource if the implementation cannot process this notification
     * at this time. This <em>may</em> be a transient, recoverable error.
     */
    public void signallingChannelDisconnected_() throws ExNoResource;

    /**
     * Called when a message of the type the client registered for via
     * <code>registerSignallingClient_()</code> in {@link ISignallingChannel}
     * is received on the signalling channel
     *
     * @param did {@link DID} of the peer that sent this message
     * @param msg {@link PBTPHeader} body of the message received on the signalling channel
     * @throws ExNoResource if the implementation cannot carry out this task at
     * this time. This <em>may</em> be a transient, recoverable error.
     */
    public void processSignallingMessage_(DID did, PBTPHeader msg) throws ExNoResource;

    /**
     * Called when a message that the client wanted to send on the signalling
     * channel via <code>sendMessageOnSignallingChannel()</code> cannot be sent
     * because of an error. This is an <em>error callback method</em>.
     *
     * @param did {@link DID} of the peer to which the message was supposed to be sent
     * @param failedmsg {@link PBTPHeader} message (in original, client-supplied
     * format) that could not be sent via the signalling channel
     * @param failex Exception that prevented the message from being sent
     * @throws ExNoResource if the implementation cannot carry out this task at
     * this time. This <em>may</em> be a transient, recoverable error.
     */
    public void sendSignallingMessageFailed_(DID did, PBTPHeader failedmsg, Exception failex) throws ExNoResource;
}
