/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.base.id.DID;

import java.io.InputStream;
import java.net.InetAddress;

/**
 * This is a temporary interface to expose only the minimum set of methods that Unicast and TCPServerHandler
 * need to know about TCP. This is going to be refactored later into a better architecture.
 */
public interface ITCP
{
    public void onMessageReceived(InetAddress remote, DID did, InputStream is) throws Exception;
    public void closePeerStreams(DID did, boolean outbound, boolean inbound);
}
