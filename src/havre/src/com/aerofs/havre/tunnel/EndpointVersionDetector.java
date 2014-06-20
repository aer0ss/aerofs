package com.aerofs.havre.tunnel;

import com.aerofs.base.Version;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * HTTP-based endpoint version detection
 *      - GET /version on the given channel
 *      - expect a JSON-serialized {@link Version} object in the response
 */
public class EndpointVersionDetector implements ChannelPipelineFactory
{
    private final Gson _gson = new GsonBuilder()
            .create();

    @Override
    public ChannelPipeline getPipeline()
    {
        ChannelPipeline p = Channels.pipeline();
        p.addLast("http", new HttpClientCodec());
        p.addLast("aggregator", new HttpChunkAggregator(128));
        p.addLast("handler", new Handler());
        return p;
    }

    /**
     * Asynchronous endpoint version detection
     *
     * @param c a throwaway Channel to an endpoint
     * @return future for the detected Version
     */
    ListenableFuture<Version> detectHighestSupportedVersion(Channel c)
    {
        Handler h = (Handler)checkNotNull(c.getPipeline().get("handler"));
        c.write(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/version"));
        return h._future;
    }

    private class Handler extends SimpleChannelUpstreamHandler
    {
        private final SettableFuture<Version> _future = SettableFuture.create();

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) throws IOException
        {
            HttpResponse response = (HttpResponse)me.getMessage();
            int status = response.getStatus().getCode();
            if (status == HttpResponseStatus.OK.getCode()) {
                InputStream in = new ChannelBufferInputStream(response.getContent());
                Version version = _gson.fromJson(new InputStreamReader(in), Version.class);
                if (version != null) {
                    _future.set(version);
                } else {
                    _future.setException(new Exception("invalid response content"));
                }
            } else {
                _future.setException(new Exception("unexpected response status: " + status));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        {
            _future.setException(e.getCause());
        }
    }
}
