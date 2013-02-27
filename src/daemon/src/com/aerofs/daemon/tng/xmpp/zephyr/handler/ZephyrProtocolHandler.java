/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr.handler;

import com.aerofs.base.net.ZephyrConstants;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.daemon.tng.xmpp.zephyr.IZephyrUnicastEventSink;
import com.aerofs.daemon.tng.xmpp.zephyr.exception.ExInvalidZephyrId;
import com.aerofs.daemon.tng.xmpp.zephyr.exception.ExInvalidZephyrMessage;
import com.aerofs.daemon.tng.xmpp.zephyr.exception.ExInvalidZephyrState;
import com.aerofs.daemon.tng.xmpp.zephyr.exception.ExZephyrFailedToBind;
import com.aerofs.daemon.tng.xmpp.zephyr.exception.ExZephyrFatal;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrBindRequest;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrDataMessage;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrRegistrationMessage;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.base.async.FailedFutureCallback;
import com.aerofs.base.async.FutureUtil;
import com.aerofs.base.async.UncancellableFuture;
import org.slf4j.Logger;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

import java.net.SocketAddress;

public class ZephyrProtocolHandler extends SimpleChannelHandler
{
    private static final Logger l = Util.l(ZephyrProtocolHandler.class);

    private enum State
    {
        NOT_STARTED,
        REGISTERING,
        REGISTERED,
        BINDING,
        BOUND,
        DEAD
    }

    // Services
    private final IZephyrUnicastEventSink _sink;
    private final UncancellableFuture<Void> _closeFuture;

    // State-full data (Both of these members must be accessed
    // in a thread-safe manner)
    private State _state = State.NOT_STARTED;
    private ChannelFuture _connectFuture = null;

    public ZephyrProtocolHandler(IZephyrUnicastEventSink sink,
            UncancellableFuture<Void> closeFuture)
    {
        _sink = sink;
        _closeFuture = closeFuture;
    }

    @Override
    public void connectRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        synchronized (this) {
            assert _state == State.NOT_STARTED;

            // Store this future and replace with our own. This is so that
            // no one downstream sets the future, indicating successful connection
            // when our protocol hasn't completed.
            _connectFuture = e.getFuture();

            // When an exception occurs, and the closeFuture is set, the connectFuture should reflect the
            // same exception
            FutureUtil.addCallback(_closeFuture, new FailedFutureCallback()
            {

                @Override
                public void onFailure(Throwable throwable)
                {
                    _connectFuture.setFailure(throwable);
                }

            });
        }

        l.info("connect requested");

        // Propagate the event with the new future
        ChannelFuture future = Channels.future(e.getChannel(), true);
        Channels.connect(ctx, future, (SocketAddress) e.getValue());
    }

    @Override
    public synchronized void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        assert _connectFuture != null;
        assert _state == State.NOT_STARTED;

        // The protocol may begin
        _state = State.REGISTERING;

        // The Zephyr relay will send us data first so we must wait for a
        // messageReceived event
        l.info("channel connected");
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        Object message = e.getMessage();

        if (message instanceof ZephyrDataMessage) {
            clientMessageReceived(ctx, e, (ZephyrDataMessage) message);
        } else if (message instanceof ZephyrRegistrationMessage) {
            serverMessageReceived(ctx, e, (ZephyrRegistrationMessage) message);
        } else {
            throw new ExInvalidZephyrMessage("Expecting an IZephyrMessage");
        }
    }

    /**
     * Processes client messages received via the Zephyr relay. These messages are only valid if the
     * channel has been BOUND to Zephyr
     *
     * @param ctx The {@link ChannelHandlerContext} associated with this channel
     * @param e The {@link MessageEvent} received
     * @param message The {@link ZephyrDataMessage} received
     */
    private synchronized void clientMessageReceived(ChannelHandlerContext ctx, MessageEvent e,
            ZephyrDataMessage message)
    {
        switch (_state) {
        case BOUND:
            // The channel is in a legal state for accepting incoming data
            _sink.onDataReceivedFromChannel_(message.payload);
            break;

        default:
            l.warn("receiving data in unbound state");
            break;
        }
    }

    /**
     * Processes server messages received from the Zephyr relay. These messages are only valid if
     * the channel is REGISTERING with the Zephyr relay.
     *
     * @param ctx The {@link ChannelHandlerContext} associated with this channel
     * @param e The {@link MessageEvent} received
     * @param message The {@link ZephyrRegistrationMessage} received
     */
    private synchronized void serverMessageReceived(ChannelHandlerContext ctx, MessageEvent e,
            ZephyrRegistrationMessage message)
            throws Exception
    {
        switch (_state) {
        case REGISTERING: {
            if (message.zid == ZephyrConstants.ZEPHYR_INVALID_CHAN_ID || message.zid < 0) {
                throw new ExInvalidZephyrId(message.zid);
            }

            l.info("reg message received");

            _state = State.REGISTERED;

            // Make sure we stop reading from the channel, since we need to wait for permission
            Channels.setInterestOps(e.getChannel(), Channel.OP_WRITE);

            _sink.onChannelRegisteredWithZephyr_(message.zid);
        }
        break;

        default:
            // We only get registration data from the server, so this
            // must be a double registration, which is illegal
            assert false;
            break;
        }
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (e.getMessage() instanceof ZephyrBindRequest) {
            bindWithZephyrRequested(ctx, e, (ZephyrBindRequest) e.getMessage());
        } else {
            synchronized (this) {
                if (_state != State.BOUND) {
                    throw new ExInvalidZephyrState(_state, "Can not send data on un-BOUND channel");
                }
            }

            super.writeRequested(ctx, e);
        }
    }

    /**
     * Processes a bind request to Zephyr. Only valid if the channel is REGISTERED.
     *
     * @param ctx The {@link ChannelHandlerContext} associated with this channel
     * @param e The {@link MessageEvent} received
     * @param bindRequest The {@link ZephyrBindRequest} received
     */
    private void bindWithZephyrRequested(ChannelHandlerContext ctx, MessageEvent e,
            ZephyrBindRequest bindRequest)
            throws Exception
    {
        synchronized (this) {
            switch (_state) {
            case REGISTERED:
                _state = State.BINDING;
                break;

            default:
                throw new ExInvalidZephyrState(_state, "Binding in incorrect state");
            }
        }

        // This is all processed out here so that we don't hold the monitor
        // lock on this class while processing downstream

        // Check the request for validity
        if (bindRequest.remoteZid == ZephyrConstants.ZEPHYR_INVALID_CHAN_ID ||
                bindRequest.remoteZid < 0) {
            throw new ExInvalidZephyrId(bindRequest.remoteZid);
        }

        l.info("binding");

        // Attach a listener
        e.getFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception
            {
                if (!future.isSuccess()) {
                    Channels.fireExceptionCaught(future.getChannel(), new ExZephyrFailedToBind());
                    return;
                }

                l.info("bind succeeded");
                synchronized (ZephyrProtocolHandler.this) {
                    assert _state == State.BINDING;
                    _state = State.BOUND;

                    assert _connectFuture != null;
                    _connectFuture.setSuccess();
                }
            }
        });

        // Send the request
        Channels.write(ctx, e.getFuture(), bindRequest);
    }

    @Override
    public synchronized void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        l.info("Channel disconnected! Uhoh");

        // Normally, to set the close future with an exception, the ZephyrUnicastConnection
        // will fire an exception event. In the case where close() is called on the channel,
        // we need SOME exception to set, so use a generic ExTransport exception
        if (!_closeFuture.isDone()) {
            _closeFuture.setException(new ExTransport("User manually closed the connection"));
        }

        assert _state != State.DEAD;
        _state = State.DEAD;
    }

    @Override
    public synchronized void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception
    {
        l.info("Exception caught!!! " + e.getCause());

        if (e.getCause() instanceof ExZephyrFatal) {
            // This is an error generated by Zephyr logic. It is likely a logic
            // error from the server and thus we should assert
            SystemUtil.fatal(e.getCause());
        }

        _closeFuture.setException(e.getCause());

        Channels.close(ctx, e.getChannel().getCloseFuture());
    }

}
