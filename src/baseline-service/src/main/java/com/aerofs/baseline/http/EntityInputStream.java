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
import com.codahale.metrics.Histogram;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

// FIXME (AG): this entire class is such a shitty, shitty piece of code; I'm ashamed of myself
@ThreadSafe
final class EntityInputStream extends ContentInputStream {

    private static final Histogram INPUT_SIZE_HISTOGRAM = MetricRegistries.getRegistry().histogram(MetricRegistries.name("http", "request", "entity-size"));

    private final List<ByteBuf> buffers = Lists.newLinkedList();
    private final HttpVersion httpVersion;
    private final ChannelHandlerContext ctx;
    private final boolean continueRequested;

    // all variables protected by this
    private long total = 0;
    private long readable = 0;
    private boolean closed;
    private boolean firstRead;
    private boolean readChoked;
    private boolean inputCompleted;

    public EntityInputStream(HttpVersion httpVersion, boolean continueRequested, ChannelHandlerContext ctx) {
        this.httpVersion = httpVersion;
        this.ctx = ctx;
        this.continueRequested = continueRequested;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void mark(int readLimit) {
        throw new UnsupportedOperationException("cannot rewind http entity input stream");
    }

    @Override
    public void reset() throws IOException {
        throw new UnsupportedOperationException("cannot rewind http entity input stream");
    }

    @Override
    public long skip(long n) throws IOException {
        throw new UnsupportedOperationException("cannot skip bytes in http entity input stream");
    }

    // FIXME (AG): write a customized read()
    // for now, just punt to the generalized read(b, off, len)
    @Override
    public synchronized int read() throws IOException {
        byte[] one = new byte[1];
        int read = read(one, 0, 1);
        return read == -1 ? -1 : one[0];
    }

    @Override
    public int read(@Nonnull byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public synchronized int read(@Nonnull byte[] b, int off, int len) throws IOException {
        // basic argument checks
        // pulled straight from InputStream
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        // if the sender already closed
        // the stream then we're going to
        // abort, *even if* the sender
        // has already sent the bytes we
        // care about
        throwIfClosed();

        // we may have to send a 100 CONTINUE
        // if the client specifically requests
        // it. to prevent the server from buffering
        // bytes we only send it the moment the
        // app thread starts reading from the
        // stream. this can be changed easily
        sendContinueIfRequested();

        int initialOffset = off;
        int bufferRemaining = len;

        while (true) {
            // get bytes from the network
            getNetworkBytes();

            // how many bytes can we read?
            int remaining = inputCompleted ? (int) Math.min(readable, bufferRemaining) : bufferRemaining;
            if (remaining == 0) {
                break;
            }

            Preconditions.checkState(!buffers.isEmpty(), "readable bytes, but no buffers");

            // read as much as required
            // from the top buffer
            ByteBuf head = buffers.get(0);
            int chunkReadable = Math.min(head.readableBytes(), remaining);
            head.readBytes(b, off, chunkReadable);

            // bookkeeping
            readable -= chunkReadable;
            off += chunkReadable;
            bufferRemaining -= chunkReadable;

            // remove the top buffer if it's exhausted
            if (head.readableBytes() == 0) {
                buffers.remove(0);
                head.release();
            }
        }

        return off == initialOffset ? -1 : off - initialOffset;
    }

    private void getNetworkBytes() throws IOException {
        while (buffers.isEmpty()) {
            // before starting the wait, check
            // if the channel was closed under us
            throwIfClosed();

            // nothing more is coming
            // there's no need to wait
            if (inputCompleted) {
                break;
            }

            // if we'd turned *off* reading
            // because we'd buffered too many bytes
            // turn it back on again
            if (readChoked) {
                readChoked = false;
                ctx.channel().read();
            }

            // wait until the buffers
            // are filled with bytes
            // from the wire
            try {
                wait();
            } catch (InterruptedException e) {
                throw new IOException("interrupted during wait for read");
            }

            // we don't have to check closed
            // here. this is because when close()
            // is called, we clean out all the
            // buffers. this ensures that we'll
            // get to the top of the loop again,
            // and cause throwIfClosed() to run
        }
    }

    private void sendContinueIfRequested() {
        // we only have to send a continue
        // the *first* time the request processor
        // decides to read the data
        if (firstRead) {
            firstRead = false;

            if (continueRequested) {
                ctx.channel().writeAndFlush(new DefaultHttpResponse(httpVersion, HttpResponseStatus.CONTINUE, false));
            }
        }
    }

    @Override
    public synchronized int available() throws IOException {
        throwIfClosed();
        return (int) readable;
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;

            // free the buffers we're holding on to
            Iterator<ByteBuf> iterator = buffers.iterator();
            while (iterator.hasNext()) {
                iterator.next().release();
                iterator.remove();
            }
        }

        // there should be *no network buffers*
        // sitting around after close() is called
        Preconditions.checkState(buffers.isEmpty(), "network buffers exist after close");

        // regardless of whether we've closed
        // previously or not, let any waiters
        // know that there's nothing available
        // any more
        notifyAll();
    }

    synchronized void addBuffer(ByteBuf content, boolean last) throws IOException {
        throwIfClosed();

        if (content.readableBytes() > 0) {
            // crucial for reading
            buffers.add(content);
            readable += content.readableBytes();

            // for metrics use only
            total += content.readableBytes();
        }

        if (last) {
            // we're done
            // only update the input size histogram now
            // NOTE: don't trigger additional reading -
            // that will be done by the jersey runtime's
            // call to commit()
            inputCompleted = true;
            INPUT_SIZE_HISTOGRAM.update(total);
        } else {
            // continue reading unless we hit
            // the limit of max cached bytes
            if (readable <= com.aerofs.baseline.http.Constants.ENTITY_UNREAD_BYTES_HIGH_WATERMARK) {
                ctx.channel().read();
            } else {
                readChoked = true;
            }
        }

        // let any waiters know that there's more to consume
        notifyAll();
    }

    private void throwIfClosed() throws IOException {
        if (closed) {
            throw new IOException("stream closed");
        }
    }
}
