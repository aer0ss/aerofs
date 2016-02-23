package com.aerofs.havre.proxy;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.havre.RequestRouter;
import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.havre.Authenticator;
import com.aerofs.havre.Authenticator.UnauthorizedUserException;
import com.aerofs.havre.EndpointConnector;
import com.aerofs.havre.Version;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.oauth.TokenVerifier;
import com.aerofs.tunnel.ShutdownEvent;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.http.cookie.*;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.cookie.Cookie;
import org.jboss.netty.handler.codec.http.cookie.DefaultCookie;
import org.jboss.netty.handler.timeout.IdleState;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Relay HTTP requests upstream (i.e. from downstream caller to upstream host)
 *
 * Connections are persistent by default (HTTP 1.1):
 *   - each incoming connection (i.e. client to gateway) gets its own handler instance
 *   - each incoming connection gets its own outgoing connection
 *
 * Consistency across connections (i.e. reconnecting to the same endpoint) is implemented
 * through HTTP cookies, as described in the design doc (docs/design/rest_gateway)
 */
public class HttpRequestProxyHandler extends SimpleChannelUpstreamHandler
{
    private static final Logger l = Loggers.getLogger(HttpRequestProxyHandler.class);

    private final Timer _timer;
    private final Authenticator _auth;
    private final RequestRouter _router;
    private final EndpointConnector _endpoints;
    private final ChannelGroup _channelGroup;

    private String _token;
    private AuthenticatedPrincipal _principal;

    private Channel _downstream;
    private Channel _upstream;

    public HttpRequestProxyHandler(Timer timer, Authenticator auth, EndpointConnector endpoints,
                                   RequestRouter router, ChannelGroup channelGroup)
    {
        _timer = timer;
        _auth = auth;
        _router = router;
        _endpoints = endpoints;
        _channelGroup = channelGroup;
    }

    static long READ_TIMEOUT = 10;
    static long WRITE_TIMEOUT = 30;
    static TimeUnit TIMEOUT_UNIT = TimeUnit.SECONDS;

    private static final String HEADER_ROUTE =  "Route";
    private static final String HEADER_ALT_ROUTES = "Alternate-Routes";

    private static @Nullable DID parseDID(String route)
    {
        try {
            return route != null ? new DID(route) : null;
        } catch (ExInvalidID e) {
            return null;
        }
    }

    private static String extractToken(HttpRequest request) {
        // reject requests with more than one Authorization header
        List<String> authHeaders = request.headers().getAll(Names.AUTHORIZATION);
        String authHeader = authHeaders != null && authHeaders.size() == 1 ?
                authHeaders.get(0) : null;

        // reject requests with more than one token query param
        List<String> tokenParams = new QueryStringDecoder(request.getUri())
                .getParameters()
                .get("token");
        String queryToken = tokenParams != null && tokenParams.size() == 1 ?
                tokenParams.get(0) : null;

        // user must include token in either the header or the query params, but not both
        if ((authHeader == null) == (queryToken == null)) return null;
        return authHeader != null ? TokenVerifier.accessToken(authHeader) : queryToken;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent me)
            throws Exception
    {
        final Object message = me.getMessage();
        final Channel downstream = me.getChannel();

        if (message instanceof HttpRequest) {
            HttpRequest req = (HttpRequest)message;

            String token = extractToken(req);
            if (token == null) {
                sendError(downstream, HttpResponseStatus.UNAUTHORIZED);
                return;
            }

            // First request in a given gateway connection
            //  - derive user id from OAuth token to pick an appropriate server
            //    and avoid load from unauthorized clients
            if (_principal == null || !token.equals(_token)) {
                try {
                    _principal = _auth.authenticate(token);
                    _token = token;
                } catch (UnauthorizedUserException e) {
                    sendError(downstream, HttpResponseStatus.UNAUTHORIZED);
                    return;
                }
            }

            Version version = Version.fromRequestPath(req.getUri());

            // The Route header overrides any cookie and enforces strict consistency
            boolean strictConsistency = false;
            DID did = parseDID(req.headers().get(HEADER_ROUTE));
            if (did != null) {
                strictConsistency = true;
            }

            if (_router != null && did == null) {
                did = _router.route( new QueryStringDecoder(req.getUri()).getPath(),
                        _endpoints.candidates(_principal, version));
                l.info("{} {} {} {}", did, false, version, _downstream);
            }
            if (_upstream != null) {
                DID cur = _endpoints.device(_upstream);
                if (!cur.equals(did)) {
                    l.info("close upstream {} {}", _upstream, _downstream);
                    ChannelFuture f = new DefaultChannelFuture(_upstream, false);
                    _upstream.getPipeline().sendDownstream(new ShutdownEvent(_upstream, f));
                    // NB: netty doesn't like await() being called inside an i/o thread, for
                    // good reasons. It's actually safe to do here because upstream and downstream
                    // use distinct pools of i/o threads so there's no room for deadlock and the
                    // downstream wait is unavoidable. It's not ideal because other downstream
                    // channels using the same i/o thread may experience delays. Another alternative
                    // would be to always disable read on the downstream channel while a response is
                    // expected. It's not clear how foolproof that would be though...
                    SettableFuture<Void> ff = SettableFuture.create();
                    f.addListener(cf -> {
                        if (cf.isSuccess()) {
                            ff.set(null);
                        } else {
                            ff.setException(cf.getCause());
                        }
                    });
                    ff.get();
                    _upstream = null;
                }
            }
            if (_upstream == null || !_upstream.isConnected()) {
                _upstream = _endpoints.connect(_principal, did, strictConsistency, version, pipeline());
                l.info("opened upstream {} {}", _upstream, _downstream);
            }

            if (_upstream == null) {
                sendError(downstream, HttpResponseStatus.SERVICE_UNAVAILABLE);
                return;
            }

            // remove gateway-specific header
            req.headers().remove(Names.COOKIE);
            req.headers().remove(HEADER_ROUTE);

            _upstream.write(message);
        } else {
            // HttpChunk
            if (_upstream == null || !_upstream.isConnected()) {
                l.warn("ignore incoming message on {}", downstream);
            } else {
                _upstream.write(message);
                if (((HttpChunk)message).isLast()) {
                    // stop reading incoming messages until the response is sent
                    // this is necessary to prevent pipelined requests from breaking
                    // when sent to different upstreams
                    downstream.setReadable(false);
                }
            }
        }
    }

    private ChannelPipeline pipeline() {
        return Channels.pipeline(
                new IdleStateHandler(_timer, READ_TIMEOUT, WRITE_TIMEOUT, 0, TIMEOUT_UNIT),
                new HttpClientCodec(),
                new HttpResponseProxyHandler()
        );
    }

    private static void sendError(Channel downstream, HttpResponseStatus status)
    {
        if (downstream.isConnected()) {
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
            response.headers().set(Names.CONTENT_LENGTH, 0);
            response.headers().set(Names.CACHE_CONTROL, Values.NO_CACHE + "," + Values.NO_TRANSFORM);
            response.headers().set(Names.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            if (status == HttpResponseStatus.UNAUTHORIZED) {
                response.headers().set(Names.WWW_AUTHENTICATE, "Bearer realm=\"AeroFS\"");
            }
            downstream.write(response);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e)
            throws Exception
    {
        l.warn("exception on downstream channel {} {}", e.getChannel(), BaseLogUtil.suppress(e.getCause(), ClosedChannelException.class));
        if (!(e.getCause() instanceof ClosedChannelException)) {
            l.warn("", e.getCause());
            e.getChannel().close();
        }
    }

    @Override
    public void channelOpen(final ChannelHandlerContext ctx, final ChannelStateEvent cse)
            throws Exception
    {
        Preconditions.checkState(_downstream == null);
        _downstream = cse.getChannel();
        l.info("downstream channel open {}", _downstream);
        _channelGroup.add(_downstream);
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent cse)
    {
        if (_upstream != null && _upstream.isConnected()) {
            final boolean downstreamWritable = ctx.getChannel().isWritable();
            if (_upstream.isReadable() != downstreamWritable) {
                l.info("{} upstream {}", downstreamWritable ? "resume" : "suspend", _upstream);
                _upstream.setReadable(downstreamWritable);
            }
        }
    }

    @Override
    public void channelDisconnected(final ChannelHandlerContext ctx, final ChannelStateEvent cse)
    {
        l.info("downstream channel disconnected {} {} {}", cse.getChannel(),
                _downstream.isReadable(), _downstream.isWritable());
        if (_upstream != null && _upstream.isConnected()) _upstream.close();
    }

    private static void addCookie(HttpResponse response, String name, String value)
    {
        Cookie cookie = new DefaultCookie(name, value);
        cookie.setPath("/");
        cookie.setSecure(true);
        response.headers().add(Names.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
    }

    private class HttpResponseProxyHandler extends IdleStateAwareChannelHandler
    {
        private final AtomicBoolean _closeIfIdle = new AtomicBoolean();
        private final AtomicLong _expectedResponses = new AtomicLong();
        private final AtomicBoolean _expectingResponseChunks = new AtomicBoolean();
        private final AtomicBoolean _expectingRequestChunks = new AtomicBoolean();

        private final AtomicBoolean _shutdown = new AtomicBoolean();
        private final AtomicReference<ChannelFuture> _shutdownFuture = new AtomicReference<>();

        @Override
        public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
            if (e instanceof ShutdownEvent) {
                if (!_shutdownFuture.compareAndSet(null, e.getFuture())) {
                    throw new IllegalStateException("multiple shutdown requests " + _downstream + " " + _upstream);
                }
                if (_expectedResponses.get() == 0) {
                    completeShutdown();
                }
            } else {
                super.handleDownstream(ctx, e);
            }
        }

        private void completeShutdown() {
            ChannelFuture f = _shutdownFuture.get();
            if (f != null && _shutdown.compareAndSet(false, true)) {
                _upstream.close();
                f.setSuccess();
            }
        }

        @Override
        public void writeRequested(ChannelHandlerContext ctx, MessageEvent me)
        {
            Object msg = me.getMessage();
            if (msg instanceof HttpRequest) {
                _expectedResponses.incrementAndGet();
                _expectingRequestChunks.set(((HttpRequest)msg).isChunked());
            } else if (msg instanceof HttpChunk) {
                // in case of a clean shutdown during an upload, discard the incoming chunks
                if (!_expectingRequestChunks.get()) {
                    l.debug("ignore chunk");
                    me.getFuture().setSuccess();
                    return;
                } else if (((HttpChunk)msg).isLast()) {
                    _expectingRequestChunks.set(false);
                }
                _closeIfIdle.set(false);
            }
            ctx.sendDownstream(me);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent me)
        {
            if (!_downstream.isConnected()) return;
            _closeIfIdle.set(false);

            final Channel upstream = me.getChannel();

            Object m = me.getMessage();
            if (m instanceof HttpResponse) {
                HttpResponse response = (HttpResponse)m;
                int status = response.getStatus().getCode();

                response.headers().set(HEADER_ROUTE, _endpoints.device(upstream).toStringFormal());

                // add list of alternate routes for flexible failure handling
                response.headers().set(HEADER_ALT_ROUTES,
                        _endpoints.alternateDevices(upstream)
                                .map(UniqueID::toStringFormal)
                                .collect(Collectors.joining(",")));

                // do not count 1xx provisional responses
                if (status < 100 || status >= 200) updateExpectations(response);
            } else if (m instanceof HttpChunk) {
                updateExpectations((HttpChunk)m);
            }

            _downstream.write(m);
        }

        private void updateExpectations(HttpResponse response)
        {
            if (_expectingResponseChunks.get()) {
                l.warn("new response before end of previous chunk stream");
            }

            if (_expectedResponses.get() <= 0) {
                l.warn("unexpected upstream response {}", response);
                return;
            }

            _expectingResponseChunks.set(response.isChunked());
            if (!response.isChunked()) {
                if (_expectedResponses.decrementAndGet() == 0) {
                    completeShutdown();
                }
            }
        }

        private void updateExpectations(HttpChunk chunk)
        {
            if (_expectedResponses.get() <= 0 || !_expectingResponseChunks.get()) {
                l.warn("unexpected upstream chunk {}", chunk.getContent().readableBytes());
                return;
            }

            if (chunk.isLast()) {
                if (_expectedResponses.decrementAndGet() == 0) {
                    completeShutdown();
                }
                _expectingResponseChunks.set(false);
                // resume processing requests once response is fully sent
                _downstream.setReadable(true);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        {
            l.warn("exception on upstream channel {} {}", e.getChannel(), e.getCause());
            if (!(e.getCause() instanceof ClosedChannelException)) {
                l.warn("", e.getCause());
                if (e.getChannel().isConnected()) e.getChannel().close();
            }
        }

        @Override
        public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent cse)
        {
            l.info("upstream channel open {}", cse.getChannel());
            _channelGroup.add(cse.getChannel());
        }

        @Override
        public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent cse)
        {
            if (!_downstream.isConnected()) return;
            final boolean upstreamWritable = ctx.getChannel().isWritable();
            l.info("{} downstream {}", upstreamWritable ? "resume" : "suspend", _downstream);
            // only alter downstream readability while waiting for request chunks
            if (_expectingRequestChunks.get()) {
                _downstream.setReadable(upstreamWritable);
            }
        }

        @Override
        public void setInterestOpsRequested(ChannelHandlerContext ctx, ChannelStateEvent cse) {
            if (!ctx.getChannel().isReadable() && ((int)cse.getValue() & Channel.OP_READ) != 0) {
                _closeIfIdle.set(false);
            }
            ctx.sendDownstream(cse);
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent cse)
        {
            l.info("upstream channel closed {} up: {} {} down: {} {} exp: {} {}", cse.getChannel(),
                    ctx.getChannel().isReadable(), ctx.getChannel().isWritable(),
                    _downstream.isReadable(), _downstream.isWritable(),
                    _expectedResponses.get(), _expectingRequestChunks.get());

            // avoid forwarding any response Netty may still deliver
            ctx.getPipeline().remove(this);

            // NB: only close downstream if there were missing responses
            // otherwise it is safe to keep downstream open and pick a new
            // upstream to service the next request
            if (_downstream.isConnected() && _expectedResponses.get() > 0) {
                cleanDownstreamClose();
            }
        }

        private void cleanDownstreamClose()
        {
            // don't close downstream if it switched to a different upstream already
            if (_shutdown.get()) return;
            // If a partial response was written we have no choice but to abruptly close the
            // connection. Otherwise we can do slightly better by returning a 502 for each
            // pipelined request.
            if (!_expectingResponseChunks.get()) {
                // if no response body was being streamed, send a 504 for each pipelined requests
                do {
                    sendError(_downstream, HttpResponseStatus.GATEWAY_TIMEOUT);
                } while (_expectedResponses.decrementAndGet() > 0);

                if (!_expectingRequestChunks.get()) return;
                // If a request body is still being streamed, make sure queued chunks are discarded
                // and close the connection once the write buffer is flushed
                _expectingRequestChunks.set(false);
            }
            _downstream.write(ChannelBuffers.EMPTY_BUFFER)
                    .addListener(ChannelFutureNotifier.CLOSE);
        }

        @Override
        public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
        {
            Channel c = ctx.getChannel();
            if (e.getState() == IdleState.READER_IDLE) {
                if (_expectingRequestChunks.get() && _expectedResponses.get() == 1) {
                    return;
                }
                l.info("read idle {} {}", _expectedResponses.get(), c);
                if (_expectedResponses.get() > 0) {
                    // if we paused upstream to avoid overloading downstream we shouldn't fault
                    // upstream for being idle...
                    if (!c.isReadable()) {
                        l.warn("lingering unreadable upstream {} [down: {} {}]",
                                c, _downstream.isWritable(), _downstream);
                        return;
                    }

                    // 2 strikes: we may get a read timeout immediately after the request
                    // is sent because the read timeout is not reset when a message is sent
                    // This makes the timeout effectively a random number between 10s and 20s
                    if (_closeIfIdle.compareAndSet(false, true)) return;

                    l.info("close idle upstream {}", c);
                    // avoid forwarding any response Netty may still deliver
                    ctx.getPipeline().remove(this);
                    cleanDownstreamClose();
                    // close connection if requests remain unanswered for too long
                    // to prevent bad state buildup
                    c.close();
                } else {
                    _closeIfIdle.set(false);
                }
            } else if (e.getState() == IdleState.WRITER_IDLE) {
                if (_expectedResponses.get() > 0 && !_expectingRequestChunks.get()) return;

                l.info("write idle {}", c);

                // if we paused downstream to avoid overloading upstream we shouldn't fault
                // downstream for being idle...
                if (!c.isWritable()) {
                    l.warn("lingering unwritable upstream {} [down: {} {}]",
                            c, _downstream.isReadable(), _downstream);
                    return;
                }

                // close upstream connection if no requests have been forwarded during the last 30s
                c.close();
            }
        }
    }
}
