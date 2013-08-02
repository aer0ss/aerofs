/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.proto.Diagnostics.PBInetSocketAddress;
import com.aerofs.proto.Diagnostics.ServerStatus;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.UnknownHostException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utility functions for printing sockets, data transfer strings, etc.
 */
public class TransportUtil
{
    private static final Logger l = Loggers.getLogger(TransportUtil.class);

    /**
     * Helpful logging method to print an {@link InetSocketAddress} in a consistent way
     *
     * @param a address to print
     * @return log string of the form: addr:port
     */
    public static String prettyPrint(InetSocketAddress a)
    {
        checkNotNull(a);
        return a.getAddress() + ":" + a.getPort();
    }

    /**
     * Attempts to return a resolved address, and if not, null
     */
    public static @Nullable String getResolvedAddress(InetSocketAddress a)
    {
        checkNotNull(a);

        InetAddress resolved = null;

        if (a.isUnresolved()) {
            String host = a.getHostName();
            try {
                resolved = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                l.warn("fail name resolution host:{}", host);
            }
        } else {
            resolved = a.getAddress();
        }

        return (resolved == null ? null : resolved.getHostAddress());
    }

    public static PBInetSocketAddress fromInetSockAddress(InetSocketAddress a)
    {
        checkNotNull(a);

        PBInetSocketAddress.Builder builder = PBInetSocketAddress
                .newBuilder()
                .setHost(a.getHostName())
                .setPort(a.getPort());

        String resolved = getResolvedAddress(a);
        if (resolved != null) builder.setResolvedHost(resolved);

        return builder.build();
    }

    public static Socket newConnectedSocket(InetSocketAddress serverAddress, int ioTimeout)
            throws IOException
    {
        Socket s = null;
        try {
            InetSocketAddress address = serverAddress;
            if (address.isUnresolved()) {
                address = new InetSocketAddress(address.getHostName(), address.getPort());
            }

            s = new Socket();
            s.connect(address, ioTimeout);
            s.setSoTimeout(ioTimeout);
            return s;
        } catch (IOException e) {
            try {
                if (s!= null) s.close();
            } catch (IOException closeException) {
                l.warn("fail close socket err:{}", closeException.getMessage());
            }

            throw e;
        }
    }

    public static String getReachabilityErrorString(ServerStatus.Builder serverStatus, IOException e)
    {
        return getUserFacingIOExceptionMessage(e) + ": " + serverStatus.getServerAddress().getHost();
    }

    private static String getUserFacingIOExceptionMessage(IOException e)
    {
        if (e instanceof NoRouteToHostException) {
            return "no route to host";
        } else if (e instanceof UnknownHostException) {
            return "unknown host";
        } else {
            return e.getMessage();
        }
    }
}
