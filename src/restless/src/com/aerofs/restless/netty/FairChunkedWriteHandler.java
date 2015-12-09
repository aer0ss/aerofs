package com.aerofs.restless.netty;

import com.google.common.base.Objects;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.handler.stream.ChunkedInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty's ChunkedWriteHandler will happily start an unbounded write loop from an io thread.
 *
 * If the underlying channel can keep up this will not only starve other streams but also
 * prevent incoming messages from being handled in a timely fashion.
 *
 * To avoid these issues, a single handler is shared by all connections. It uses a separate thread
 * to write messages to avoid read starvation and flushes writable channels in a round-robin
 * fashion to ensure some amount of fairness.
 */
@Sharable
public class FairChunkedWriteHandler implements ChannelUpstreamHandler, ChannelDownstreamHandler {

    private final static Logger l = LoggerFactory.getLogger(FairChunkedWriteHandler.class);

    private static class S {
        // doubly-linked list for round-robin writes
        S prev;
        S next;

        MessageEvent current;

        final AtomicBoolean skip;
        final Queue<MessageEvent> q;
        final ChannelHandlerContext ctx;

        // default ctor for head node
        S() {
            q = null;
            ctx = null;
            skip = null;
        }

        S(ChannelHandlerContext ctx) {
            this.ctx = ctx;
            this.q = new ConcurrentLinkedQueue<>();
            skip = new AtomicBoolean();
        }

        public boolean flushOne() {
            MessageEvent me;
            if (current == null) {
                me = q.poll();

                if (me == null) return false;

                // send non-chunked message directly
                if (!(me.getMessage() instanceof ChunkedInput)) {
                    ctx.sendDownstream(me);
                    return true;
                }
                current = me;
            } else {
                me = current;
            }

            if (me.getFuture().isDone()) {
                // partial write failed
                current = null;
                return true;
            }

            // or send a single chunk
            ChunkedInput chunks = (ChunkedInput)me.getMessage();
            try {
                Object chunk = chunks.nextChunk();

                ChannelFuture f;
                if (chunks.isEndOfInput()) {
                    current = null;
                    f = me.getFuture();
                    f.addListener(cf -> closeInput(chunks));
                } else {
                    f = Channels.future(ctx.getChannel());
                    f.addListener(cf -> {
                        if (!cf.isSuccess()) {
                            me.getFuture().setFailure(cf.getCause());
                            closeInput(chunks);
                        }
                    });
                }

                Channels.write(ctx, f, chunk, me.getRemoteAddress());
            } catch (Throwable t) {
                l.debug("uh oh", t);
                current = null;
                Channels.fireExceptionCaughtLater(ctx, t);
                closeInput(chunks);
            }
            return true;
        }
    }
    // access to linked-list and map synchronized on head
    // list of flushable channels
    private final S head = new S();
    // set of active channels
    private final Map<Channel, S> _m = new ConcurrentHashMap<>();

    private volatile boolean stopped;
    private Thread _t;

    private void startIfNeeded() {
        if (_t != null) return;
        synchronized (this) {
            if (_t == null) {
                _t = new Thread(this::run, "cwh");
                _t.start();
            }
        }
    }

    public void stop() {
        stopped = true;
        synchronized (head) {
            head.notifyAll();
        }
    }

    private void run() {
        S c = head;
        while (!stopped) {
            // round-robin among flushable channels
            synchronized (head) {
                if (c == null || c.next == null) {
                    // wait for flushable channels
                    while (head.next == null) {
                        if (stopped) return;
                        try {
                            l.debug("wait");
                            head.wait();
                        } catch (InterruptedException e) {
                            throw new AssertionError(e);
                        }
                    }
                    c = head.next;
                } else {
                    c = c.next;
                }
                assert c.q != null && c.ctx != null;

                Channel channel = c.ctx.getChannel();
                if (!channel.isConnected()) {
                    l.debug("closed {}", channel);
                    _m.remove(channel);
                    discard(c);
                    c = hold(c);
                    continue;
                } else if (!channel.isWritable()) {
                    // delay removal of channel from linked list to mitigate the adverse impact of
                    // frequent writability change (when bandwidth is maxed out)
                    // repeatedly shuffling the list of channels would hurt fairness as the order
                    // in which channels are marked as writable will skew the bandwidth distribution
                    // and may go as far as starving some channels long enough that the downstream
                    // end times out
                    if (!c.skip.compareAndSet(false, true)) {
                        c = hold(c);
                    }
                    continue;
                }
            }

            // propagate non-chunked message
            if (!c.flushOne()) {
                c = hold(c);
            }
        }
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent cse = (ChannelStateEvent) e;
            switch (cse.getState()) {
                case INTEREST_OPS: {
                    if (ctx.getChannel().isWritable()) {
                        S s = _m.get(ctx.getChannel());
                        if (s != null) {
                            s.skip.set(false);
                            flush(s);
                        }
                    }
                    break;
                }
                case OPEN: {
                    if (!(Boolean) cse.getValue()) {
                        S s = _m.get(ctx.getChannel());
                        if (s != null) flush(s);
                    }
                    break;
                }
            }
        }
        ctx.sendUpstream(e);
    }

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (!(e instanceof MessageEvent)) {
            ctx.sendDownstream(e);
            return;
        }

        final Channel channel = ctx.getChannel();

        S s = _m.get(channel);
        if (s == null) {
            s = new S(ctx);
            s = Objects.firstNonNull(_m.putIfAbsent(channel, s), s);
        }

        s.q.offer((MessageEvent)e);

        if (!channel.isConnected() || channel.isWritable()) {
            startIfNeeded();
            flush(s);
        }
    }

    private void flush(S s) {
        synchronized (head) {
            if (s.prev == null) {
                s.prev = head;
                s.next = head.next;
                if (s.next != null) s.next.prev = s;
                head.next = s;
            }
            // wakeup flush thread
            head.notifyAll();
        }
    }

    // NB: should only be called from flush thread to avoid gaps in iteration
    private S hold(S s) {
        synchronized (head) {
            S prev = s.prev;
            assert prev != null;
            prev.next = s.next;
            if (s.next != null) s.next.prev = prev;
            s.next = null;
            s.prev = null;
            return prev;
        }
    }

    private void discard(S s) {
        MessageEvent me;
        ClosedChannelException cause = null;
        while ((me = s.q.poll()) != null) {
            Object m = me.getMessage();
            if (m instanceof ChunkedInput) {
                closeInput((ChunkedInput)m);
            }
            if (cause == null) {
                cause = new ClosedChannelException();
            }
            me.getFuture().setFailure(cause);
        }
        if (cause != null) {
            l.debug("discard {}", s.ctx.getChannel());
            Channels.fireExceptionCaughtLater(s.ctx.getChannel(), cause);
        }
    }

    static void closeInput(ChunkedInput chunks) {
        try {
            chunks.close();
        } catch (Throwable t) {
            l.warn("Failed to close chunked input", t);
        }
    }
}
