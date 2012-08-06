/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;

public abstract class Handlers
{
    private Handlers()
    {}

    public static void sendOutgoingMessage_(IPipelineContext ctx, MessageEvent oldEvent,
            Object newMessage)
    {
        ctx.sendOutgoingEvent_(
                new MessageEvent(oldEvent.getConnection_(), oldEvent.getCompletionFuture_(),
                        newMessage, oldEvent.getPriority_()));
    }

    public static void sendIncomingMessage_(IPipelineContext ctx, MessageEvent oldEvent,
            Object newMessage)
    {
        ctx.sendIncomingEvent_(
                new MessageEvent(oldEvent.getConnection_(), oldEvent.getCompletionFuture_(),
                        newMessage, oldEvent.getPriority_()));
    }
}
