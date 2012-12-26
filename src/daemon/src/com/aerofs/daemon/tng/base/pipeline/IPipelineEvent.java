/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pipeline;

import com.aerofs.base.async.UncancellableFuture;

// FIXME: strongly consider differentiation between incoming and outgoing events
// I've noticed in my testing that it's very, very easy to accidentally call processIncoming_ or
// sendIncomingEvent_ on an _outgoing_ event
public interface IPipelineEvent<FutureReturn>
{
    UncancellableFuture<FutureReturn> getCompletionFuture_();

    IConnection getConnection_();
}