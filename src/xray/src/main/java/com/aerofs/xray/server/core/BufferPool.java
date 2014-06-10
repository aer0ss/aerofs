/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.xray.server.core;

import java.nio.ByteBuffer;

import static java.nio.ByteOrder.BIG_ENDIAN;

/**
 * Implements a pool of {@link ByteBuffer} objects
 *
 * A {@link ByteBuffer} object's equality is determined by the following:
 * <ul>
 *      <li>Whether their types are the same</li>
 *      <li>Whether their capacity, limit, remaining, etc. are the same</li>
 *      <li>Whether they have the exact same remaining elements</li>
 * </ul>
 *
 * Also, .equals() should be consistent with hashcode()
 *
 * FIXME: does a buffer pool actually save us any time? Or are we better off simply doing JVM allocs?
 */
public class BufferPool
{
    /**
     * Constructor
     * @param bufcapacity capacity of each ByteBuffer (all ByteBuffers returned
     * by this pool will have this capacity)
     * @param poolsize maximum size of the buffer pool
     */
    public BufferPool(int bufcapacity, int poolsize)
    {
        assert (bufcapacity > 0 && poolsize > 0) :
            ("invalid construct parameters for BufferPool");

        _bufsize = bufcapacity;
        _buffers = new ByteBuffer[poolsize];
        _bufidx = _buffers.length - 1; // point to last ByteBuffer in array

        for (int i = 0; i < poolsize; ++i) {
            _buffers[i] = ByteBuffer.allocate(bufcapacity);
            _buffers[i].order(BIG_ENDIAN);
        }
    }

    /**
     * Gets a ByteBuffer (will never block - i.e. I will create one for you if the
     * pool is exhausted)
     *
     * @return a valid {@link ByteBuffer} of the default allocation capacity
     */
    public synchronized ByteBuffer getBuffer_()
    {
        ByteBuffer b = (_bufidx < 0 ? ByteBuffer.allocate(_bufsize) : _buffers[_bufidx--]);
        b.clear();
        b.order(BIG_ENDIAN);
        return b;
    }

    /**
     * Return a used {@link ByteBuffer} back into the buffer pool
     * @param b ByteBuffer to return
     *
     * @important I will assert that you are returning ByteBuffer objects of the
     * default allocation capacity only to this pool!
     */
    public synchronized void putBuffer_(ByteBuffer b)
    {
        assert b != null :
            ("attempt to return null buffer to pool");
        assert b.capacity() == _bufsize :
            ("attempt to return non-pool-owned buffer to pool");

        for (int i = 0; i < _bufidx; i++) {
            assert _buffers[i] != b : ("buf alredy exist in set");
        }

        if (_bufidx < (_buffers.length - 1)) _buffers[++_bufidx] = b;
    }

    public int getBufferSize()
    {
        return _bufsize;
    }

    /** size to make each individual buffer */
    private final int _bufsize;

    /** set of buffers available to be used */
    private final ByteBuffer[] _buffers;

    /** index into the _buffers array from which to retrieve (or not) a {@link ByteBuffer} object */
    private int _bufidx;

    /** number of bytebuffers with which to populate the buffer manager */
    public static final int DEFAULT_INITIAL_NUM_BYTEBUFFERS = 64;

    /** default size for each bytebuffer */
    public static final int DEFAULT_BYTEBUFFER_SIZE = 4096;
}
