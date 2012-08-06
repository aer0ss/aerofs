/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pipeline;

public interface IPipelineBuilder
{
    IPipelineBuilder addFirst_(IPipelineEventHandler handler);

    IPipelineBuilder addLast_(IPipelineEventHandler handler);

    IPipeline build_();
}
