/*
 * Copyright (c) Air Computing Inc., 2013.
 */
package com.aerofs.restless.netty;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.sun.jersey.core.header.InBoundHeaders;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.WebApplication;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ClosedChannelException;

public class JerseyHandler extends SimpleChannelUpstreamHandler
{
    private static final Logger l = Loggers.getLogger(JerseyHandler.class);

    private final Configuration _config;
    private final WebApplication _application;

    private ChunkedRequestInputStream _chunkedRequestInputStream;

    public JerseyHandler(WebApplication application, Configuration config)
    {
        _config = config;
        _application = application;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent me)
            throws URISyntaxException, IOException
    {
        Channel channel = ctx.getChannel();
        if (_chunkedRequestInputStream != null) {
            HttpChunk chunk = (HttpChunk)me.getMessage();
            if (chunk.isLast()) {
                _chunkedRequestInputStream.end();
                _chunkedRequestInputStream = null;
            } else {
                _chunkedRequestInputStream.offer(chunk.getContent());
            }
        } else {
            HttpRequest request = (HttpRequest)me.getMessage();

            if (HttpHeaders.is100ContinueExpected(request)) {
                ctx.getChannel().write(new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                        HttpResponseStatus.CONTINUE));
            }

            final InputStream content;
            if (request.isChunked()) {
                l.info("chunked request {}", request);
                content = _chunkedRequestInputStream = new ChunkedRequestInputStream(channel);
            } else {
                l.info("simple request {}", request);
                content = new ChannelBufferInputStream(request.getContent());
            }
            handleRequest(me.getChannel(), request, content);
        }
    }

    private void handleRequest(Channel c, HttpRequest request, InputStream content)
    {
        URI requestUri = URI.create(Service.DUMMY_BASE_URI + request.getUri().substring(1));

        ContainerRequest cRequest = new ContainerRequest(_application,
                request.getMethod().getName(), Service.DUMMY_BASE_URI, requestUri, getHeaders(request),
                content);

        try {
            _application.handleRequest(cRequest, new JerseyResponseWriter(c, request, _config));
        } catch (Exception e) {
            // When a WebApplicationException (or really any exception whatsoever) is thrown
            // after the response is committed (i.e. the first byte has been written on a Netty
            // channel), it is no longer possible to send an error response because HTTP does
            // not have any mecanism to say 'wait, I cannot actually service this request'
            //
            // This case is most likely to occur when a large download is interrupted because,
            // the underlying file changed.
            //
            // In any case, the only correct way to treat such an exception is to forcefully
            // close the connection which the client which the client will interpret as an
            // unspecified error.
            l.warn("exception after response committed: ",
                    BaseLogUtil.suppress(e, ClosedChannelException.class));
            c.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        // Close the connection when an exception is raised.
        l.warn("unexpected exception:",
                BaseLogUtil.suppress(e.getCause(), ClosedChannelException.class));
        if (_chunkedRequestInputStream != null) _chunkedRequestInputStream.fail();
        e.getChannel().close();
    }

    private static InBoundHeaders getHeaders(final HttpRequest request)
    {
        InBoundHeaders headers = new InBoundHeaders();
        for (String name : request.getHeaderNames()) {
            headers.put(name, request.getHeaders(name));
        }
        return headers;
    }
}
