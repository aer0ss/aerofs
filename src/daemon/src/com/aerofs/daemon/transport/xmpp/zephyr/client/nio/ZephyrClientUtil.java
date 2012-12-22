/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio;

import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.base.id.DID;
import com.aerofs.zephyr.core.BufferPool;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

/**
 * Utility functions for {@link ZephyrClientState} and {@link com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientContext}
 * objects.
 *
 * @important All functions in this class should be static and package-local, and
 * this class should never have state.
 */
class ZephyrClientUtil
{
    /**
     * Use to handle any errors encountered while sending a packet via Zephyr
     * to a remote ZephyrClient.
     *
     * @param d {@link DID} of the remote ZephyrClient for which the error was
     * generated
     * @param w {@link IResultWaiter} to be notified of the error
     * @param e Exception to deliver to the IResultWaiter (cannot be null)
     * @param l {@link Logger} to use to print messages
     *
     * FIXME: should I also allow a logmsgprefix here?
     */
    static void handleError(DID d, IResultWaiter w, Exception e, Logger l)
    {
        l.warn("err for did:" + d + " err:" + e);
        if (w == null) return;

        assert e != null : ("null exception");
        w.error(e);
    }

    /**
     * Prints the number of remaining bytes in an array of {@link ByteBuffer}
     * objects. Useful when you are doing scatter-gather IO via
     * {@link java.nio.channels.SelectableChannel} objects.
     *
     * @param bufs         Array of ByteBuffer objects into which data is being read
     * or from which data is being written
     * @param logmsgprefix Prefix for all log-messages
     * (usually {@link com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientContext}.toString())
     * @param l            {@link Logger} to use to print messages
     */
    static void logRemaining(ByteBuffer[] bufs, String logmsgprefix, Logger l)
    {
        for (int i = 0; i < bufs.length; i++) {
            if (logmsgprefix != null) {
                l.debug(logmsgprefix + "wb[" + i + "] rem:" + bufs[i].remaining());
            } else {
                l.debug("wb[" + i + "] rem:" + bufs[i].remaining());
            }
        }
    }

    /**
     * Creates a {@link ByteArrayInputStream} from which a caller can read the
     * data in the passed-in {@link ByteBuffer} objects
     *
     * @param bufs ByteBuffer objects with the data you want available in the
     * ByteArrayInputStream
     * @important expects that ByteBuffer objects passed in are ready for reads
     * (i.e. they are flipped already)
     */
    static ByteArrayInputStream createByteArrayInputStream(ByteBuffer[] bufs)
    {
        int totalbytes = 0;
        for (ByteBuffer buf : bufs) totalbytes += buf.remaining();

        byte[] baisbuf = new byte[totalbytes];
        int offset = 0;
        int tocopy = 0;
        for (ByteBuffer buf : bufs) {
            tocopy = buf.remaining();
            buf.get(baisbuf, offset, tocopy);
            offset += tocopy;
        }

        return new ByteArrayInputStream(baisbuf);
    }

    /**
     * Returns the number of {@link BufferPool}-supplied {@link ByteBuffer}
     * objects you would need to store a bytestring
     *
     * @param buflen  Number of bytes you want to store
     * @param bufpool BufferPool from which the ByteBuffer objects will be
     * retrieved
     *
     * @return number of required ByteBuffer objects
     */
    static int getNumRequiredBuffers(int buflen, BufferPool bufpool)
    {
        return (int) Math.ceil(buflen / (double) bufpool.getBufferSize());
    }

    /**
     * Given that you want to store a buffer of length l, retrieves
     * the as many {@link ByteBuffer} objects from the {@link BufferPool} as
     * necessary, clear()s them, sets limit() on them as necessary, etc. This
     * allows you to read/write <em>exactly</em> l bytes via a
     * {@link java.nio.channels.SelectableChannel}
     *
     * @param boss   {@link com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientManager} that owns the BufferPool
     * @param buflen number of bytes you want your ByteBuffer objects to hold
     * @return the setup ByteBuffer array
     */
    static ByteBuffer[] setupBufferArray(ZephyrClientManager boss, int buflen)
    {
        // get the number of buffers we need
        BufferPool bufpool = boss.getBufferPool();
        int numbufs = getNumRequiredBuffers(buflen, bufpool);
        assert numbufs > 0 : ("invalid numbufs for buflen:" + buflen);

        // create and populate the ByteBuffer array
        ByteBuffer[] bufs = new ByteBuffer[numbufs];
        for (int i = 0; i < numbufs; i++) bufs[i] = bufpool.getBuffer_();

        // set the limit for the last ByteBuffer to be right after the last valid byte
        int rembytes = buflen % bufpool.getBufferSize();
        if (rembytes > 0) bufs[numbufs - 1].limit(rembytes);

        return bufs;
    }

    /**
     * Copies the contents of an array of byte arrays to an array of ByteBuffer
     * objects
     * @important all {@link ByteBuffer} objects should be setup before being passed
     * in as a parameter
     * @param bss  array of byte-arrays with the data you want to copy
     * @param bufs ByteBuffer array into which to copy the data
     * @return true if all bytes could be copied, false otherwise
     *
     * FIXME: not checked
     */
    static boolean copyByteArraysToByteBuffers(byte[][] bss, ByteBuffer[] bufs)
    {
        boolean allcopied = true;
        int bssidx = 0;
        int bufidx = 0;
        int offset = 0;
        int copied = 0;
        ByteBuffer buf = bufs[bufidx];
        byte[] bs = bss[bssidx];
        do {
            offset = 0;
            while (offset < bs.length) {
                if (!buf.hasRemaining()) {
                    buf.flip(); // now make ready for reading
                    if (++bufidx == bufs.length) {
                        allcopied = false;
                        break;
                    }
                    buf = bufs[bufidx];
                }

                copied = bs.length < buf.remaining() ? bs.length : buf.remaining();
                buf.put(bs, offset, copied);
                offset+= copied;
            }

            ++bssidx;
        } while ((bssidx < bss.length) && allcopied);

        return allcopied;
    }
}
