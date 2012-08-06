/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.base.pipeline.IIncomingPipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;

class MockIdentifiedForwardingIncomingHandler extends MockAbstractIdentifiedHandler
        implements IIncomingPipelineEventHandler
{
    MockIdentifiedForwardingIncomingHandler(int id)
    {
        super(id);
    }

    @Override
    public void onIncoming_(IPipelineContext ctx, IPipelineEvent<?> event)
            throws Exception
    {
        addToTrippedListAndSendIncoming_(ctx, event);
    }
}
