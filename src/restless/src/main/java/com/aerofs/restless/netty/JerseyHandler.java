/*
 * Copyright (c) Air Computing Inc., 2013.
 */
package com.aerofs.restless.netty;

import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.sun.jersey.core.header.InBoundHeaders;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.WebApplication;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.execution.ChannelEventRunnable;
import org.jboss.netty.handler.execution.ChannelUpstreamEventRunnable;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.Executor;

public class JerseyHandler extends SimpleChannelUpstreamHandler
{
    private static final Logger l = LoggerFactory.getLogger(JerseyHandler.class);

    private final Configuration _config;
    private final @Nullable Executor _executor;
    private final WebApplication _application;

    private ChunkedRequestInputStream _chunkedRequestInputStream;

    public JerseyHandler(WebApplication application, @Nullable Executor executor,
            Configuration config)
    {
        _config = config;
        _executor = executor;
        _application = application;
        if (executor != null & !(executor instanceof OrderedMemoryAwareThreadPoolExecutor)) {
            l.warn("unordered thread pool: race between pipelined requests may cause problems");
        }
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (_executor != null && e instanceof ChannelStateEvent) {
            // pass state events through the executor
            // this is mostly to ensure that OrderedMemoryAwareThreadPoolExecutor detects closed
            // channels and cleans up any reference to them to avoid memory leaks
            _executor.execute(new ChannelUpstreamEventRunnable(ctx, e, _executor));
        } else {
            super.handleUpstream(ctx, e);
        }
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent me)
            throws URISyntaxException, IOException
    {
        Channel channel = ctx.getChannel();
        Object msg = me.getMessage();
        if (_chunkedRequestInputStream != null) {
            HttpChunk chunk = (HttpChunk)msg;
            if (chunk.isLast()) {
                l.debug("last chunk");
                _chunkedRequestInputStream.end();
                _chunkedRequestInputStream = null;
            } else {
                l.debug("chunk {}", chunk.getContent().readableBytes());
                _chunkedRequestInputStream.offer(chunk.getContent());
            }
        } else if (msg instanceof HttpRequest) {
            final HttpRequest request = (HttpRequest)msg;

            // for chunked messages, defer sending 100 Continue to first attempt to read content
            boolean sendContinue = HttpHeaders.is100ContinueExpected(request);

            final InputStream content;
            if (request.isChunked()) {
                l.debug("chunked request {}", request);
                content = _chunkedRequestInputStream =
                        new ChunkedRequestInputStream(channel, sendContinue);
            } else {
                l.debug("simple request {}", request);
                content = new ChannelBufferInputStream(request.getContent());
                if (sendContinue) {
                    channel.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                            HttpResponseStatus.CONTINUE));
                }
            }

            if (_executor != null) {
                _executor.execute(new ChannelEventRunnable(ctx, me, _executor) {
                    @Override
                    public void doRun()
                    {
                        handleRequest(me.getChannel(), request, content);
                    }
                });
            } else {
                handleRequest(me.getChannel(), request, content);
            }
        } else {
            l.warn("unexpected msg {}", msg);
        }
    }

    private void handleRequest(Channel c, HttpRequest request, InputStream content)
    {
        URI requestUri = URI.create(Service.DUMMY_BASE_URI + request.getUri().substring(1));

        ContainerRequest cRequest = new ContainerRequest(_application,
                request.getMethod().getName(), Service.DUMMY_BASE_URI, requestUri, getHeaders(request),
                content);

        JerseyResponseWriter w = new JerseyResponseWriter(c, request, _config);
        try {
            _application.handleRequest(cRequest, w);
        } catch (Exception e) {
            // When a WebApplicationException (or really any exception whatsoever) is thrown
            // after the response is committed (i.e. the first byte has been written on a Netty
            // channel), it is no longer possible to send an error response because HTTP does
            // not have any mechanism to say 'wait, I cannot actually service this request'
            //
            // The only correct way to treat such an exception is to forcefully close the connection
            // which the client which the client will interpret as an unspecified error.
            l.warn("exception after response committed: {}", w.partialResponse(),
                    suppress(e, ClosedChannelException.class));
            c.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        // Close the connection when an exception is raised.
        l.warn("unexpected exception:",
                suppress(e.getCause(), ClosedChannelException.class));
        if (_chunkedRequestInputStream != null) _chunkedRequestInputStream.fail();
        e.getChannel().close();
    }

    private static InBoundHeaders getHeaders(final HttpRequest request)
    {
        InBoundHeaders headers = new InBoundHeaders();
        for (String name : request.headers().names()) {
            headers.put(name, request.headers().getAll(name));
        }
        return headers;
    }

    /**
     * Suppress the stack trace for the given throwable.
     *
     * This is useful to pass an abbreviated exception on to the logging subsystem.
     */
    private static <T extends Throwable> T suppress(T throwable)
    {
        Throwable t = throwable;
        do {
            t.setStackTrace(new StackTraceElement[0]);
            t = t.getCause();
        } while (t != null);

        return throwable;
    }

    /**
     * Suppress the stack trace if the throwable is an instance of one of the given
     * exception types.
     */
    private static <T extends Throwable> T suppress(T throwable, Class<?>... suppressTypes)
    {
        for (Class<?> clazz : suppressTypes) {
            if (clazz.isInstance(throwable)) {
                return suppress(throwable);
            }
        }
        return throwable;
    }
}
