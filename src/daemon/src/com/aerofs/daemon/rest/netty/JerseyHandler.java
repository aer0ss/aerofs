/*
 * Copyright (c) Air Computing Inc., 2013.
 */
package com.aerofs.daemon.rest.netty;

import com.aerofs.base.Loggers;
import com.sun.jersey.core.header.InBoundHeaders;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.WebApplication;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class JerseyHandler extends SimpleChannelUpstreamHandler
{
    private static final Logger l = Loggers.getLogger(JerseyHandler.class);

    private final WebApplication _application;

    // NB: for some reason Jersey's ContainerRequest requires a base URI to be provided
    // in all cases although we absolutely don't need it for any reason whatsoever...
    private final URI _baseURI = URI.create("https://dummy/");

    public JerseyHandler(final WebApplication application)
    {
        _application = application;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext context, final MessageEvent me)
            throws URISyntaxException, IOException
    {
        // TODO: support chunked requests (for PUT/POST)
        HttpRequest request = (HttpRequest) me.getMessage();
        URI requestUri = URI.create(_baseURI + request.getUri().substring(1));

        ContainerRequest cRequest = new ContainerRequest(_application,
                request.getMethod().getName(), _baseURI, requestUri, getHeaders(request),
                new ChannelBufferInputStream(request.getContent()));

        _application.handleRequest(cRequest, new JerseyResponseWriter(me.getChannel()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        // Close the connection when an exception is raised.
        l.warn("Rest service: unexpected exception:", e.getCause());
        e.getChannel().close();
    }

    private InBoundHeaders getHeaders(final HttpRequest request)
    {
        InBoundHeaders headers = new InBoundHeaders();
        for (String name : request.getHeaderNames()) {
            headers.put(name, request.getHeaders(name));
        }
        return headers;
    }
}
