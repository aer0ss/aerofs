/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pipeline;

public interface IPipeline
{
    void processIncoming_(IPipelineEvent<?> event);

    void processOutgoing_(IPipelineEvent<?> event);
}