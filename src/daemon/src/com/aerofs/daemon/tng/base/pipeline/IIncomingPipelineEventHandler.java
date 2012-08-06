/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pipeline;

public interface IIncomingPipelineEventHandler extends IPipelineEventHandler
{
    void onIncoming_(IPipelineContext ctx, IPipelineEvent<?> event)
            throws Exception;
}
