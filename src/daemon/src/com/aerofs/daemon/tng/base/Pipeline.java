/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipelineBuilder;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEventSink;
import com.aerofs.daemon.tng.base.pipeline.IStateContainer;
import com.aerofs.lib.id.DID;

public abstract class Pipeline
{
    private Pipeline()
    {}

    static IPipelineBuilder builder_(DID did, IStateContainer stateContainer,
            IConnection unicastConnection, IPipelineEventSink outgoingEventSink)
    {
        return SimplePipeline.builder_(did, stateContainer, unicastConnection, outgoingEventSink);
    }
}
