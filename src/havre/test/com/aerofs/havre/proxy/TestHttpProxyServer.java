/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.havre.proxy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.aerofs.base.BaseUtil;
import com.aerofs.ids.DID;
import com.aerofs.havre.Authenticator;
import com.aerofs.havre.Authenticator.UnauthorizedUserException;
import com.aerofs.havre.EndpointConnector;
import com.aerofs.havre.Version;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.testlib.AbstractBaseTest;
import com.jayway.restassured.RestAssured;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DefaultChannelConfig;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.hamcrest.Matchers.*;
import static org.mockito.Matchers.eq;

public class TestHttpProxyServer extends AbstractBaseTest
{
    @Mock Authenticator auth;
    @Mock EndpointConnector connector;

    HttpProxyServer proxy;
    Timer timer;

    static {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();
        ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();
        console.setContext(context);
        console.setEncoder(newEncoder(context));
        console.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(console);
        context.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.DEBUG);
    }

    private static PatternLayoutEncoder newEncoder(LoggerContext context)
    {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern("%-5level [%date{ISO8601, UTC}] [%-8.8thread] %c{0}: %m%n%xEx");
        encoder.setContext(context);
        encoder.setCharset(BaseUtil.CHARSET_UTF);
        encoder.start();
        return encoder;
    }

    @Before
    public void setUp()
    {
        // tweak timeout to prevent 504 test from taking ages
        HttpRequestProxyHandler.READ_TIMEOUT = 100;
        HttpRequestProxyHandler.WRITE_TIMEOUT = 300;
        HttpRequestProxyHandler.TIMEOUT_UNIT = TimeUnit.MILLISECONDS;

        timer = new HashedWheelTimer();
        proxy = new HttpProxyServer(new InetSocketAddress(0), null, timer, auth, connector);
        proxy.start();
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = proxy.getListeningPort();
    }

    @After
    public void tearDown()
    {
        proxy.stop();
        timer.stop();
    }

    @Test
    public void shouldReturn401WhenAccessTokenMissing() throws Exception
    {
        when(auth.authenticate(any(HttpRequest.class))).thenThrow(new UnauthorizedUserException());

        expect()
                .statusCode(401)
                .cookie("route", isEmptyString())
        .when()
                .get("/");
    }

    @Test
    public void shouldReturn503WhenNoDeviceAvailable() throws Exception
    {
        AuthenticatedPrincipal user = new AuthenticatedPrincipal("foo@bar.baz");
        when(auth.authenticate(any(HttpRequest.class))).thenReturn(user);

        expect()
                .statusCode(503)
                .cookie("route", isEmptyString())
        .when()
                .get("/");
    }

    @Test
    public void shouldReturn503WhenRequestedDeviceNotAvailable() throws Exception
    {
        AuthenticatedPrincipal user = new AuthenticatedPrincipal("foo@bar.baz");
        DID did = DID.generate();

        when(auth.authenticate(any(HttpRequest.class))).thenReturn(user);

        when(connector.connect(eq(user), any(DID.class), eq(false), any(Version.class),
                any(ChannelPipeline.class)))
                .thenReturn(mock(Channel.class));
        when(connector.connect(eq(user), eq(did), eq(true), any(Version.class),
                any(ChannelPipeline.class)))
                .thenReturn(null);

        given()
                .cookie("route", did.toStringFormal())
                .header("Endpoint-Consistency", "strict")
        .expect()
                .statusCode(503)
                .cookie("route", isEmptyString())
        .when()
                .get("/");
    }

    @Test
    public void shouldReturn503WhenRouteNotAvailable() throws Exception
    {
        AuthenticatedPrincipal user = new AuthenticatedPrincipal("foo@bar.baz");
        DID did = DID.generate();

        when(auth.authenticate(any(HttpRequest.class))).thenReturn(user);

        when(connector.connect(eq(user), any(DID.class), eq(false), any(Version.class),
                any(ChannelPipeline.class)))
                .thenReturn(mock(Channel.class));
        when(connector.connect(eq(user), eq(did), eq(true), any(Version.class),
                any(ChannelPipeline.class)))
                .thenReturn(null);

        given()
                .header("Route", did.toStringFormal())
        .expect()
                .statusCode(503)
                .cookie("route", isEmptyString())
        .when()
                .get("/");
    }

    public static class TestChannel extends AbstractChannel
    {
        private static class Address extends SocketAddress
        {
            private static final long serialVersionUID = 2271932999490284871L;
        }

        private static class Sink extends AbstractChannelSink
        {
            private final Executor _e = Executors.newSingleThreadExecutor();

            @Override
            public void eventSunk(ChannelPipeline p, ChannelEvent e) throws Exception
            {
            }

            @Override
            public ChannelFuture execute(ChannelPipeline pipeline, Runnable task)
            {
                ChannelFuture cf = Channels.future(pipeline.getChannel());
                _e.execute(() -> {
                    try {
                        task.run();
                        cf.setSuccess();
                    } catch (Throwable t) {
                        cf.setFailure(t);
                    }
                });
                return cf;
            }
        }

        private final AtomicInteger _ops;
        private final Address _addr = new Address();
        private final ChannelConfig _config = new DefaultChannelConfig();

        public TestChannel(ChannelPipeline pipeline, AtomicInteger ops)
        {
            super(null, null, pipeline, new Sink());
            _ops = ops;
        }

        @Override
        public ChannelConfig getConfig()
        {
            return _config;
        }

        @Override
        public boolean isBound()
        {
            return true;
        }

        @Override
        public boolean isConnected()
        {
            return true;
        }

        @Override
        public int getInterestOps()
        {
            return _ops.get();
        }

        @Override
        public SocketAddress getLocalAddress()
        {
            return _addr;
        }

        @Override
        public SocketAddress getRemoteAddress()
        {
            return _addr;
        }
    }

    private static Channel makeChannel(ChannelPipeline p, AtomicInteger ops, Consumer<Channel> init)
    {
        Channel c = new TestChannel(p, ops);
        Channels.fireChannelOpen(c);
        c.getPipeline().execute(
                () -> Channels.fireChannelConnected(c, c.getRemoteAddress()));
        init.accept(c);
        return c;
    }

    @Test
    public void shouldReturn504OnUpstreamTimeout() throws Exception
    {
        AuthenticatedPrincipal user = new AuthenticatedPrincipal("foo@bar.baz");

        when(auth.authenticate(any(HttpRequest.class))).thenReturn(user);

        AtomicInteger ops = new AtomicInteger(Channel.OP_READ);
        when(connector.connect(eq(user), any(DID.class), eq(false), any(Version.class),
                any(ChannelPipeline.class)))
                .thenAnswer(i -> makeChannel((ChannelPipeline) i.getArguments()[4], ops, c -> {
                }));

        expect()
                .statusCode(504)
        .when()
                .get("/");
    }

    @Test
    public void shouldWaitForUpstreamToBeReadable() throws Exception
    {
        AuthenticatedPrincipal user = new AuthenticatedPrincipal("foo@bar.baz");

        when(auth.authenticate(any(HttpRequest.class))).thenReturn(user);

        DID did = DID.generate();
        AtomicInteger ops = new AtomicInteger(0);
        ChannelBuffer response = ChannelBuffers.wrappedBuffer((
                "HTTP/1.1 204 No Content\r\n\r\n"
        ).getBytes(StandardCharsets.UTF_8));

        when(connector.connect(eq(user), any(DID.class), eq(false), any(Version.class),
                any(ChannelPipeline.class)))
                .thenAnswer(i -> makeChannel((ChannelPipeline) i.getArguments()[4], ops, c -> {
                    when(connector.device(c)).thenReturn(did);
                    when(connector.alternateDevices(c)).thenReturn(Collections.emptyList());

                    // wait 20 times the read timeout before producing response
                    timer.newTimeout(timeout -> {
                        ops.set(Channel.OP_READ);
                        Channels.fireMessageReceived(c, response);
                    }, 20 * HttpRequestProxyHandler.READ_TIMEOUT, HttpRequestProxyHandler.TIMEOUT_UNIT);
                }));

        expect()
                .statusCode(204)
                .cookie("route", did.toStringFormal())
        .when()
                .get("/");
    }
}
