/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.havre.proxy;

import com.aerofs.base.Version;
import com.aerofs.base.id.DID;
import com.aerofs.havre.Authenticator;
import com.aerofs.havre.Authenticator.UnauthorizedUserException;
import com.aerofs.havre.EndpointConnector;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.testlib.AbstractBaseTest;
import com.jayway.restassured.RestAssured;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.util.HashedWheelTimer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.net.InetSocketAddress;

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

    @Before
    public void setUp()
    {
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
                .cookie("server", isEmptyString())
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
                .cookie("server", isEmptyString())
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
                .cookie("server", did.toStringFormal())
                .header("Endpoint-Consistency", "strict")
        .expect()
                .statusCode(503)
                .cookie("server", isEmptyString())
                .when()
                .get("/");
    }
}
