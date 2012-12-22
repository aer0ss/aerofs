/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IIncomingPipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IOutgoingPipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IPipeline;
import com.aerofs.daemon.tng.base.pipeline.IPipelineBuilder;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEventSink;
import com.aerofs.daemon.tng.base.pipeline.IStateContainer;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.LinkedList;

final class SimplePipeline implements IPipeline
{
    private final DID _did;
    private final IStateContainer _stateContainer;
    private final IConnection _connection;
    private final IPipelineEventSink _outgoingEventSink;
    private final Context _head;
    private final Context _tail;

    static IPipelineBuilder builder_(DID did, IStateContainer stateContainer,
            IConnection unicastConnection, IPipelineEventSink outgoingEventSink)
    {
        return new Builder(did, stateContainer, unicastConnection, outgoingEventSink);
    }

    private SimplePipeline(Builder builder)
    {
        this._did = builder._did;
        this._stateContainer = builder._stateContainer;
        this._connection = builder._connection;
        this._outgoingEventSink = builder._outgoingEventSink;

        if (!builder._handlers.isEmpty()) {
            Context curr, prev = null;

            Iterator<IPipelineEventHandler> it = builder._handlers.iterator();
            _head = prev = new Context(prev, it.next());

            while (it.hasNext()) {
                curr = new Context(prev, it.next());
                prev.setNext_(curr);
                prev = curr;
            }

            _tail = prev;
        } else {
            _head = _tail = null;
        }
    }

    /**
     * <strong>IMPORTANT:</strong> For testing only!
     *
     * @return a list of handlers in forward (incoming processing) order
     */
    ImmutableList<IPipelineEventHandler> getHandlersInForwardOrder_()
    {
        ImmutableList.Builder<IPipelineEventHandler> forwardHandlers = ImmutableList.builder();

        Context ctx = _head;
        while (ctx != null) {
            forwardHandlers.add(ctx._handler);
            ctx = ctx._next;
        }

        return forwardHandlers.build();
    }

    /**
     * <strong>IMPORTANT:</strong> For testing only! I have this version of the method because
     * internally the handlers are stored in a doubly-linked list. I need to verify that the reverse
     * links have been created correctly, so I traverse my internal list in reverse to build the
     * handler list.
     *
     * @return a list of handlers in reverse (outgoing processing) order
     */
    ImmutableList<IPipelineEventHandler> getHandlersInReverseOrder_()
    {
        ImmutableList.Builder<IPipelineEventHandler> reverseHandlers = ImmutableList.builder();

        Context ctx = _tail;
        while (ctx != null) {
            reverseHandlers.add(ctx._handler);
            ctx = ctx._prev;
        }

        return reverseHandlers.build();
    }

    @Override
    public void processIncoming_(IPipelineEvent<?> event)
    {
        processIncoming_(_head, event);
    }

    @Override
    public void processOutgoing_(IPipelineEvent<?> event)
    {
        processOutgoing_(_tail, event);
    }

    private void processIncoming_(Context ctx, IPipelineEvent<?> event)
    {
        if (ctx == null) {
            event.getCompletionFuture_().setException(new ExTransport("unprocessed:" + event));
            return;
        }

        Context actual = findContextWithIncomingHandler_(ctx);
        handleIncomingEvent_(actual, event);
    }

    private void processOutgoing_(Context ctx, IPipelineEvent<?> event)
    {
        if (ctx == null) {
            _outgoingEventSink.processSunkEvent_(event);
            return;
        }

        Context actual = findContextWithOutgoingHandler_(ctx);
        handleOutgoingEvent_(actual, event);
    }

    private Context findContextWithIncomingHandler_(Context ctx)
    {
        assert ctx != null;

        do {
            if (ctx._canHandleIncomingEvent) break;
        } while ((ctx = ctx._next) != null);

        return ctx;
    }

    private Context findContextWithOutgoingHandler_(Context ctx)
    {
        assert ctx != null;

        do {
            if (ctx._canHandleOutgoingEvent) break;
        } while ((ctx = ctx._prev) != null);

        return ctx;
    }

    private void handleIncomingEvent_(Context ctx, IPipelineEvent<?> event)
    {
        assert ctx._canHandleIncomingEvent;

        try {
            IIncomingPipelineEventHandler handler = (IIncomingPipelineEventHandler) ctx._handler;
            handler.onIncoming_(ctx, event);
        } catch (Exception e) {
            handleHandlerException_(event, e);
        }
    }

    private void handleOutgoingEvent_(Context ctx, IPipelineEvent<?> event)
    {
        assert ctx._canHandleOutgoingEvent;

        try {
            IOutgoingPipelineEventHandler handler = (IOutgoingPipelineEventHandler) ctx._handler;
            handler.onOutgoing_(ctx, event);
        } catch (Exception e) {
            handleHandlerException_(event, e);
        }
    }

    private void handleHandlerException_(IPipelineEvent<?> event, Exception e)
    {
        _outgoingEventSink.processSunkEvent_(ExceptionEvent.getInstance_(event, e));
    }

    //
    // Context
    //

    // IMPORTANT: keep Context very simple and delegate to SimplePipeline to ease testing
    private final class Context implements IPipelineContext
    {
        private final Context _prev;
        private final IPipelineEventHandler _handler;
        private final boolean _canHandleIncomingEvent;
        private final boolean _canHandleOutgoingEvent;

        @Nullable private Context _next = null;

        private Context(Context prev, IPipelineEventHandler currentHandler)
        {
            this._prev = prev;
            this._handler = currentHandler;
            this._canHandleIncomingEvent = (currentHandler instanceof IIncomingPipelineEventHandler);
            this._canHandleOutgoingEvent = (currentHandler instanceof IOutgoingPipelineEventHandler);
        }

        void setNext_(Context next)
        {
            assert _next == null;
            _next = next;
        }

        @Override
        public void sendIncomingEvent_(IPipelineEvent<?> event)
        {
            SimplePipeline.this.processIncoming_(_next, event);
        }

        @Override
        public void sendOutgoingEvent_(IPipelineEvent<?> event)
        {
            SimplePipeline.this.processOutgoing_(_prev, event);
        }

        @Override
        public IConnection getConnection_()
        {
            return _connection;
        }

        @Override
        public DID getDID_()
        {
            return _did;
        }

        @Override
        public IStateContainer getStateContainer_()
        {
            return _stateContainer;
        }
    }

    //
    // Builder
    //

    private static final class Builder implements IPipelineBuilder
    {
        private final DID _did;
        private final IStateContainer _stateContainer;
        private final IConnection _connection;
        private final IPipelineEventSink _outgoingEventSink;
        private final LinkedList<IPipelineEventHandler> _handlers = new LinkedList<IPipelineEventHandler>();

        public Builder(DID did, IStateContainer stateContainer, IConnection connection,
                IPipelineEventSink outgoingEventSink)
        {
            this._did = did;
            this._stateContainer = stateContainer;
            this._connection = connection;
            this._outgoingEventSink = outgoingEventSink;
        }

        private void checkPreconditions(IPipelineEventHandler handler)
        {
            if (_handlers.contains(handler)) {
                throw new IllegalArgumentException("Handler already exists in pipeline builder");
            }
        }

        @Override
        public IPipelineBuilder addFirst_(IPipelineEventHandler handler)
        {
            checkPreconditions(handler);

            _handlers.addFirst(handler);
            return this;
        }

        @Override
        public IPipelineBuilder addLast_(IPipelineEventHandler handler)
        {
            checkPreconditions(handler);

            _handlers.addLast(handler);
            return this;
        }

        @Override
        public IPipeline build_()
        {
            return new SimplePipeline(this);
        }
    }
}
