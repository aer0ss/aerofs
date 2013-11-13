package com.aerofs.havre.proxy;

import com.aerofs.base.Loggers;
import com.aerofs.base.Version;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.havre.Authenticator;
import com.aerofs.havre.Authenticator.UnauthorizedUserException;
import com.aerofs.havre.EndpointConnector;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.jboss.netty.channel.Channel;
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
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.channels.ClosedChannelException;
import java.util.Map;

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

    private final Authenticator _auth;
    private final EndpointConnector _endpoints;
    private final ChannelGroup _channelGroup;

    private AuthenticatedPrincipal _principal;

    private Channel _downstream;
    private Channel _upstream;

    public HttpRequestProxyHandler(Authenticator auth, EndpointConnector endpoints, ChannelGroup channelGroup)
    {
        _auth = auth;
        _endpoints = endpoints;
        _channelGroup = channelGroup;
    }

    private static final String COOKIE_SERVER = "server";
    private static final String HEADER_CONSISTENCY = "X-Aero-Consistency";

    private static Map<String, String> getCookies(HttpRequest r)
    {
        Map<String, String> m = Maps.newHashMap();
        String cookie = r.getHeader(Names.COOKIE);
        if (cookie != null) {
            for (Cookie c : new CookieDecoder().decode(cookie)) m.put(c.getName(), c.getValue());
        }
        return m;
    }

    private static @Nullable DID getLastServer(Map<String, String> c)
    {
        String id = c.get(COOKIE_SERVER);
        try {
            return id != null ? new DID(id) : null;
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
        Map<String, String> cookies = getCookies(req);

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

        DID did = getLastServer(cookies);
        Version version = Version.fromRequestPath(req.getUri());
        boolean strictConsistency = shouldEnforceStrictConsistency(req) && did != null;

        l.info("{} {} {}", did, strictConsistency, version);

        return _endpoints.connect(_principal, did, strictConsistency, version,
                Channels.pipeline(
                        new HttpClientCodec(),
                        new HttpResponseProxyHandler()
                ));
    }

    private static boolean shouldEnforceStrictConsistency(HttpRequest r)
    {
        String flag = r.getHeader(HEADER_CONSISTENCY);
        return flag != null && "strict".equals(flag);
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
            addCookie(response, COOKIE_SERVER, "");
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

    private class HttpResponseProxyHandler extends SimpleChannelUpstreamHandler
    {
        @Override
        public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent me)
                throws Exception
        {
            if (!_downstream.isConnected()) return;

            final Channel upstream = me.getChannel();

            Object m = me.getMessage();
            if (m instanceof HttpResponse) {
                HttpResponse response = (HttpResponse)m;

                // add server id
                addCookie(response, COOKIE_SERVER, _endpoints.device(upstream).toStringFormal());
            }

            _downstream.write(m);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception
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
            if (_downstream.isConnected()) _downstream.close();
            ctx.getPipeline().remove(this);
        }
    }
}
