/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.core.net.ClientSSLEngineFactory;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.lib.CoreExecutor;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.security.GeneralSecurityException;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static com.google.common.base.Preconditions.checkState;

/**
 *
 */
public class PolarisClient
{
    private final static Logger l = Loggers.getLogger(PolarisClient.class);

    private final Auth _auth;
    private final URI _endpoint;
    private final Bootstrap _bootstrap;
    private final Executor _executor;

    private AtomicReference<Channel> _channel = new AtomicReference<>();

    private static class Auth implements UnaryOperator<HttpHeaders>
    {
        private final String auth;
        private final String cname;

        Auth(UserID user, DID did)
        {
            auth = "Aero-Device-Cert " + did.toStringFormal() + " " + user;
            cname = "CN=" + BaseSecUtil.getCertificateCName(user, did);
        }

        @Override
        public HttpHeaders apply(HttpHeaders headers)
        {
            headers.add(Names.AUTHORIZATION, auth);
            // TODO: remove DName and Verify headers when Polaris instance is behind nginx
            headers.add("DName", cname);
            headers.add("Verify", "SUCCESS");
            return headers;
        }
    }

    @Inject
    public PolarisClient(CoreExecutor executor, CfgLocalDID localDID, CfgLocalUser localUser,
            ClientSSLEngineFactory sslEngineFactory)
    {
        this(URI.create("http://polaris.aerofs.com:9999"), executor,
                new Auth(localUser.get(), localDID.get()), sslEngineFactory);
    }

    public PolarisClient(URI endpoint, Executor executor, Auth auth, SSLEngineFactory sslEngineFactory)
    {
        _auth = auth;
        _executor = executor;
        _endpoint = endpoint;
        _bootstrap = new Bootstrap();
        _bootstrap.group(new NioEventLoopGroup());
        _bootstrap.channel(NioSocketChannel.class);
        _bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        _bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch)
            {
                // TODO: enable SSL when Polaris instance is behind nginx
                //try {
                    ch.pipeline().addLast(
                            new IdleStateHandler(0, 0, 5),
                            //new SslHandler(sslEngineFactory.getSSLEngine()),
                            new HttpClientCodec(),
                            new HttpObjectAggregator(256 * C.KB),
                            new Handler());
                //} catch (IOException | GeneralSecurityException e) {
                //    throw new RuntimeException(e);
                //}
            }
        });
    }

    private static class Request
    {
        final HttpRequest http;
        final SettableFuture<FullHttpResponse> f;

        Request(HttpRequest http, SettableFuture<FullHttpResponse> f)
        {
            this.http = http;
            this.f = f;
        }
    }

    private static class Handler extends ChannelDuplexHandler
    {
        private final Queue<SettableFuture<FullHttpResponse>> _requests =
                Queues.newConcurrentLinkedQueue();

        @Override
        public void write(ChannelHandlerContext ctx, Object request,
                ChannelPromise promise)
        {
            if (request instanceof Request) {
                Request r = (Request)request;
                _requests.add(r.f);
                if (r.http instanceof FullHttpMessage) {
                    l.info("write {}\n{}", r.http,
                            ((FullHttpMessage)r.http).content().toString(BaseUtil.CHARSET_UTF));
                } else {
                    l.info("write {}", r.http);
                }
                ctx.writeAndFlush(r.http, promise);
            } else {
                ctx.write(request, promise);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object response)
        {
            FullHttpResponse r = (FullHttpResponse)response;
            l.info("recv {} {}", r.status(), r.headers());
            _requests.poll().set((FullHttpResponse)response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
        {
            l.warn("ex", BaseLogUtil.suppress(cause, ClosedChannelException.class));
            ctx.close();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
        {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent e = (IdleStateEvent) evt;
                l.info("timeout {}", e.state());
                ctx.close();
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx)
        {
            l.info("active");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx)
        {
            l.info("inactive");
            failRequests();
        }

        private synchronized void failRequests()
        {
            SettableFuture<?> f;
            while ((f = _requests.poll()) != null) {
                l.info("fail remaining req");
                f.setException(new ClosedChannelException());
            }
        }
    }

    ListenableFuture<FullHttpResponse> send(HttpRequest req)
    {
        final SettableFuture<FullHttpResponse> f = SettableFuture.create();

        _auth.apply(req.headers());

        Channel c = _channel.get();
        if (c != null && c.isActive()) {
            write(c, req, f);
            return f;
        }

        _bootstrap.connect(new InetSocketAddress(_endpoint.getHost(), _endpoint.getPort()))
                .addListener((ChannelFuture cf) -> {
                    if (cf.isSuccess()) {
                        onConnect(cf.channel());
                        write(cf.channel(), req, f);
                    } else {
                        l.info("connect failed", cf.cause());
                        f.setException(cf.cause());
                    }
                });

        return f;
    }

    @FunctionalInterface
    public interface Function<T, R, E extends Exception>
    {
        R apply(T t) throws E;
    }

    public void send(HttpRequest req, AsyncTaskCallback cb,
            Function<FullHttpResponse, Boolean, Exception> cons)
    {

        ListenableFuture<FullHttpResponse> f = send(req);
        f.addListener(() -> {
            checkState(f.isDone());
            checkState(!f.isCancelled());
            try {
                cb.onSuccess_(cons.apply(f.get()));
            } catch (Throwable t) {
                cb.onFailure_(t);
            }
        }, _executor);
    }

    private void onConnect(Channel c)
    {
        _channel.set(c);
        c.closeFuture().addListener(f -> _channel.compareAndSet(c, null));
    }

    private void write(Channel c, HttpRequest req, SettableFuture<FullHttpResponse> f)
    {
        c.write(new Request(req, f)).addListener(cf -> {
            if (!cf.isSuccess()) {
                l.info("write failed", BaseLogUtil.suppress(cf.cause(),
                        ClosedChannelException.class));
                f.setException(cf.cause());
            }
        });
    }
}
