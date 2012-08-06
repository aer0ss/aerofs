/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pipeline;

public interface IOutgoingPipelineEventHandler extends IPipelineEventHandler
{
    void onOutgoing_(IPipelineContext ctx, IPipelineEvent<?> event)
            throws Exception;
}