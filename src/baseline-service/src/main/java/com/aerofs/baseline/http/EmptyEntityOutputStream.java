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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.LastHttpContent;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;

@ThreadSafe
final class EmptyEntityOutputStream extends ContentOutputStream {

    private final ChannelHandlerContext ctx;

    private boolean flushed = false;

    EmptyEntityOutputStream(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    void markError() {
        // noop
    }

    @Override
    public void write(int b) throws IOException {
        throw new IllegalStateException("cannot write to an empty output stream");
    }

    @Override
    public synchronized void flush() throws IOException {
        flushOnce();
    }

    @Override
    public synchronized void close() throws IOException {
        flushOnce();
    }

    private void flushOnce() {
        if (!flushed) {
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            flushed = true;
        }
    }
}
