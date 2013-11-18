/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.event.net.tx.EOChunk;

public interface IOutgoingStreamFeedback
{
    void incChunkCount();

    void decChunkCount();

    void setFirstFailedChunk(EOChunk chunk);
}
