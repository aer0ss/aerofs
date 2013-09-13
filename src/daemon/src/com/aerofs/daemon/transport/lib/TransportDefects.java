/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

public abstract class TransportDefects
{
    private TransportDefects()
    {
        // private to prevent instantiation
    }

    public static final String DEFECT_NAME_HANDSHAKE_RENEGOTIATION = "net.zephyr.renegotiation";
    public static final String DEFECT_NAME_XSC_CONNECTION_ALREADY_REPLACED = "net.xsc.listener.replaced";
    public static final String DEFECT_NAME_SLOW_CONNECT = "net.connection.connect.slow"; // sent if a connect takes longer than 10 seconds
    public static final String DEFECT_NAME_CONNECT_FAILED = "net.connection.connect.failed";
}
