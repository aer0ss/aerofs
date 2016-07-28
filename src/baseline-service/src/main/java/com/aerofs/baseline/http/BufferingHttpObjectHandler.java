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

import com.google.common.collect.Lists;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayList;

@NotThreadSafe
final class BufferingHttpObjectHandler extends ChannelDuplexHandler {

    private final ArrayList<Object> queuedReads = Lists.newArrayListWithCapacity(10); // FIXME (AG): UNBOUNDED!

    private boolean currentReadComplete = true;
    private boolean waiting;

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        if (hasQueued()) {
            super.channelRead(ctx, queuedReads.remove(0));
        } else {
            waiting = true;

            if (currentReadComplete) {
                readAgain(ctx);
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        queuedReads.add(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        currentReadComplete = true;

        if (waiting) {
            if (hasQueued()) {
                waiting = false;
                super.channelRead(ctx, queuedReads.remove(0));
            } else {
                readAgain(ctx);
            }
        }
    }

    private boolean hasQueued() {
        return !queuedReads.isEmpty();
    }

    private void readAgain(ChannelHandlerContext ctx) throws Exception {
        currentReadComplete = false;
        super.read(ctx);
    }
}
