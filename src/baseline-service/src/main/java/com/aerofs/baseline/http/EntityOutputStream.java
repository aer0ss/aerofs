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

import com.codahale.metrics.Histogram;
import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.LastHttpContent;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;

@ThreadSafe
final class EntityOutputStream extends ContentOutputStream {

    private final ChannelHandlerContext ctx;
    private final Histogram contentLengthHistogram;

    private boolean failed = false;
    private boolean closed = false;
    private long written;

    @Nullable
    private ByteBuf chunk;

    public EntityOutputStream(ChannelHandlerContext ctx, Histogram contentLengthHistogram) {
        this.ctx = ctx;
        this.contentLengthHistogram = contentLengthHistogram;
    }

    @Override
    synchronized void markError() {
        failed = true;
    }

    @Override
    public synchronized void write(int b) throws IOException {
        throwIfClosed();

        while(true) {
            allocateChunk();

            if (chunk.writableBytes() == 0) { // chunk is guaranteed not to be null here
                writeChunk();
                continue;
            }

            chunk.writeByte(b); // may throw
            written++;
            break;
        }
    }

    @Override
    public synchronized void write(@Nullable byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        while (len > 0) {
            allocateChunk();

            if (chunk.writableBytes() == 0) { // chunk is guaranteed not to be null here
                writeChunk();
                continue;
            }

            int writable = Math.min(chunk.writableBytes(), len);
            chunk.writeBytes(b, off, writable);

            off+= writable;
            len -= writable;
            written += writable;
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        throwIfClosed();

        if (chunk != null) {
            writeChunk();
            flushChunk();
        }

        // don't allocate a chunk here because the
        // caller may not want to send any more data
    }

    // assume that close is *always* called,
    // regardless of whether this stream is in
    // a good state
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;

        if (failed) {
            if (chunk != null) {
                chunk.release();
            }
        } else {
            if (chunk != null) {
                writeChunk();
            }

            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT); // netty requires this to indicate output completed

            contentLengthHistogram.update(written);
        }
    }

    private void allocateChunk() {
        if (chunk == null) {
            chunk = ctx.alloc().buffer();
        }
    }

    private void writeChunk() {
        Preconditions.checkNotNull(chunk, "null chunk prior to write");
        ByteBuf forwarded = chunk;
        chunk = null;
        ctx.write(new DefaultHttpContent(forwarded)); // pass ownership to next handler
    }

    private void flushChunk() {
        ctx.flush();
    }

    private void throwIfClosed() throws IOException {
        if (closed) {
            throw new IOException("channel closed");
        }
    }
}