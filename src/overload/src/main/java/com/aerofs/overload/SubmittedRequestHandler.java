package com.aerofs.overload;

import com.google.common.collect.Lists;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.List;

final class SubmittedRequestHandler extends ChannelHandlerAdapter implements ChannelInboundHandler, ChannelOutboundHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubmittedRequestHandler.class);
    private static final SubmittedRequestCallback NOOP_COMPLETION_CALLBACK = new SubmittedRequestCallback() {
        @Override
        public void onWriteSucceeded() {
            // noop
        }

        @Override
        public void onResponseReceived(FullHttpResponse response) {
            response.content().release();
        }

        @Override
        public void onFailure(Throwable cause) {
            // noop
        }
    };

    private final List<SubmittedRequestCallback> pending = Lists.newArrayList();

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().closeFuture().addListener(new GenericFutureListener<Future<Void>>() {

            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                LOGGER.info("channel closed - fail pending requests");

                synchronized (SubmittedRequestHandler.this) {
                    for (SubmittedRequestCallback callback : pending) {
                        callback.onFailure(new IOException("channel closed"));
                    }

                    pending.clear();
                }
            }
        });

        ctx.fireChannelRegistered();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        Submitted submitted = (Submitted) msg;

        final FullHttpRequest request = submitted.getRequest();
        final SubmittedRequestCallback callback = (submitted.getCompletionCallback() == null ? NOOP_COMPLETION_CALLBACK : submitted.getCompletionCallback());

        if (ctx.channel().closeFuture().isSuccess()) {
            request.content().release(); // release the buffer explicitly, because we haven't passed ownership on
            callback.onFailure(new IOException("channel closed"));
            return;
        }

        synchronized (this) {
            pending.add(callback);
        }

        final Channel channel = ctx.channel();
        ChannelFuture future = ctx.writeAndFlush(request); // at this point we pass responsibility for the buffer to the HTTP handlers
        future.addListener(new GenericFutureListener<Future<Void>>() {

            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    channel.close();
                    return;
                }

                callback.onWriteSucceeded();
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        SubmittedRequestCallback callback;

        synchronized (this) {
            if (pending.size() != 0) {
                callback = pending.remove(0);
            } else {
                LOGGER.warn("no pending request for incoming response");
                ctx.close();
                return;
            }
        }

        callback.onResponseReceived((FullHttpResponse) msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    //
    // default implementations
    //

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelInactive();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadComplete();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }
}
