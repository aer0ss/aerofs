/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

/**
 * Implemented by classes that want to be notified of
 * events from a transport unicast subsystem.
 */
public interface IUnicastStateListener
{
    /**
     * Triggered when the unicast subsystem
     * is ready to make one-to-one connections to
     * a remote device.
     */
    void onUnicastReady();

    /**
     * Triggered when the unicast subsystem
     * cannot make one-to-one connections to
     * a remote device.
     */
    void onUnicastUnavailable();
}
