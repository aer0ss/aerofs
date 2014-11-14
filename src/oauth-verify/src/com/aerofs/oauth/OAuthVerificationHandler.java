package com.aerofs.oauth;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.net.NettyUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringEncoder;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;

import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.util.Queue;

/**
 * Simple Netty channel handler to perform asynchronous OAuth token verification
 *
 * Sits on top of an HttpClientCodec, sends appropriate HTTP request for each VerifyTokenRequest,
 * decodes the response body using Gson and forwards the decoded response to a future.
 */
public class OAuthVerificationHandler<T> extends IdleStateAwareChannelHandler
{
    private final static Logger l = Loggers.getLogger(OAuthVerificationHandler.class);

    static class VerifyTokenRequest<T>
    {
        final String auth;
        final String accessToken;

        final SettableFuture<T> future = SettableFuture.create();

        VerifyTokenRequest(String accessToken, String auth)
        {
            this.auth = auth;
            this.accessToken = accessToken;
        }
    }

    public static class UnexpectedResponse extends Exception
    {
        private static final long serialVersionUID = 0L;
        public final int statusCode;
        public UnexpectedResponse(int code) { statusCode = code; }
    }

    private final String _host;
    private final String _path;
    private final Class<T> _class;

    private final Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final Queue<VerifyTokenRequest<T>> _requests = Queues.newConcurrentLinkedQueue();

    OAuthVerificationHandler(URI endpoint, Class<T> clazz)
    {
        _host = endpoint.getHost();
        _path = endpoint.getPath();
        _class = clazz;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        l.warn("ex ", BaseLogUtil.suppress(e.getCause(), ClosedChannelException.class));
        ctx.getChannel().close();
    }

    @Override
    public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
    {
        l.info("timeout");
        ctx.getChannel().close();
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        l.info("closed");
        failRequests();
    }

    private void failRequests()
    {
        while (!_requests.isEmpty()) {
            VerifyTokenRequest<T> r = _requests.remove();
            l.info("fail {}", r);
            r.future.setException(new ClosedChannelException());
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me)
    {
        HttpResponse msg = (HttpResponse)me.getMessage();
        String content = BaseUtil.utf2string(NettyUtil.toByteArray(msg.getContent()));

        l.debug("response {}", content);

        VerifyTokenRequest<T> req = Preconditions.checkNotNull(_requests.peek());
        T response;
        try {
            response = _gson.fromJson(content, _class);
        } catch (JsonParseException e) {
            l.warn("invalid JSON");
            response = null;
        }
        if (response != null) {
            req.future.set(response);
        } else {
            l.warn("unexpected {}", msg.getStatus());
            req.future.setException(new UnexpectedResponse(msg.getStatus().getCode()));
        }
        // IMPORTANT: dequeue request *AFTER* setting future, to ensure that if an exception
        // occurs before that, the exceptionCaught handler will still be able to set it
        _requests.remove();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent me)
    {
        VerifyTokenRequest<T> req = (VerifyTokenRequest<T>)me.getMessage();

        QueryStringEncoder encoder = new QueryStringEncoder(_path);
        encoder.addParam("access_token", req.accessToken);
        HttpRequest http = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                encoder.toString());
        http.setHeader(Names.HOST, _host);
        http.setHeader(Names.AUTHORIZATION, req.auth);

        _requests.add(req);
        me.getFuture().addListener(cf -> {
            if (!cf.isSuccess()) {
                l.warn("failed to write ", BaseLogUtil.suppress(cf.getCause()));
                failRequests();
            }
        });
        ctx.sendDownstream(new DownstreamMessageEvent(ctx.getChannel(), me.getFuture(), http, null));
    }
}
