/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.lib;

import java.net.*;

/**
 * Utility functions for printing sockets, data transfer strings, etc.
 */
public class AddressUtils
{
    /**
     * Convenience method to get the IP part of an {@link InetSocketAddress}
     *
     * @param a address from which to get the IP
     * @return the IP in a {@link InetAddress}
     */
    public static InetAddress getinetaddr(InetSocketAddress a)
    {
        assert a != null : ("invalid isa");

        return a.getAddress();
    }

    /**
     * Convenience method to get the IP from which a {@link DatagramPacket} was sent
     *
     * @param pkt the datagram packet
     * @return a valid {@link InetAddress} with the IP
     */
    public static InetAddress getinetaddr(DatagramPacket pkt)
    {
        assert pkt != null : ("null packet");
        assert pkt.getAddress() != null : ("null addr from pkt");

        return ((InetSocketAddress) pkt.getSocketAddress()).getAddress(); // taking a chance here
    }

    /**
     * Helpful logging method to print an {@link InetSocketAddress} in a consistent way
     *
     * @param a address to print
     * @return log string of the form: addr:port
     */
    public static String printaddr(InetSocketAddress a)
    {
        assert a != null : ("invalid isa");

        return a.getAddress() + ":" + a.getPort();
    }

    /**
     * Helpful logging method for printing socket addresses in a consistent way
     *
     * @param s {@link Socket} from which to get local and remote addresses
     * @param printlocaladdr true if you want the local address, false otherwise
     * @return log string of the form: localaddr:localport or remoteaddr:remoteport
     */
    public static String printsock(Socket s, boolean printlocaladdr)
    {
        assert s != null : ("invalid sock");

        return printlocaladdr ?
            (s.getLocalAddress().getHostAddress() + ":" + s.getLocalPort()) :
            (s.getInetAddress().getHostAddress() + ":" + s.getPort());
    }

    /**
     * Helpful logging method for debugging to print transfers in a consistent way
     *
     * @param s {@link Socket} from which to get local and remote addresses -
     * <strong>it is safer to synchronize around socket before using this method!!!</strong>
     * @param incoming true if transfer is incoming; false otherwise
     * @return log string of the form: localaddr:localport->remoteaddr:remoteport
     * or localaddr:localport<-remoteaddr:remoteport
     * @throws SocketException if the socket is in an invalid state
     */
    public static String getTransferString_(Socket s, boolean incoming) throws SocketException
    {
        assert s != null : ("invalid sock");

        return printsock(s, true) + (incoming ? "<-" : "->") + printsock(s, false);
    }
}
