/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.base.pipeline.IIncomingPipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IOutgoingPipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;

class MockIdentifiedForwardingDualHandler
        extends MockAbstractIdentifiedHandler implements IIncomingPipelineEventHandler,
        IOutgoingPipelineEventHandler
{
    MockIdentifiedForwardingDualHandler(int id)
    {
        super(id);
    }

    @Override
    public void onIncoming_(IPipelineContext ctx, IPipelineEvent<?> event)
            throws Exception
    {
        addToTrippedListAndSendIncoming_(ctx, event);
    }

    @Override
    public void onOutgoing_(IPipelineContext ctx, IPipelineEvent<?> event)
            throws Exception
    {
        addToTrippedListAndSendOutgoing_(ctx, event);
    }
}