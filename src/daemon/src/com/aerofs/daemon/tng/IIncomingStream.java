/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.daemon.tng.base.streams.Chunk;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Future;

public interface IIncomingStream extends IStream
{
    /**
     * Read bytes from a stream
     *
     * @return a {@link ListenableFuture} that will be triggered when the next set of bytes is
     *         available for this stream.
     *         <p/>
     *         This future behaves as follows: <ol><li>It will be triggered on each incoming
     *         chunk</li><li>Calling {@link Future#get()} will return a valid (i.e. not null) {@link
     *         Chunk} if the stream is still open. It will return an {@link
     *         com.aerofs.daemon.tng.ex.ExStreamInvalid} with a {@link com.aerofs.proto.Transport.PBStream.InvalidationReason}
     *         if the stream was invalidated either via an Abort or End call. The same applies for
     *         any listeners that were attached.</li><li>Calling {@link IIncomingStream#receive_}
     *         multiple times <em>before</em> it is triggered will return the same {@code
     *         ListenableFuture}. After bytes are received, a subsequent {@link
     *         IIncomingStream#receive_} call will return a new {@code ListenableFuture}.</li>
     *         </ol>
     */
    ListenableFuture<ImmutableList<Chunk>> receive_();
}