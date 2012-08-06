/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pipeline;

public interface IPipelineEventSink
{
    void processSunkEvent_(IPipelineEvent<?> event);
}
