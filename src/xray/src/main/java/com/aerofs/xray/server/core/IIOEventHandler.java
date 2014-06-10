/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.xray.server.core;

import java.nio.channels.SelectionKey;

/**
 * Each {@link SelectionKey} can have an attachment. When the {@link Dispatcher}
 * receives an i/o event for a given key it retrieves the attachment and calls
 * the appropriate handle... function for that event
 *
 * @important expected to run <b>in the same thread</b> as the calling
 * {@link java.nio.channels.Selector}
 * @important we expect the object implementing the IIoEventHandler to be attached to
 * the SelectionKey for which the i/o event was generated
 */
public interface IIOEventHandler
{
    /**
     * Handle an OP_ACCEPT event from a Selector
     * @param k SelectionKey for which the event was generated
     * @throws FatalIOEventHandlerException if the handler wants to shut down the
     * entire dispatching and processing system
     */
    public void handleAcceptReady_(SelectionKey k)
        throws FatalIOEventHandlerException;

    /**
     * Handle an OP_CONNECT event from a Selector
     * @param k SelectionKey for which the event was generated
     * @throws FatalIOEventHandlerException if the handler wants to shut down the
     * entire dispatching and processing system
     */
    public void handleConnectReady_(SelectionKey k)
        throws FatalIOEventHandlerException;

    /**
     * Handle an OP_READ event from a Selector
     * @param k SelectionKey for which the event was generated
     * @throws FatalIOEventHandlerException if the handler wants to shut down the
     * entire dispatching and processing system
     */
    public void handleReadReady_(SelectionKey k)
        throws FatalIOEventHandlerException;

    /**
     * Handle an OP_WRITE event from a Selector
     * @param k SelectionKey for which the event was generated
     * @throws FatalIOEventHandlerException if the handler wants to shut down the
     * entire dispatching and processing system
     */
    public void handleWriteReady_(SelectionKey k)
        throws FatalIOEventHandlerException;

    /**
     * Called when the Dispatcher notices that a SelectionKey is no longer valid
     * (i.e. cancelled)
     * @param k SelectionKey that was cancelled
     * @throws FatalIOEventHandlerException if the handler wants to shut down the
     * entire dispatching and processing system
     */
    public void handleKeyCancelled_(SelectionKey k)
        throws FatalIOEventHandlerException;
}
