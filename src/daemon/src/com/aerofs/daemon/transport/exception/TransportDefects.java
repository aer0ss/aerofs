/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.exception;

public abstract class TransportDefects
{
    private TransportDefects()
    {
        // private to prevent instantiation
    }

    public static final String DEFECT_NAME_HANDSHAKE_RENEGOTIATION = "net.zephyr.renegotiation";
    public static final String DEFECT_NAME_XSC_CONNECTION_ALREADY_REPLACED = "net.xsc.listener.replaced";
}
