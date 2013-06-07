/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.base.id.DID;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.util.concurrent.ListenableFuture;

public interface IStream
{
    StreamID getStreamId_();

    DID getDid_();

    Prio getPriority_();

    /**
     * Returns the future that is set when the IOutgoingStream is closed. This method must return
     * the same future each method invocation
     *
     * @return The future that is set when the IOutgoingStream is closed
     */
    ListenableFuture<Void> getCloseFuture_();

    /**
     * Abort the stream
     *
     * @return The same future returned from {@link IStream#getCloseFuture_()}
     */
    ListenableFuture<Void> abort_(InvalidationReason reason);

    /**
     * Ends the stream
     *
     * @return The same future returned from {@link IStream#getCloseFuture_()}
     */
    ListenableFuture<Void> end_();
}