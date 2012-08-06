/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.google.common.util.concurrent.ListenableFuture;

public interface IOutgoingStream extends IStream
{
    /**
     * Send bytes to the peer via this stream
     *
     * @param payload bytes to send
     * @return future indicating whether bytes were sent successfully or not
     */
    ListenableFuture<Void> send_(byte[] payload);
}
