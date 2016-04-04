/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core;

import com.aerofs.auth.client.cert.AeroDeviceCert;
import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.polaris.GsonUtil;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.security.GeneralSecurityException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static com.google.common.base.Preconditions.checkState;

/**
 *
 */
public class AsyncHttpClient
{
    protected static final Logger l = Loggers.getLogger(AsyncHttpClient.class);

    private final Auth _auth;
    private final URI _endpoint;
    private final ClientBootstrap _bootstrap;
    private final Executor _executor;

    private AtomicReference<Channel> _channel = new AtomicReference<>();

    protected static class Auth implements UnaryOperator<HttpHeaders>
    {
        private final String auth;

        public Auth(UserID user, DID did)
        {
            auth = AeroDeviceCert.getHeaderValue(user.getString(), did.toStringFormal());
        }

        @Override
        public HttpHeaders apply(HttpHeaders headers)
        {
            headers.add(HttpHeaders.Names.AUTHORIZATION, auth);
            return headers;
        }
    }

    protected AsyncHttpClient(URI endpoint, Executor executor, Auth auth, Timer timer,
                              ChannelFactory channelFactory, SSLEngineFactory sslEngineFactory)
    {
        _auth = auth;
        _executor = executor;
        _endpoint = endpoint;
        _bootstrap = new ClientBootstrap(channelFactory);
        _bootstrap.setPipelineFactory(() -> {
            try {
                return Channels.pipeline(
                        sslEngineFactory.newSslHandler(),
                        new HttpClientCodec(),
                        new HttpChunkAggregator(256 * C.KB),
                        new IdleStateHandler(timer, 0, 0, 60),
                        new Handler());
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static class Request
    {
        final HttpRequest http;
        final SettableFuture<HttpResponse> f;

        Request(HttpRequest http, SettableFuture<HttpResponse> f)
        {
            this.http = http;
            this.f = f;
        }
    }

    private static class Handler extends IdleStateAwareChannelHandler
    {
        // NB: use atomics because SslHandler's handshake lock makes it tricky to use a single lock
        // to synchronize upstream and downstream handlers without risking deadlocks
        private final AtomicBoolean _done = new AtomicBoolean();
        private final Queue<SettableFuture<HttpResponse>> _requests = new ConcurrentLinkedDeque<>();

        @Override
        public void writeRequested(ChannelHandlerContext ctx, MessageEvent ev)
        {
            Object request = ev.getMessage();
            if (request instanceof Request) {
                Request r = (Request)request;
                if (_done.get()) {
                    ev.getFuture().setFailure(new ClosedChannelException());
                    return;
                }
                // NB: it's important that the request be added to the queue and sent downstream
                // atomically, otherwise responses may be mapped to the wrong callback, which might
                // cause a crash or corrupted state.
                synchronized (this) {
                    _requests.add(r.f);
                    ctx.sendDownstream(new DownstreamMessageEvent(ev.getChannel(), ev.getFuture(),
                            r.http, ev.getRemoteAddress()));
                }
                if (r.http.getContent() != null) {
                    l.info("write {}\n{}", r.http,
                            r.http.getContent().toString(BaseUtil.CHARSET_UTF));
                } else {
                    l.info("write {}", r.http);
                }
            } else {
                ctx.sendDownstream(ev);
            }
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent ev)
        {
            HttpResponse r = (HttpResponse)ev.getMessage();
            l.info("recv {} {}", r.getStatus(), r.headers());
            if (l.isDebugEnabled()) {
                l.debug("{}", r.getContent().toString(BaseUtil.CHARSET_UTF));
            }
            if (!_done.get()) {
                _requests.poll().set(r);
            } else {
                l.warn("already closed");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent ev)
        {
            l.warn("ex", BaseLogUtil.suppress(ev.getCause(), UnresolvedAddressException.class,
                    ClosedChannelException.class, SocketException.class));
            ctx.getChannel().close();
        }

        @Override
        public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
        {
            l.info("timeout {}", e.getState());
            ctx.getChannel().close();
        }

        @Override
        public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            l.info("disconnected");
            failRequests();
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception
        {
            l.info("closed");
        }

        private void failRequests()
        {
            SettableFuture<HttpResponse> f;
            if (!_done.compareAndSet(false, true)) return;
            while ((f = _requests.poll()) != null) {
                l.info("fail remaining req");
                f.setException(new ClosedChannelException());
            }
        }
    }

    private void send(HttpRequest req, final SettableFuture<HttpResponse> f)
    {
        _auth.apply(req.headers());
        req.headers().add(HttpHeaders.Names.HOST, _endpoint.getHost());
        req.setUri(_endpoint.getPath() + req.getUri());

        Channel c = _channel.get();
        if (c != null && c.isConnected()) {
            write(c, req, f);
            return;
        }

        _bootstrap.connect(new InetSocketAddress(_endpoint.getHost(), _endpoint.getPort()))
                .addListener((ChannelFuture cf) -> {
                    if (cf.isSuccess()) {
                        onConnect(cf.getChannel());
                        write(cf.getChannel(), req, f);
                    } else {
                        l.info("connect failed", BaseLogUtil.suppress(cf.getCause()));
                        f.setException(cf.getCause());
                    }
                });
    }

    @FunctionalInterface
    public interface Function<T, R, E extends Exception>
    {
        R apply(T t) throws E;
    }

    public void post(String url, Object body, AsyncTaskCallback cb,
                     Function<HttpResponse, Boolean, Exception> cons)
    {
        post(url, body, cb, cons, _executor);
    }

    public void post(String url, Object body, AsyncTaskCallback cb,
                     Function<HttpResponse, Boolean, Exception> cons, Executor executor)
    {
        byte[] d = BaseUtil.string2utf(GsonUtil.GSON.toJson(body));
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, url);
        req.headers().add(HttpHeaders.Names.CONTENT_TYPE, "application/json");
        req.headers().add(Names.CONTENT_LENGTH, d.length);
        req.setContent(ChannelBuffers.wrappedBuffer(d));

        send(req, cb, cons, executor);
    }

    public void send(HttpRequest req, AsyncTaskCallback cb,
            Function<HttpResponse, Boolean, Exception> cons)
    {
        send(req, cb, cons, _executor);
    }

    public void send(HttpRequest req, AsyncTaskCallback cb,
                     Function<HttpResponse, Boolean, Exception> cons, Executor executor)
    {
        SettableFuture<HttpResponse> f = SettableFuture.create();
        // NB: MUST add the listener before passing the future to avoid some nasty race conditions
        //
        // We use CoreExecutor to make sure the listener is invoked through a core event to be able
        // to access the core db and all in-memory data structures that are implicitly synchronized
        // through the core lock.
        //
        // Unfortunately the core queue is protected by a non-reentrant lock so, in the rare but not
        // impossible case of the request completing before the listener is added, we would end up
        // calling {@CoreExecutor#execute} from a core thread, which  would result in an AE
        //
        // I consider this to be an egregious violation of the standard Executor interface and I
        // would very much like to fix it but I fear I would get sidetracked rewriting the entire
        // threading/event model of the core.
        //
        // Adding the listener before the future is used ensures that it will be called from a Netty
        // I/O thread, thereby avoiding any possibility of triggering the AE.
        f.addListener(() -> {
            checkState(f.isDone());
            checkState(!f.isCancelled());
            try {
                HttpResponse r = f.get();
                cb.onSuccess_(cons.apply(r));
            } catch (ExecutionException e) {
                cb.onFailure_(e.getCause());
            } catch (Throwable t) {
                cb.onFailure_(t);
            }
        }, executor);

        send(req, f);
    }

    private void onConnect(Channel c)
    {
        _channel.set(c);
        c.getCloseFuture().addListener(f -> _channel.compareAndSet(c, null));
    }

    private void write(Channel c, HttpRequest req, SettableFuture<HttpResponse> f)
    {
        c.write(new Request(req, f)).addListener(cf -> {
            if (!cf.isSuccess()) {
                l.info("write failed", BaseLogUtil.suppress(cf.getCause(),
                        ClosedChannelException.class));
                f.setException(cf.getCause());
            }
        });
    }
}
