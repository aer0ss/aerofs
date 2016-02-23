package com.aerofs.oauth;

import com.aerofs.base.*;
import com.aerofs.base.net.NettyUtil;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSessionContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ClosedChannelException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class SimpleHttpClient<Q,R> extends IdleStateAwareChannelHandler
{
    private final static Logger l = Loggers.getLogger(SimpleHttpClient.class);

    protected final URI _endpoint;
    private final ClientBootstrap _bootstrap;
    private final SSLEngineFactory _sslEngineFactory;

    private Channel _channel;
    private long _lastWrite;

    private final Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final Class<? extends R> _class;
    private final Queue<Req<Q, R>> _pending = new ConcurrentLinkedQueue<>();
    private final Queue<Req<Q, R>> _inflight = new ConcurrentLinkedQueue<>();

    static class Req<Q, R> {
        final Q query;
        final SettableFuture<R> future = SettableFuture.create();

        Req(Q q) { query = q; }
    }

    public static class UnexpectedResponse extends Exception
    {
        private static final long serialVersionUID = 0L;
        public final int statusCode;
        public UnexpectedResponse(int code) { statusCode = code; }
    }

    private static class ClientSSLEngineFactory extends SSLEngineFactory
    {
        public ClientSSLEngineFactory(ICertificateProvider trustedCA)
        {
            super(Mode.Client, Platform.Desktop, null, trustedCA, null);
        }

        @Override
        protected void onSSLContextCreated(SSLContext context)
        {
            SSLSessionContext sessionContext = context.getClientSessionContext();
            sessionContext.setSessionCacheSize(1);
            sessionContext.setSessionTimeout((int)C.HOUR);
        }
    }

    public SimpleHttpClient(URI endpoint, final ICertificateProvider cacert,
                            ClientSocketChannelFactory clientChannelFactory, final Timer timer)
    {
        _endpoint = fixPort(endpoint);
        _sslEngineFactory = new ClientSSLEngineFactory(cacert);
        _bootstrap = new ClientBootstrap(clientChannelFactory);
        _bootstrap.setPipelineFactory(() -> {
            ChannelPipeline p = Channels.pipeline(
                    new HttpClientCodec(),
                    new HttpChunkAggregator(2 * C.KB),
                    new IdleStateHandler(timer, 0, 0, 5, TimeUnit.SECONDS),
                    this
            );
            if (_endpoint.getScheme().equals("https")) {
                p.addFirst("ssl", _sslEngineFactory.newSslHandler());
            }
            return p;
        });
        _class = GenericUtils.getTypeClass(getClass(), 1);
    }

    private static URI fixPort(URI uri)
    {
        if (uri.getPort() != -1) return uri;
        Preconditions.checkState(ImmutableSet.of("http", "https").contains(uri.getScheme()));
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(),
                    ("http".equals(uri.getScheme()) ? 80 : 443),
                    uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    public ListenableFuture<R> send(Q query) {
        return send(new Req<>(query));
    }

    private ListenableFuture<R> send(final Req<Q, R> req) {
        _pending.add(req);
        synchronized (this) {
            if (_channel == null) {
                connect();
            } else if (_channel.isConnected()) {
                flush();
            }
        }
        return req.future;
    }

    private void flush() {
        while (true) {
            Req<Q, R> r = _pending.poll();
            if (r == null) break;

            // filter out cancelled requests (timeouts)
            if (r.future.isCancelled()) continue;

            l.debug("write {}", r.query);
            _lastWrite = System.nanoTime();
            _channel.write(r).addListener(cf -> {
                if (!cf.isSuccess()) {
                    r.future.setException(cf.getCause());
                    l.warn("failed", BaseLogUtil.suppress(cf.getCause()));
                    cf.getChannel().close();
                }
            });
        }
    }

    private void connect() {
        l.debug("connect");
        ChannelFuture f = _bootstrap.connect(new InetSocketAddress(_endpoint.getHost(), _endpoint.getPort()));
        _channel = f.getChannel();
        f.addListener(cf -> {
            if (cf.isSuccess()) {
                synchronized (SimpleHttpClient.this) {
                    flush();
                }
            } else {
                l.warn("failed to connect", BaseLogUtil.suppress(cf.getCause()));
                // fail first pending request to avoid infinite retry
                synchronized (SimpleHttpClient.this) {
                    Req<Q, R> r = _pending.poll();
                    if (r != null) r.future.setException(cf.getCause());
                }
                cf.getChannel().close();
            }
        });
        _channel.getCloseFuture().addListener(cf -> {
            synchronized (SimpleHttpClient.this) {
                _channel = null;
                if (!_pending.isEmpty()) {
                    connect();
                }
            }
        });
    }

    @Override
    public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
    {
        synchronized (this) {
            // IdleStateHandler only resets timer on writeComplete
            // this leaves a window for a write to be enqueued right before the idle timeout fires
            if (System.nanoTime() < _lastWrite + TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS)) {
                return;
            }
            if (_channel == ctx.getChannel()) {
                _channel = null;
            }
        }
        l.debug("timeout");
        ctx.getChannel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        l.warn("ex ", BaseLogUtil.suppress(e.getCause(), IOException.class, ClosedChannelException.class));
        ctx.getChannel().close();
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        l.info("closed");
        failInflightRequests();
    }

    private void failInflightRequests()
    {
        while (!_inflight.isEmpty()) {
            Req<Q, R> r = _inflight.remove();
            l.info("fail {}", r);
            r.future.setException(new ClosedChannelException());
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me)
    {
        HttpResponse msg = (HttpResponse)me.getMessage();
        String content = BaseUtil.utf2string(NettyUtil.toByteArray(msg.getContent()));

        l.debug("response {} {}", msg.getStatus(), content);

        Req<Q, R> req = Preconditions.checkNotNull(_inflight.peek());
        R response = null;
        if (msg.getStatus().getCode() == 200) {
            try {
                response = _gson.fromJson(content, _class);
            } catch (JsonParseException e) {
                l.warn("invalid JSON", BaseLogUtil.suppress(e));
                response = null;
            }
        }
        if (response != null) {
            req.future.set(response);
        } else {
            l.warn("unexpected {}", msg.getStatus());
            req.future.setException(new UnexpectedResponse(msg.getStatus().getCode()));
        }
        // IMPORTANT: dequeue request *AFTER* setting future, to ensure that if an exception
        // occurs before that, the exceptionCaught handler will still be able to set it
        _inflight.remove();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me)
    {
        Req<Q, R> req = (Req<Q, R>)me.getMessage();

        String uri = buildURI(req.query);
        HttpRequest http = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        http.headers().set(Names.HOST, _endpoint.getHost());
        modifyRequest(http, req.query);

        _inflight.add(req);
        me.getFuture().addListener(cf -> {
            if (!cf.isSuccess()) {
                l.warn("failed to write ", BaseLogUtil.suppress(cf.getCause()));
                failInflightRequests();
            }
        });
        ctx.sendDownstream(new DownstreamMessageEvent(ctx.getChannel(), me.getFuture(), http, null));
    }

    protected String buildURI(Q query) {
        return _endpoint.getPath();
    }
    protected void modifyRequest(HttpRequest req, Q query) {}
}
