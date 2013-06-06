/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.lib;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Implemented by classes that want to track basic networking statistics,
 * including (but not limited to), bytes transmitted and received, etc.
 */
public interface ITransportStats
{
    /**
     * Increment the number of bytes received
     *
     * @param count additional bytes received
     */
    public void addBytesReceived(long count);

    /**
     * @return number of bytes received so far
     */
    public long getBytesReceived();

    /**
     * Increment the number of bytes transmitted
     *
     * @param count additional bytes transmitted
     */
    public void addBytesSent(long count);

    /**
     * @return number of bytes transmitted so far
     */
    public long getBytesSent();

    /**
     * Convenience class that implements {@link ITransportStats}. It does no
     * error checking (i.e. <code>bytesrx</code> and <code>bytestx</code> can
     * be negative) and is not thread-safe. It can however, be used in a
     * multi-threaded context if precision is unnecessary.
     */
    public static class BasicStatsCounter implements ITransportStats
    {
        private AtomicLong bytesSent = new AtomicLong(0);
        private AtomicLong bytesReceived = new AtomicLong(0);

        @Override
        public void addBytesReceived(long count)
        {
            bytesSent.addAndGet(count);
        }

        @Override
        public long getBytesReceived()
        {
            return bytesSent.get();
        }

        @Override
        public void addBytesSent(long count)
        {
            bytesReceived.addAndGet(count);
        }

        @Override
        public long getBytesSent()
        {
            return bytesReceived.get();
        }
    }
}
