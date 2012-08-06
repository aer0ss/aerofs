/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.base.pipeline.IIncomingPipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IOutgoingPipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;

class MockIdentifiedStoppingHandler
        extends MockAbstractIdentifiedHandler implements IIncomingPipelineEventHandler,
        IOutgoingPipelineEventHandler
{
    MockIdentifiedStoppingHandler(int id)
    {
        super(id);
    }

    @Override
    public void onIncoming_(IPipelineContext ctx, IPipelineEvent<?> event)
            throws Exception
    {
        addToTrippedList_(event);
    }

    @Override
    public void onOutgoing_(IPipelineContext ctx, IPipelineEvent<?> event)
            throws Exception
    {
        addToTrippedList_(event);
    }
}