/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap.filter;

/**
 * Listener interface for the MessageFilter
 */
public interface IMessageFilterListener
{
    /**
     * Called when an outgoing message is received by the filter in the Pipeline
     */
    void onOutgoingMessageReceived_(MessageFilterRequest request);

    /**
     * Called when an incoming message is received by the filter in the Pipeline
     */
    void onIncomingMessageReceived_(MessageFilterRequest request);
}