/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.testlib;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.google.common.util.concurrent.AbstractIdleService;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;

import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Simple HTTP server class used to test HTTP clients
 *
 * Usage:
 *  1. Construct a new instance of this class with a port you'd like to use
 *  2. Call startAndWait()
 *  3. Call setRequestProcessor to specify how the server should handle a request. You can call
 *     this method multiple times to test different behaviors
 *  4. Call shutDown() at the end of your tests.
 *
 * Sample code:
 *
 *      HttpServerTest _server;
 *
 *      @Before
 *      public void setUp()
 *      {
 *          _server = new HttpServerTest(8080);
 *          _server.startAndWait();
 *      }
 *
 *      @After
 *      public void tearDown() throws Exception
 *      {
 *          _server.shutDown();
 *      }
 *
 *      @Test
 *      public void shouldDoSomeTest()
 *      {
 *          _server.setRequestProcessor(...how the server should hanlde the request...)
 *      }
 *
 * N.B. it only support content up to the size {@link #MAX_CONTENT_LENGTH}
 *
 */
public class SimpleHttpServer extends AbstractIdleService
{
    public interface RequestProcessor
    {
        public HttpResponse process(HttpRequest request) throws Exception;
    }

    private static final Logger l = Loggers.getLogger(SimpleHttpServer.class);
    private static final int MAX_CONTENT_LENGTH = 2 * C.MB;

    private final ChannelFactory _factory = new NioServerSocketChannelFactory(
            Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
    private final ServerBootstrap _bootstrap = new ServerBootstrap(_factory);
    private final SocketAddress _address;
    private RequestProcessor _requestProcessor;

    /**
     * Constructs a new HTTP server listening to 'port'
     */
    public SimpleHttpServer(int port)
    {
        _address = new InetSocketAddress("localhost", port);
    }

    /**
     * Sets how the HTTP server will reply to requests.
     * This method can be called multiple time between requests
     */
    public void setRequestProcessor(RequestProcessor requestProcessor)
    {
        _requestProcessor = requestProcessor;
    }

    @Override
    protected void startUp() throws Exception
    {
        _bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                return Channels.pipeline(
                        new HttpServerCodec(),
                        new HttpChunkAggregator(MAX_CONTENT_LENGTH),
                        new Handler()
                );
            }
        });

        _bootstrap.bind(_address);

        l.debug("HTTP server listening to {}", _address);
    }

    @Override
    public void shutDown() throws Exception
    {
        _factory.releaseExternalResources();
    }

    class Handler extends SimpleChannelHandler
    {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception
        {
            HttpResponse response = _requestProcessor.process((HttpRequest)e.getMessage());
            writeAndClose(e.getChannel(), response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception
        {
            l.warn("Exception caught: ", e.getCause());
            // Send a 500 reply and close the channel
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            writeAndClose(e.getChannel(), response);
            e.getChannel().close();
        }

        private void writeAndClose(Channel channel, HttpResponse response)
        {
            channel.write(response).addListener(future -> future.getChannel().close());
        }
    }
}
