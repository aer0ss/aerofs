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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
abstract class Channels {

    private static final Logger LOGGER = LoggerFactory.getLogger(Channels.class);

    static void expectedClose(ChannelHandlerContext ctx, String logMessage) {
        LOGGER.trace("{}: close: {}", Channels.getHexText(ctx.channel()), logMessage);
        ctx.channel().close();
    }

    static void close(ChannelHandlerContext ctx, String logMessage, Object... logObjects) {
        close(ctx.channel(), logMessage, logObjects);
    }

    static void close(Channel channel, String logMessage, Object... logObjects) {
        LOGGER.warn("{}: close: " + logMessage, Channels.getHexText(channel), logObjects);
        channel.close();
    }

    static void closeAndLogStack(ChannelHandlerContext ctx, String logMessage, Throwable t) {
        closeAndLogStack(ctx.channel(), logMessage, t);
    }

    static void closeAndLogStack(Channel channel, String logMessage, Throwable t) {
        LOGGER.warn("{}: close: {}", Channels.getHexText(channel), logMessage, t);
        channel.close();
    }

    static String getHexText(ChannelHandlerContext ctx) {
        return getHexText(ctx.channel());
    }

    static String getHexText(Channel channel) {
        return channel.id().asShortText();
    }

    private Channels() {
        // to prevent instantiation by subclasses
    }
}
