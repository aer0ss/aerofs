/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.xray.server.core;

import com.aerofs.base.Loggers;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * General utility functions used by all zephyr-related code (client, server, dispatcher)
 *
 * @important this class should never store any state
 */
public class ZUtil
{
    //
    // SelectableChannel utility functions
    //

    /**
     * Extracts the channel from an {@link SelectionKey} and attempts to
     * cast it to and return an {@link SocketChannel} object
     *
     * @param k SelectionKey from which to get the channel
     * @return a non-null SocketChannel object
     */
    public static SocketChannel getSocketChannel(SelectionKey k)
    {
        SocketChannel chan = (SocketChannel) k.channel();
        assert chan != null : ("k:" + k + ":null");
        return chan;
    }

    /**
     * Closes any channel
     *
     * @important this method is idempotent. Following the first successful
     * completion, subsequent close() attempts will return without effect
     *
     * @param ch {@link SelectableChannel} to close
     */
    public static void closeChannel(SelectableChannel ch)
    {
        if (ch == null) return;

        try {
            ch.close();
        } catch (IOException sce) {
            l.warn("err on close:" + sce);
        }
    }

    //
    // SelectionKey utility functions
    //

    /**
     * Adds a set of {@link SelectionKey} interest masks to a given key
     *
     * @param k SelectionKey to add the valid interest mask to
     * @param ists varargs list of interest masks to add
     */
    public static void addInterest(SelectionKey k, int... ists)
    {
        int istmask = getInterestMask(ists);
        k.interestOps(k.interestOps() | istmask);
    }


    /**
     * Removes a set of {@link SelectionKey} interest masks from a given key
     *
     * @param k SelectionKey to remove the valid interest mask from
     * @param ists varargs list of interest masks to remove
     */
    public static void subInterest(SelectionKey k, int... ists)
    {
        int istmask = getInterestMask(ists);
        k.interestOps(k.interestOps() & ~istmask);
    }


    public static void clrInterests(SelectionKey k)
    {
        k.interestOps((k.interestOps() & ~SelectionKey.OP_ACCEPT) |
                      (k.interestOps() & ~SelectionKey.OP_READ) |
                      (k.interestOps() & ~SelectionKey.OP_WRITE) |
                      (k.interestOps() & ~SelectionKey.OP_CONNECT));
    }

    /**
     * Check if a {@link SelectionKey} is interested in a specific operation
     *
     * @param k SelectionKey to check for interest
     * @param ist interest (OP_READ, OP_WRITE, OP_ACCEPT, OP_CONNECT) to check
     * @return true if the specified interest is set, false otherwise
     */
    public static boolean hasInterest(SelectionKey k, int ist)
    {
        assertValidInterest(ist);
        return (k.interestOps() & ist) != 0;
    }

    /**
     * Check if a we can perform an operation on a {@link SelectionKey}
     * (i.e. it is ready for that operation)
     *
     * @param k SelectionKey to check for readiness
     * @param ist interest (OP_READ, OP_WRITE, OP_ACCEPT, OP_CONNECT) to check
     * @return true if the operation can be performed, false otherwise
     */
    public static boolean hasReady(SelectionKey k, int ist)
    {
        return (k.readyOps() & ist) != 0;
    }

    /**
     * Returns the |-ing of a set of {@link SelectionKey} interest mask ints
     * @important also asserts that you've passed in valid selection key interests
     *
     * @param ists varargs list of interest-mask ints
     * @return computed interest mask as int
     */
    public static int getInterestMask(int... ists)
    {
        int istmask = 0;
        for (int ist : ists) {
            assertValidInterest(ist);
            istmask |= ist;
        }
        return istmask;
    }

    /**
     * Assert that the supplied interest integer is a valid {@link SelectionKey}
     * interest (OP_READ, OP_WRITE, OP_ACCEPT, OP_CONNECT)
     *
     * @param ist interest to check for validity
     */
    private static void assertValidInterest(int ist)
    {
        assert ist == SelectionKey.OP_ACCEPT ||
               ist == SelectionKey.OP_CONNECT ||
               ist == SelectionKey.OP_READ ||
               ist == SelectionKey.OP_WRITE : (ist + ":invalid");
    }

    /**
     * Return a string describing the interest mask for this key
     *
     * @return the interest mask for the {@link SelectionKey} in the form:
     * <code>a:{0|1} c:{0|1} s:{0|1} r:{0|1}</code>; returns <code>inv</code>
     * if the key is invalid
     */
    public static String istdesc(SelectionKey k)
    {
        if (!k.isValid()) return "inv";

        return "a:" +   booleanAsInt(hasInterest(k, SelectionKey.OP_ACCEPT)) +
                " c:" + booleanAsInt(hasInterest(k, SelectionKey.OP_CONNECT)) +
                " s:" + booleanAsInt(hasInterest(k, SelectionKey.OP_WRITE)) +
                " r:" + booleanAsInt(hasInterest(k, SelectionKey.OP_READ));
    }

    /**
     * Return a string describing the readiness mask for this key
     *
     * @return the interest mask for the {@link SelectionKey} in the form:
     * <code>a:{0|1} c:{0|1} s:{0|1} r:{0|1}</code>; returns <code>inv</code>
     * if the key is invalid
     */
    public static String rdydesc(SelectionKey k)
    {
        if (!k.isValid()) return "inv";

        return "a:" +   booleanAsInt(hasReady(k, SelectionKey.OP_ACCEPT)) +
                " c:" + booleanAsInt(hasReady(k, SelectionKey.OP_CONNECT)) +
                " s:" + booleanAsInt(hasReady(k, SelectionKey.OP_WRITE)) +
                " r:" + booleanAsInt(hasReady(k, SelectionKey.OP_READ));
    }

    /**
     * Helper function to return the integer representation of a boolean
     *
     * @param b boolean to return as an int
     * @return integer representation of a boolean (1 = true, 0 = false)
     */
    static int booleanAsInt(boolean b)
    {
        return b ? 1 : 0;
    }

    /** logger */
    private static Logger l = Loggers.getLogger(ZUtil.class);
}
