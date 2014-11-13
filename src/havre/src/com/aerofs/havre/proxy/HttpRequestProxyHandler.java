package com.aerofs.havre.proxy;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.havre.Authenticator;
import com.aerofs.havre.Authenticator.UnauthorizedUserException;
import com.aerofs.havre.EndpointConnector;
import com.aerofs.havre.Version;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureNotifier;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultCookie;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.timeout.IdleState;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
    private final EndpointConnector _endpoints;
    private final ChannelGroup _channelGroup;

    private AuthenticatedPrincipal _principal;

    private Channel _downstream;
    private Channel _upstream;

    public HttpRequestProxyHandler(Timer timer, Authenticator auth, EndpointConnector endpoints,
            ChannelGroup channelGroup)
    {
        _timer = timer;
        _auth = auth;
        _endpoints = endpoints;
        _channelGroup = channelGroup;
    }

    static long READ_TIMEOUT = 10;
    static long WRITE_TIMEOUT = 30;
    static TimeUnit TIMEOUT_UNIT = TimeUnit.SECONDS;

    private static final String COOKIE_ROUTE = "route";
    private static final String HEADER_ROUTE =  "Route";
    private static final String HEADER_CONSISTENCY = "Endpoint-Consistency";
    private static final String HEADER_ALT_ROUTES = "Alternate-Routes";

    private static Map<String, String> getCookies(HttpRequest r)
    {
        Map<String, String> m = Maps.newHashMap();
        String cookie = r.getHeader(Names.COOKIE);
        if (cookie != null) {
            for (Cookie c : new CookieDecoder().decode(cookie)) m.put(c.getName(), c.getValue());
        }
        return m;
    }

    private static @Nullable DID parseDID(String route)
    {
        try {
            return route != null ? new DID(route) : null;
        } catch (ExFormatError e) {
            return null;
        }
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent me)
            throws Exception
    {
        final Object message = me.getMessage();
        final Channel downstream = me.getChannel();

        if (message instanceof HttpRequest) {
            HttpRequest req = (HttpRequest)message;
            if (_upstream == null || !_upstream.isConnected()) {
                if ((_upstream = getUpstreamChannel(req)) == null) {
                    sendError(downstream, _principal == null
                            ? HttpResponseStatus.UNAUTHORIZED
                            : HttpResponseStatus.SERVICE_UNAVAILABLE);
                    return;
                }
            }

            // remove gateway-specific header
            req.removeHeader(Names.COOKIE);
            req.removeHeader(HEADER_ROUTE);
            req.removeHeader(HEADER_CONSISTENCY);

            _upstream.write(message);
        } else {
            // HttpChunk
            if (_upstream == null) {
                l.warn("ignore incoming message on {}", downstream);
            } else {
                _upstream.write(message);
            }
        }
    }

    private @Nullable Channel getUpstreamChannel(HttpRequest req)
    {
        // First request in a given gateway connection
        //  - derive user id from OAuth token to pick an appropriate server
        //    and avoid load from unauthorized clients
        if (_principal == null) {
            try {
                _principal = _auth.authenticate(req);
            } catch (UnauthorizedUserException e) {
                return null;
            }
        }

        Version version = Version.fromRequestPath(req.getUri());

        // The Route header overrides any cookie and enforces strict consistency
        boolean strictConsistency;
        DID did = parseDID(req.getHeader(HEADER_ROUTE));
        if (did != null) {
            strictConsistency = true;
        } else {
            did = parseDID(getCookies(req).get(COOKIE_ROUTE));
            strictConsistency = shouldEnforceStrictConsistency(req);
        }

        l.info("{} {} {}", did, strictConsistency, version);

        return _endpoints.connect(_principal, did, strictConsistency && did != null, version,
                Channels.pipeline(
                        new IdleStateHandler(_timer, READ_TIMEOUT, WRITE_TIMEOUT, 0, TIMEOUT_UNIT),
                        new HttpClientCodec(),
                        new HttpResponseProxyHandler()
                ));
    }

    private static boolean shouldEnforceStrictConsistency(HttpRequest r)
    {
        String flag = r.getHeader(HEADER_CONSISTENCY);
        return flag != null && "strict".equalsIgnoreCase(flag);
    }

    private void sendError(Channel downstream, HttpResponseStatus status)
    {
        if (downstream.isConnected()) {
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
            response.setHeader(Names.CONTENT_LENGTH, 0);
            response.setHeader(Names.CACHE_CONTROL, Values.NO_CACHE + "," + Values.NO_TRANSFORM);
            response.setHeader(Names.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            if (status == HttpResponseStatus.UNAUTHORIZED) {
                response.setHeader(Names.WWW_AUTHENTICATE, "Bearer realm=\"AeroFS\"");
            }
            // reset server id cookie
            addCookie(response, COOKIE_ROUTE, "");
            downstream.write(response);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e)
            throws Exception
    {
        l.warn("exception on downstream channel {} {}", e.getChannel(), e.getCause());
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
        if (_upstream != null && _upstream.isConnected()
                && _upstream.isReadable() != ctx.getChannel().isWritable()) {
            l.info("{} upstream {}", _upstream.isReadable() ? "suspending" : "resuming", _upstream);
            _upstream.setReadable(ctx.getChannel().isWritable());
        }
    }

    @Override
    public void channelDisconnected(final ChannelHandlerContext ctx, final ChannelStateEvent cse)
    {
        l.info("downstream channel disconnected {} {} {}", cse.getChannel(),
                _downstream.isReadable(), _downstream.isWritable());
        if (_upstream != null && _upstream.isConnected()) _upstream.close();
    }

    private void addCookie(HttpResponse response, String name, String value)
    {
        CookieEncoder encoder = new CookieEncoder(true);
        Cookie cookie = new DefaultCookie(name, value);
        cookie.setPath("/");
        cookie.setSecure(true);
        encoder.addCookie(cookie);
        response.addHeader(Names.SET_COOKIE, encoder.encode());
    }

    private class HttpResponseProxyHandler extends IdleStateAwareChannelHandler
    {
        private final AtomicBoolean _closeIfIdle = new AtomicBoolean();
        private final AtomicLong _expectedResponses = new AtomicLong();
        private final AtomicBoolean _streamingChunks = new AtomicBoolean();

        @Override
        public void writeRequested(ChannelHandlerContext ctx, MessageEvent me)
        {
            Object msg = me.getMessage();
            if (msg instanceof HttpRequest) {
                _expectedResponses.incrementAndGet();
            }
            ctx.sendDownstream(me);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent me)
        {
            if (!_downstream.isConnected()) return;

            final Channel upstream = me.getChannel();

            Object m = me.getMessage();
            if (m instanceof HttpResponse) {
                HttpResponse response = (HttpResponse)m;
                int status = response.getStatus().getCode();

                // add route id for session consistency
                addCookie(response, COOKIE_ROUTE, _endpoints.device(upstream).toStringFormal());

                // add list of alternate routes for flexible failure handling
                response.setHeader(HEADER_ALT_ROUTES, join(_endpoints.alternateDevices(upstream)));

                // do not count 1xx provisional responses
                if (status < 100 || status >= 200) updateExpectations(response);
            } else if (m instanceof HttpChunk) {
                updateExpectations((HttpChunk)m);
            }

            _downstream.write(m);
        }

        private void updateExpectations(HttpResponse response)
        {
            if (_streamingChunks.get()) {
                l.warn("new response before end of previous chunk stream");
            }

            if (_expectedResponses.get() <= 0) {
                l.warn("unexpected upstream response {}", response);
                return;
            }

            _streamingChunks.set(response.isChunked());
            if (!response.isChunked()) _expectedResponses.decrementAndGet();
        }

        private void updateExpectations(HttpChunk chunk)
        {
            if (_expectedResponses.get() <= 0 || !_streamingChunks.get()) {
                l.warn("unexpected upstream chunk {}", chunk.getContent().readableBytes());
                return;
            }

            if (chunk.isLast()) {
                _expectedResponses.decrementAndGet();
                _streamingChunks.set(false);
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
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent cse)
        {
            l.info("upstream channel closed {} {} {}", cse.getChannel(),
                    _upstream.isReadable(), _upstream.isWritable());

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
            // If a partial response was written we have no choice but to abruptly close the
            // connection. Otherwise we can do slightly better by returning a 502 for each
            // pipelined request.
            if (!_streamingChunks.get()) {
                // if no response body was being streamed, send a 504 for each pipelined requests
                do {
                    sendError(_downstream, HttpResponseStatus.GATEWAY_TIMEOUT);
                } while (_expectedResponses.decrementAndGet() > 0);
            }
            _downstream.write(ChannelBuffers.EMPTY_BUFFER)
                    .addListener(ChannelFutureNotifier.CLOSE);
        }

        @Override
        public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
        {
            if (e.getState() == IdleState.READER_IDLE) {
                l.info("read idle {}", _expectedResponses.get());
                if (_expectedResponses.get() > 0) {
                    // 2 strikes: we may get a read timeout immediately after the request
                    // is sent because the read timeout is not reset when a message is sent
                    // This makes the timeout effectively a random number between 10s and 20s
                    if (_closeIfIdle.compareAndSet(false, true)) return;

                    l.info("close idle upstream {}", _upstream);
                    // avoid forwarding any response Netty may still deliver
                    ctx.getPipeline().remove(this);
                    cleanDownstreamClose();
                    // close connection if requests remain unanswered for too long
                    // to prevent bad state buildup
                    _upstream.close();
                } else {
                    _closeIfIdle.set(false);
                }
            } else if (e.getState() == IdleState.WRITER_IDLE) {
                if (_expectedResponses.get() > 0) return;

                l.info("write idle {}");
                // close upstream connection if no requests have been forwarded during the last 30s
                _upstream.close();
            }
        }
    }

    private static String join(Iterable<? extends UniqueID> ids)
    {
        String s = "";
        for (UniqueID i : ids) {
            if (!s.isEmpty()) s += ",";
            s += i.toStringFormal();
        }
        return s;
    }
}
