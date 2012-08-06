/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;

abstract class MockAbstractIdentifiedHandler implements IIdentifiedHandler
{
    private final int _id;

    protected MockAbstractIdentifiedHandler(int id)
    {
        this._id = id;
    }

    @Override
    public int getId_()
    {
        return _id;
    }

    protected void addToTrippedList_(IPipelineEvent<?> event)
    {
        if (event instanceof TrippedHandlerEvent) {
            TrippedHandlerEvent tripped = (TrippedHandlerEvent) event;
            tripped.addTrippedHandlerId_(_id);
        }
    }

    protected void addToTrippedListAndSendIncoming_(IPipelineContext ctx, IPipelineEvent<?> event)
    {
        addToTrippedList_(event);
        ctx.sendIncomingEvent_(event);
    }

    protected void addToTrippedListAndSendOutgoing_(IPipelineContext ctx, IPipelineEvent<?> event)
    {
        addToTrippedList_(event);
        ctx.sendOutgoingEvent_(event);
    }
}
