package com.aerofs.overload;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Simple HTTP client that can make requests over
 * multiple connections to a single HTTP server.
 */
final class PipelinedHttpClient {

    private static final int MAX_CONTENT_LENGTH = 2 * 1024 * 1024; // 2 MB
    private static final long INTRA_CONNECT_DELAY = 1;
    private static final TimeUnit CONNECT_DELAY_TIMEUNIT = TimeUnit.SECONDS;

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelinedHttpClient.class);

    private final Set<Channel> availables = Sets.newHashSet();
    private final Set<Channel> connecting = Sets.newHashSet();
    private final Random random = new Random();
    private final Timer timer = new HashedWheelTimer();
    private final ByteBufAllocator allocator = new UnpooledByteBufAllocator(false);
    private final EventLoopGroup clientGroupLoop = new NioEventLoopGroup(2);
    private final String host;
    private final int port;
    private final Bootstrap bootstrap;
    private final int maxConcurrentConnections;

    PipelinedHttpClient(String host, int port, int connectTimeout, int maxConcurrentConnections) {
        this.host = host;
        this.port = port;
        this.maxConcurrentConnections = maxConcurrentConnections;

        this.bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.ALLOCATOR, allocator);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
        bootstrap.group(clientGroupLoop);
        bootstrap.handler(new io.netty.channel.ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(new HttpClientCodec());
                channel.pipeline().addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                channel.pipeline().addLast(new SubmittedRequestHandler());
            }
        });
    }

    ByteBufAllocator getAllocator() {
        return allocator;
    }

    void start() throws Exception {
        LOGGER.info("initiating {} connections to {}:{}", maxConcurrentConnections, host, port);

        final Semaphore atLeastOneConnectedSemaphore = new Semaphore(0);

        // don't synchronize the entire method
        // if you do so will hold the lock while waiting
        // to acquire the semaphore, which will lead
        // to a deadlock
        synchronized (this) {
            for (int i = 0; i < maxConcurrentConnections; i++) {
                delayedConnect(INTRA_CONNECT_DELAY * i, CONNECT_DELAY_TIMEUNIT, new GenericFutureListener<Future<Void>>() {

                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        if (future.isSuccess()) {
                            atLeastOneConnectedSemaphore.release();
                        }
                    }
                });
            }
        }

        atLeastOneConnectedSemaphore.acquire();
    }

    synchronized void shutdown() {
        LOGGER.info("shutting down");

        Set<Channel> dying = ImmutableSet.copyOf(availables);
        for (Channel channel : dying) {
            channel.close();
        }

        clientGroupLoop.shutdownGracefully();
    }

    synchronized boolean submit(FullHttpRequest request, @Nullable SubmittedRequestCallback callback) {
        boolean submitted = false;

        try {
            Channel chosen = chooseRandomChannel();
            if (chosen != null) {
                chosen.write(new Submitted(request, callback));
                submitted = true;
            } else {
                if ((availables.size() + connecting.size()) < maxConcurrentConnections) {
                    connect();
                }
            }
        } catch (Exception e) {
            request.content().release();
        }

        return submitted;
    }

    @Nullable
    private Channel chooseRandomChannel() {
        if (availables.size() == 0) {
            return null;
        }

        int iterations = random.nextInt(availables.size());

        Channel chosen = null;
        int count = 0;
        for (Channel channel : availables) {
            chosen = channel;
            count++;
            if (count == iterations) {
                break;
            }
        }

        return chosen;
    }

    private void delayedConnect(long delay, TimeUnit delayTimeUnit, @Nullable final GenericFutureListener<Future<Void>> connectFutureListener) {
        timer.newTimeout(new TimerTask() {

            @Override
            public void run(Timeout timeout) throws Exception {
                ChannelFuture connectFuture = connect();

                if (connectFutureListener != null) {
                    connectFuture.addListener(connectFutureListener);
                }
            }
        }, delay, delayTimeUnit);
    }

    private ChannelFuture connect() {
        ChannelFuture future = bootstrap.connect(host, port);
        final Channel channel = future.channel();

        connecting.add(channel);

        channel.closeFuture().addListener(new GenericFutureListener<Future<Void>>() {

            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                synchronized (PipelinedHttpClient.this) {
                    connecting.remove(channel);
                    availables.remove(channel);
                }

                delayedConnect(INTRA_CONNECT_DELAY, CONNECT_DELAY_TIMEUNIT, null);
            }
        });

        future.addListener(new GenericFutureListener<Future<Void>>() {

            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (future.isSuccess()) {
                    synchronized (PipelinedHttpClient.this) {
                        connecting.remove(channel);
                        availables.add(channel);
                    }
                }
            }
        });

        return future;
    }
}
