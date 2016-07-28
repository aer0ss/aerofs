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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Random;

@ThreadSafe
@ChannelHandler.Sharable
final class RequestHeaderAssigner extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestHeaderAssigner.class);
    private static final Random RANDOM = new Random();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            String requestId = request.headers().get(Headers.REQUEST_TRACING_HEADER);

            if (requestId == null) {
                requestId = Integer.toHexString(RANDOM.nextInt(Integer.MAX_VALUE));
                request.headers().add(Headers.REQUEST_TRACING_HEADER, requestId);
            }

            LOGGER.debug("{}: [{}] new http request", Channels.getHexText(ctx), requestId);
        }

        super.channelRead(ctx, msg);
    }
}
