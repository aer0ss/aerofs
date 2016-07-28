/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.http;

import com.aerofs.baseline.Managed;
import com.aerofs.baseline.Threads;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.Timer;
import org.glassfish.jersey.server.ApplicationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.NotThreadSafe;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.netty.channel.ChannelOption.ALLOCATOR;
import static io.netty.channel.ChannelOption.AUTO_READ;
import static io.netty.channel.ChannelOption.SO_BACKLOG;

@NotThreadSafe
public final class HttpServer implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServer.class);

    private final String serverIdentifier;
    private final String host;
    private final short port;
    private final ExecutorService requestProcessingExecutor;
    private final NioEventLoopGroup bossEventLoopGroup;
    private final NioEventLoopGroup workEventLoopGroup;
    private final ServerBootstrap bootstrap;

    private Channel listenChannel;

    public HttpServer(String serverIdentifier, HttpConfiguration http, Timer timer, ApplicationHandler applicationHandler) {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(http.isDirectMemoryBacked());

        this.serverIdentifier = serverIdentifier;
        this.host = http.getHost();
        this.port = http.getPort();
        this.requestProcessingExecutor = Executors.newFixedThreadPool(http.getNumRequestProcessingThreads(), Threads.newNamedThreadFactory(serverIdentifier + "-requests-%d"));
        this.bossEventLoopGroup = new NioEventLoopGroup(com.aerofs.baseline.http.Constants.DEFAULT_NUM_BOSS_THREADS, Threads.newNamedThreadFactory(serverIdentifier + "-nio-boss-%d"));
        this.workEventLoopGroup = new NioEventLoopGroup(http.getNumNetworkThreads(), Threads.newNamedThreadFactory(serverIdentifier + "-nio-work-%d"));
        this.bootstrap = new ServerBootstrap();
        this.bootstrap
                .group(bossEventLoopGroup, workEventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new AcceptedChannelInitializer(http, applicationHandler, URI.create(String.format("http://%s:%s/", host, port)), requestProcessingExecutor, timer))
                .option(ALLOCATOR, allocator)
                .option(SO_BACKLOG, http.getMaxAcceptQueueSize())
                .childOption(AUTO_READ, false)
                .childOption(ALLOCATOR, allocator);
    }

    @Override
    public void start() throws Exception {
        LOGGER.info("bind {}-http to {}:{}", serverIdentifier, host, port);
        listenChannel = bootstrap.bind(host, port).sync().channel();
    }

    @Override
    public void stop() {
        LOGGER.info("stop {}-http", serverIdentifier);

        try {
            if (listenChannel != null) {
                listenChannel.close().sync();
            }
        } catch (InterruptedException e) {
            LOGGER.warn("interrupted during {}-http shutdown", serverIdentifier);
        }

        bossEventLoopGroup.shutdownGracefully();
        workEventLoopGroup.shutdownGracefully();

        requestProcessingExecutor.shutdownNow();
    }
}
