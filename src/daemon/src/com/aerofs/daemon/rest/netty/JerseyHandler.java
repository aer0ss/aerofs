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
    private final URI _baseURI;

    public JerseyHandler(final WebApplication application, URI baseURI)
    {
        _application = application;
        _baseURI = baseURI;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext context,
            final MessageEvent messageEvent)
            throws URISyntaxException, IOException
    {
        HttpRequest request = (HttpRequest) messageEvent.getMessage();

        URI requestUri = URI.create(_baseURI + request.getUri().substring(1));

        ContainerRequest cRequest = new ContainerRequest(_application,
                request.getMethod().getName(), _baseURI, requestUri, getHeaders(request),
                new ChannelBufferInputStream(request.getContent()));

        _application.handleRequest(cRequest, new JerseyResponseWriter(messageEvent.getChannel()));
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
