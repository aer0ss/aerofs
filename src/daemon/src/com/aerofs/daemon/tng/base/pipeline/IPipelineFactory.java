/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pipeline;

import com.aerofs.daemon.tng.base.IPeer;

public interface IPipelineFactory
{
    IPipeline getPipeline_(IPeer peer, IConnection connection,
            IPipelineEventSink outgoingEventSink);
}
