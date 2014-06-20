package com.aerofs.daemon.rest;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.bifrost.server.BifrostTest;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.mock.logical.MockDS.MockDSDir;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class TestAuth extends AbstractRestTest
{
    public TestAuth(boolean useProxy)
    {
        super(useProxy);
    }

    @Test
    public void shouldReturn401WhenAccessTokenMissing() throws Exception
    {
        expect()
                .statusCode(401)
                .header(Names.WWW_AUTHENTICATE, "Bearer realm=\"AeroFS\"")
        .when().get("/v0.9/children");
    }

    @Test
    public void shouldReturn401WhenAccessTokenInvalid() throws Exception
    {
        givenInvalidToken()
        .expect()
                .statusCode(401)
                .header(Names.WWW_AUTHENTICATE, "Bearer realm=\"AeroFS\"")
        .when().get("/v0.9/children");
    }

    @Test
    public void shouldReturn401WhenAccessTokenExpired() throws Exception
    {
        givenExpiredToken()
        .expect()
                .statusCode(401)
                .header(Names.WWW_AUTHENTICATE, "Bearer realm=\"AeroFS\"")
        .when().get("/v0.9/children");
    }

    @Test
    public void shouldAcceptTokenInQueryParam() throws Exception
    {
        // stub TokenVerifier
        givenAccess();

        given()
                .queryParam("token", BifrostTest.RW_TOKEN)
        .expect()
                .statusCode(200)
        .when().get("/v1.0/children");
    }

    @Test
    public void shouldReturn401WhenAccessTokenGivenInQueryParamAndAuthHeader() throws Exception
    {
        given()
                .queryParam("token", BifrostTest.RW_TOKEN)
                .header(Names.AUTHORIZATION, "Bearer " + BifrostTest.RW_TOKEN)
        .expect()
                .statusCode(401)
        .when().get("/v1.0/children");
    }

    @Test
    public void shouldReturn401WhenAccessTokenGivenInTwoQueryParams() throws Exception
    {
        given()
                .queryParam("token", BifrostTest.RW_TOKEN)
                .queryParam("token", BifrostTest.RW_TOKEN)
        .expect()
                .statusCode(401)
        .when().get("/v1.0/children");
    }

    @Test
    public void shouldReturn401WhenAccessTokenGivenInTwoAuthHeaders() throws Exception
    {
        given()
                .header(Names.AUTHORIZATION, "Bearer " + BifrostTest.RW_TOKEN)
                .header(Names.AUTHORIZATION, "Bearer " + BifrostTest.RW_TOKEN)
        .expect()
                .statusCode(401)
        .when().get("/v1.0/children");
    }

    @Test
    public void shouldGetFileWithTokenScopedToFile() throws Exception
    {
        mds.root().dir("d1").file("f1");
        mds.root().file("f2");

        givenReadAccessTo(object("d1/f1"))
        .expect()
                .statusCode(200)
        .when().log().everything()
                .get("/v1.2/files/" + object("d1/f1").toStringFormal());
    }

    @Test
    public void shouldGet403WhenTokenScopedToDifferentFile() throws Exception
    {
        mds.root().dir("d1").file("f1");
        mds.root().file("f2");

        givenReadAccessTo(object("f2"))
        .expect()
                .statusCode(403)
        .when()
                .get("/v1.2/files/" + object("d1/f1").toStringFormal());
    }

    @Test
    public void shouldGetFileWithTokenScopedToParent() throws Exception
    {
        mds.root().dir("d1").file("f1");
        mds.root().file("f2");

        givenReadAccessTo(object("d1"))
        .expect()
                .statusCode(200)
        .when()
                .get("/v1.2/files/" + object("d1/f1").toStringFormal());
    }

    @Test
    public void shouldGetFolderWithTokenScopedToFolder() throws Exception
    {
        mds.root().dir("d1").file("f1");
        mds.root().file("f2");


        givenReadAccessTo(object("d1"))
        .expect()
                .statusCode(200)
        .when()
                .get("/v1.2/folders/" + object("d1").toStringFormal());
    }

    @Test
    public void shouldGetAppDataFolder() throws Exception
    {
        when(oc.create_(eq(Type.DIR), any(SOID.class), anyString(), eq(PhysicalOp.APPLY), eq(t)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable
                    {
                        Object[] args = invocation.getArguments();
                        SOID p = (SOID)args[1];
                        String name = (String)args[2];
                        ResolvedPath pathParent = ds.resolve_(p);
                        MockDSDir parent = mds.cd(pathParent);
                        if (parent.hasChild(name)) throw new ExAlreadyExist();
                        MockDSDir d = parent.dir(name);
                        return d.soid();
                    }
                });

        givenTokenWithScopes(ImmutableSet.of("files.appdata"))
        .expect()
                .statusCode(200)
        .when()
                .get("/v1.2/folders/appdata");
    }

    @Test
    public void shouldGetFolderWithTokenScopedToParentFolder() throws Exception
    {
        mds.root().dir("d1").dir("d2").file("f1");

        givenReadAccessTo(object("d1"))
        .expect()
                .statusCode(200)
        .when()
                .get("/v1.2/folders/" + object("d1/d2").toStringFormal());
    }

    @Test
    public void shouldGet403WhenTokenScopedToChildFolder() throws Exception
    {
        mds.root().dir("d1").dir("d2").file("f1");

        givenReadAccessTo(object("d1/d2"))
        .expect()
                .statusCode(403)
        .when()
                .get("/v1.2/folders/" + object("d1").toStringFormal());
    }
}
