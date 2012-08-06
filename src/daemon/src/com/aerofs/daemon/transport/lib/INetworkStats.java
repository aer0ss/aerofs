/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.lib;

/**
 * Implemented by classes that want to track basic networking statistics,
 * including (but not limited to), bytes transmitted and received, etc.
 */
public interface INetworkStats
{
    /**
     * Increment the number of bytes received
     *
     * @param bytesrx additional bytes received
     */
    public void addBytesRx(long bytesrx);

    /**
     * @return number of bytes received so far
     */
    public long getBytesRx();

    /**
     * Increment the number of bytes transmitted
     *
     * @param bytestx additional bytes transmitted
     */
    public void addBytesTx(long bytestx);

    /**
     * @return number of bytes transmitted so far
     */
    public long getBytesTx();

    /**
     * Convenience class that implements {@link INetworkStats}. It does no
     * error checking (i.e. <code>bytesrx</code> and <code>bytestx</code> can
     * be negative) and is not thread-safe. It can however, be used in a
     * multi-threaded context if precision is unnecessary.
     */
    public static class BasicStatsCounter implements INetworkStats
    {
        @Override
        public void addBytesRx(long bytesrx)
        {
            _bytesrx += bytesrx;
        }

        @Override
        public long getBytesRx()
        {
            return _bytesrx;
        }

        @Override
        public void addBytesTx(long bytestx)
        {
            _bytestx += bytestx;
        }

        @Override
        public long getBytesTx()
        {
            return _bytestx;
        }

        private long _bytesrx;
        private long _bytestx;
    }
}
