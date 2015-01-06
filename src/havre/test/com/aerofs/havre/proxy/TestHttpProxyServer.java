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
import com.aerofs.base.id.DID;
import com.aerofs.havre.Authenticator;
import com.aerofs.havre.Authenticator.UnauthorizedUserException;
import com.aerofs.havre.EndpointConnector;
import com.aerofs.havre.Version;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.testlib.AbstractBaseTest;
import com.jayway.restassured.RestAssured;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DefaultChannelConfig;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.util.HashedWheelTimer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

        proxy = new HttpProxyServer(new InetSocketAddress(0), null, new HashedWheelTimer(), auth, connector);
        proxy.start();
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = proxy.getListeningPort();
    }

    @After
    public void tearDown()
    {
        proxy.stop();
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

        private final Address _addr = new Address();
        private final ChannelConfig _config = new DefaultChannelConfig();

        public TestChannel(ChannelPipeline pipeline)
        {
            super(null, null, pipeline, new Sink());
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

    @Test
    public void shouldReturn504OnDownstreamTimeout() throws Exception
    {
        AuthenticatedPrincipal user = new AuthenticatedPrincipal("foo@bar.baz");

        when(auth.authenticate(any(HttpRequest.class))).thenReturn(user);

        when(connector.connect(eq(user), any(DID.class), eq(false), any(Version.class),
                any(ChannelPipeline.class)))
                .thenAnswer(i -> {
                    Channel c = new TestChannel((ChannelPipeline)i.getArguments()[4]);
                    Channels.fireChannelOpen(c);
                    c.getPipeline().execute(
                            () -> Channels.fireChannelConnected(c, c.getRemoteAddress()));
                    return c;
                });

        expect()
                .statusCode(504)
        .when()
                .get("/");
    }

}
