package com.aerofs.oauth;

import com.aerofs.base.BaseUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringEncoder;

import java.nio.channels.ClosedChannelException;
import java.util.Queue;

/**
 * Simple Netty channel handler to perform asynchronous OAuth token verification
 *
 * Sits on top of an HttpClientCodec, sends appropriate HTTP request for each VerifyTokenRequest,
 * decodes the response body using Gson and forwards the decoded repsonse to a future.
 *
 * TODO: auto-close connection when idle
 */
public class OAuthVerificationHandler<T> extends SimpleChannelHandler
{
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

    private final String _path;
    private final Class<T> _class;

    private final Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final Queue<VerifyTokenRequest<T>> _requests = Queues.newConcurrentLinkedQueue();

    OAuthVerificationHandler(String path, Class<T> clazz)
    {
        _path = path;
        _class = clazz;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        ctx.getChannel().close();
        while (!_requests.isEmpty()) {
            _requests.poll().future.setException(new ClosedChannelException());
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me)
    {
        HttpResponse msg = (HttpResponse)me.getMessage();
        String content = BaseUtil.utf2string(msg.getContent().array());
        VerifyTokenRequest<T> req = Preconditions.checkNotNull(_requests.poll());
        T response = _gson.fromJson(content, _class);
        if (response != null) {
            req.future.set(response);
        } else {
            req.future.setException(new UnexpectedResponse(msg.getStatus().getCode()));
        }
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
        http.setHeader(Names.AUTHORIZATION, req.auth);

        _requests.add(req);
        ctx.sendDownstream(
                new DownstreamMessageEvent(ctx.getChannel(), me.getFuture(), http, null));
    }
}
