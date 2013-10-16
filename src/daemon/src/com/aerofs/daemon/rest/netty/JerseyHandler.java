/*
 * Copyright (c) Air Computing Inc., 2013.
 */
package com.aerofs.daemon.rest.netty;

import com.aerofs.base.Loggers;
import com.aerofs.lib.log.LogUtil;
import com.sun.jersey.core.header.InBoundHeaders;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.WebApplication;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;

import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ClosedChannelException;

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

        Channel inbound = me.getChannel();
        boolean keepAlive = HttpHeaders.isKeepAlive(request);
        try {
            // suspend processing of incoming messages to ensure responses are sent back in order
            // (HTTP pipelining). JerseyResponseWriter is responsible for re-enabling reads
            inbound.setReadable(false);

            _application.handleRequest(cRequest, new JerseyResponseWriter(inbound, keepAlive));
        } catch (WebApplicationException e) {
            // When a WebApplicationException (or really any exception whatsoever) is thrown
            // after the response is committed (i.e. the first byte has been written on a Netty
            // channel), it is no longer possible to send an error response because HTTP does not
            // have any mecanism to say 'wait, I cannot actually service this request'
            //
            // This case is most likely to occur when a large download is interrupted because,
            // the underlying file changed.
            //
            // In any case, the only correct way to treat such an exception is to forcefully close
            // the connection which the client which the client will interpret as an unspecified
            // error.
            l.warn("exception after response committed: ",
                    LogUtil.suppress(e, ClosedChannelException.class));
            inbound.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        // Close the connection when an exception is raised.
        l.warn("Rest service: unexpected exception:",
                LogUtil.suppress(e.getCause(), ClosedChannelException.class));
        if (e.getChannel().isConnected()) {
            e.getChannel().close();
        }
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
