/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.base.pipeline.IIncomingPipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IOutgoingPipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;

public class SimplePipelineEventHandler
        implements IIncomingPipelineEventHandler, IOutgoingPipelineEventHandler
{
    @Override
    public final void onIncoming_(IPipelineContext ctx, IPipelineEvent<?> event)
            throws Exception
    {
        if (event instanceof MessageEvent) {
            onIncomingMessageEvent_(ctx, (MessageEvent) event);
        } else {
            ctx.sendIncomingEvent_(event);
        }
    }

    @Override
    public final void onOutgoing_(IPipelineContext ctx, IPipelineEvent<?> event)
            throws Exception
    {
        if (event instanceof MessageEvent) {
            onOutgoingMessageEvent_(ctx, (MessageEvent) event);
        } else if (event instanceof ConnectionEvent) {
            ConnectionEvent connectionEvent = (ConnectionEvent) event;
            if (connectionEvent.getType_() == ConnectionEvent.Type.CONNECT) {
                onConnectEvent_(ctx, connectionEvent);
            } else if (connectionEvent.getType_() == ConnectionEvent.Type.DISCONNECT) {
                onDisconnectEvent_(ctx, connectionEvent);
            } else {
                // Connection event received with invalid type
                assert false : connectionEvent.getType_();
            }
        } else {
            ctx.sendOutgoingEvent_(event);
        }
    }

    protected void onConnectEvent_(IPipelineContext ctx, ConnectionEvent connectEvent)
            throws Exception
    {
        ctx.sendOutgoingEvent_(connectEvent);
    }

    protected void onDisconnectEvent_(IPipelineContext ctx, ConnectionEvent disconnectEvent)
            throws Exception
    {
        ctx.sendOutgoingEvent_(disconnectEvent);
    }

    protected void onOutgoingMessageEvent_(IPipelineContext ctx, MessageEvent messageEvent)
            throws Exception
    {
        ctx.sendOutgoingEvent_(messageEvent);
    }

    protected void onIncomingMessageEvent_(IPipelineContext ctx, MessageEvent messageEvent)
            throws Exception
    {
        ctx.sendIncomingEvent_(messageEvent);
    }

}
