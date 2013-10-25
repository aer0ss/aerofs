package com.aerofs.restless.stream;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Jersey's StreamingOutput interface sounds nice in theory but was designed with
 * blocking I/O in mind so it does not play well with Netty's asynchronous model
 *
 * This minimal interface makes it possible to stream replies of arbitrary size.
 */
public interface ContentStream
{
    public boolean hasMoreChunk() throws IOException;

    public void writeChunk(OutputStream o) throws IOException;

    public void close() throws IOException;
}

