/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap.filter;

import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.base.MessageEvent;
import com.aerofs.daemon.tng.base.SimplePipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.base.async.FutureUtil;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.lib.notifier.IListenerVisitor;
import com.aerofs.lib.notifier.SingleListenerNotifier;
import com.google.common.util.concurrent.FutureCallback;

import java.util.concurrent.Executor;

/**
 * Takes all incoming and outgoing MessageEvents and enqueues them onto a queue for permission. If
 * granted permission, the messages are forwarded into the pipeline. This is a handler meant simply
 * for testing with TAP
 */
public class MessageFilter extends SimplePipelineEventHandler
{
    private final ISingleThreadedPrioritizedExecutor _executor;
    private final SingleListenerNotifier<IMessageFilterListener> _notifier = SingleListenerNotifier.create();

    public MessageFilter(ISingleThreadedPrioritizedExecutor executor,
            IMessageFilterListener listener, Executor callbackExecutor)
    {
        _executor = executor;
        _notifier.setListener(listener, callbackExecutor);
    }

    @Override
    public void onIncomingMessageEvent_(final IPipelineContext ctx, final MessageEvent messageEvent)
            throws Exception
    {
        // Attach a callback that fails the event if the message is not
        // permitted for processing. If the message is permitted, then
        // forward it up the pipeline
        UncancellableFuture<Void> permissionFuture = UncancellableFuture.create();
        FutureUtil.addCallback(permissionFuture, new FutureCallback<Void>()
        {
            @Override
            public void onSuccess(Void aVoid)
            {
                // Send the message out the pipeline
                ctx.sendIncomingEvent_(messageEvent);
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                // Fail the events future
                messageEvent.getCompletionFuture_().setException(throwable);
            }
        }, _executor);

        final MessageFilterRequest request = new MessageFilterRequest(messageEvent.getMessage_(),
                permissionFuture);

        // Notify the listener
        _notifier.notifyOnOtherThreads(new IListenerVisitor<IMessageFilterListener>()
        {
            @Override
            public void visit(IMessageFilterListener listener)
            {
                listener.onIncomingMessageReceived_(request);
            }
        });
    }

    @Override
    public void onOutgoingMessageEvent_(final IPipelineContext ctx, final MessageEvent messageEvent)
            throws Exception
    {
        // Attach a callback that fails the event if the message is not
        // permitted for processing. If the message is permitted, then
        // forward it down the pipeline
        UncancellableFuture<Void> permissionFuture = UncancellableFuture.create();
        FutureUtil.addCallback(permissionFuture, new FutureCallback<Void>()
        {
            @Override
            public void onSuccess(Void aVoid)
            {
                // Send the message out the pipeline
                ctx.sendOutgoingEvent_(messageEvent);
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                // Fail the events future
                messageEvent.getCompletionFuture_().setException(throwable);
            }
        }, _executor);

        final MessageFilterRequest request = new MessageFilterRequest(messageEvent.getMessage_(),
                permissionFuture);

        // Notify the listener
        _notifier.notifyOnOtherThreads(new IListenerVisitor<IMessageFilterListener>()
        {
            @Override
            public void visit(IMessageFilterListener listener)
            {
                listener.onOutgoingMessageReceived_(request);
            }
        });
    }
}
