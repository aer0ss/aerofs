package com.aerofs.polaris.external_api.rest;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.RestObject;
import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.bifrost.server.Bifrost;
import com.aerofs.bifrost.server.BifrostTest;
import com.aerofs.ids.*;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.oauth.VerifyTokenResponse;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.google.common.collect.ImmutableSet;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.junit.*;
import org.junit.rules.RuleChain;

import java.util.Set;

import static com.jayway.restassured.RestAssured.given;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static com.aerofs.polaris.PolarisTestServer.getApiFoldersURL;
import static com.aerofs.polaris.PolarisTestServer.getApiFilesURL;

public class AbstractRestTest {
    static {
        RestAssured.config = PolarisHelpers.newRestAssuredConfig();
    }

    protected static final UserID USERID = UserID.fromInternal("test@aerofs.com");
    protected SID rootSID = SID.rootSID(USERID);
    private static final DID DEVICE = DID.generate();
    protected static final RequestSpecification AUTHENTICATED =
            PolarisHelpers.newAuthedAeroUserReqSpec(USERID, DEVICE);

    private static MySQLDatabase database = new MySQLDatabase("test");
    protected static PolarisTestServer polaris = new PolarisTestServer();

    protected static SessionFactory sessionFactory;
    protected static Session session;
    private static Bifrost bifrost;

    private static Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    protected final static byte[] OTHER_HASH =
            BaseSecUtil.newMessageDigestMD5().digest(new byte[] {1});
    protected final String OTHER_ETAG = "\"" + BaseUtil.hexEncode(OTHER_HASH) + "\"";

    @ClassRule
    public static RuleChain chain = RuleChain.outerRule(database).around(polaris);

    @BeforeClass
    public static void commonSetup() throws Exception {
        bifrost = new Bifrost(bifrostInjector(), PolarisTestServer.DEPLOYMENT_SECRET);
        bifrost.start();
    }

    @AfterClass
    public static void commonCleanup()
    {
        bifrost.stop();
    }

    @After
    public void afterTest()
    {
        database.clear();
        reset(polaris.getAccessManager(), polaris.getNotifier(), polaris.getTokenVerifier());
    }

    private static Injector bifrostInjector() throws Exception {
        sessionFactory = mock(SessionFactory.class);
        session = mock(Session.class);

        when(sessionFactory.openSession()).thenReturn(session);

        Injector inj = Guice.createInjector(Bifrost.bifrostModule(),
                BifrostTest.mockDatabaseModule(sessionFactory));

        BifrostTest.createTestEntities(USERID, inj);

        return inj;
    }

    protected RequestSpecification givenTokenWithScopes(Set<String> scopes)
            throws Exception {
        String token = UniqueID.generate().toStringFormal();
        VerifyTokenResponse response = new VerifyTokenResponse(
                BifrostTest.CLIENTID,
                scopes,
                0L,
                new AuthenticatedPrincipal(USERID.getString(), USERID, OrganizationID.PRIVATE_ORGANIZATION),
                MDID.generate().toStringFormal());
        doReturn(response).when(polaris.getTokenVerifier()).verifyToken(eq(token));
        return given()
                .header(HttpHeaders.Names.AUTHORIZATION, "Bearer " + token);
    }

    protected RequestSpecification givenInvalidToken() throws Exception {
        doReturn(VerifyTokenResponse.NOT_FOUND).when(polaris.getTokenVerifier())
                .verifyToken(anyString());
        return given()
                .header(HttpHeaders.Names.AUTHORIZATION, "Bearer invalid");
    }

    protected RequestSpecification givenExpiredToken() throws Exception {
        doReturn(VerifyTokenResponse.EXPIRED).when(polaris.getTokenVerifier())
                .verifyToken(anyString());
        return given()
                .header(HttpHeaders.Names.AUTHORIZATION, "Bearer " + UniqueID.generate().toStringFormal());
    }

    protected RequestSpecification givenAccess() throws Exception {
        return givenTokenWithScopes(ImmutableSet.of("files.read", "files.write"));
    }

    protected RequestSpecification givenReadAccessTo(RestObject object) throws Exception {
        return givenTokenWithScopes(ImmutableSet.of("files.read:" + object.toStringFormal()));
    }

    protected RequestSpecification givenReadAccess() throws Exception
    {
        return givenTokenWithScopes(ImmutableSet.of("files.read"));
    }

    protected RequestSpecification givenLinkShareReadAccess() throws Exception
    {
        return givenTokenWithScopes(ImmutableSet.of("files.read", "linksharing"));
    }

    protected static String json(Object o)
    {
        return _gson.toJson(o);
    }

    protected String getFolderEtag(SID store, OID folder) throws Exception
    {
        Response resp = givenReadAccess().get(getApiFoldersURL() + new RestObject(store, folder).toStringFormal());
        assert resp.statusCode() == 200;
        return resp.header(Names.ETAG);
    }

    protected String getFileEtag(SID store, OID file) throws Exception
    {
        Response resp = givenReadAccess().get(getApiFilesURL() + new RestObject(store, file).toStringFormal());
        assert resp.statusCode() == 200;
        return resp.header(Names.ETAG);
    }
}
