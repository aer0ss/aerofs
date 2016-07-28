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

import com.aerofs.baseline.metrics.MetricRegistries;
import com.codahale.metrics.Timer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.glassfish.jersey.server.ApplicationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.aerofs.baseline.http.Constants.HTTP_MAX_CHUNK_SIZE;
import static com.aerofs.baseline.http.Constants.HTTP_MAX_HEADER_SIZE;
import static com.aerofs.baseline.http.Constants.HTTP_MAX_INITIAL_LINE_LENGTH;

@ThreadSafe
final class AcceptedChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcceptedChannelInitializer.class);

    private static final Timer CHANNEL_LIFETIME_TIMER = MetricRegistries.getRegistry().timer(MetricRegistries.name("http", "connection"));

    private final FinalInboundHandler finalInboundHandler = new FinalInboundHandler();
    private final RequestHeaderAssigner requestHeaderAssigner = new RequestHeaderAssigner();
    private final HttpConfiguration http;
    private final ApplicationHandler applicationHandler;
    private final URI baseUri;
    private final Executor applicationExecutor;
    private final io.netty.util.Timer timer;

    public AcceptedChannelInitializer(HttpConfiguration http, ApplicationHandler applicationHandler, URI baseUri, Executor applicationExecutor, io.netty.util.Timer timer) {
        this.http = http;
        this.applicationHandler = applicationHandler;
        this.baseUri = baseUri;
        this.applicationExecutor = applicationExecutor;
        this.timer = timer;
    }

    @Override
    public void initChannel(SocketChannel channel) throws Exception {
        LOGGER.trace("{}: setup", Channels.getHexText(channel));

        // time how long channels live
        channel.closeFuture().addListener(new GenericFutureListener<Future<Void>>() {

            private final Timer.Context lifetimeContext = CHANNEL_LIFETIME_TIMER.time();

            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                lifetimeContext.stop();
            }
        });

        // create the channel pipeline
        channel.pipeline().addLast(
                new IdleTimeoutHandler(0, 0, (int) http.getIdleTimeout(), TimeUnit.MILLISECONDS),
                new HttpServerCodec(HTTP_MAX_INITIAL_LINE_LENGTH, HTTP_MAX_HEADER_SIZE, HTTP_MAX_CHUNK_SIZE, false),
                requestHeaderAssigner,
                new BufferingHttpObjectHandler(),
                new HttpRequestHandler(applicationHandler, baseUri, applicationExecutor, timer),
                finalInboundHandler
                );
    }
}
