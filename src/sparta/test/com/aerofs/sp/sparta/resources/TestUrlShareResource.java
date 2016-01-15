package com.aerofs.sp.sparta.resources;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.RestObject;
import com.aerofs.bifrost.module.AccessTokenDAO;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.restless.providers.GsonProvider;
import com.aerofs.sp.server.AccessCodeProvider;
import com.aerofs.sp.server.Zelda;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import com.jayway.restassured.specification.ResponseSpecification;
import org.hamcrest.Matcher;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aerofs.sp.sparta.resources.SharedFolderResource.X_REAL_IP;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * N.B. the routes covered in this test suite are currently undocumented.
 */
public class TestUrlShareResource extends AbstractResourceTest
{
    private static final String URL_SHARES_RESOURCE = "/v1.4/links";
    private static final String SINGLE_URL_SHARE_RESOURCE = URL_SHARES_RESOURCE + "/{key}";
    private static final String URL_PASSWORD_RESOURCE = SINGLE_URL_SHARE_RESOURCE + "/password";
    private static final String URL_EXPIRES_RESOURCE = SINGLE_URL_SHARE_RESOURCE + "/expires";
    private static final String URL_REQUIRE_LOGIN_RESOURCE = SINGLE_URL_SHARE_RESOURCE
            + "/require_login";

    private String token;

    private SID sid;
    private RestObject soid;

    private String key;
    private String urlContent;
    private String urlContentWithFields;
    private String urlContentToAnchor;
    private String urlContentToOtherAnchor;

    final static Long EXPIRY_EPOCH = 8000L;
    final static String EXPIRY =  GsonProvider.dateFormat().format(new Date(EXPIRY_EPOCH));

    @Before
    public void testSetup()
            throws Exception
    {
        sid = mkShare("fake_share", user);

        soid = new RestObject(sid);

        String accessCode;
        sqlTrans.begin();
        try {
            accessCode = inj.getInstance(AccessCodeProvider.class)
                    .createAccessCodeForUser(factUser.create(user));
            sqlTrans.commit();
        } catch (Exception e) {
            sqlTrans.rollback();
            throw e;
        } finally {
            sqlTrans.cleanUp();
        }

        // this token is revoked in some tests, so it's important to obtain an actual token.
        token = inj.getInstance(Zelda.class)
                .createAccessToken(soid.toStringFormal(), accessCode, 0L);
        key = mkUrl(soid, token, user, false, null, null);

        urlContent = formatJSONContent(ImmutableMap.of("soid", soid.toStringFormal()));
        urlContentWithFields = formatJSONContent(ImmutableMap.of(
                "soid", soid.toStringFormal(),
                "password", "fake_password",
                "require_login", true,
                "expires", EXPIRY));
        urlContentToAnchor = formatJSONContent(ImmutableMap.of("soid",
                new RestObject(SID.rootSID(user), SID.storeSID2anchorOID(sid)).toStringFormal()));
        urlContentToOtherAnchor = formatJSONContent(ImmutableMap.of("soid",
                new RestObject(SID.rootSID(other), SID.storeSID2anchorOID(sid)).toStringFormal()));
    }

    @Test
    public void getURLInfo_should200GivenReadAccess()
            throws Exception {
        mockTime(5000L);

        givenReadAccess()
                .header(X_REAL_IP, "4.2.2.2")
        .expect()
                .statusCode(200)
                .body("key", equalTo(key))
                .body("soid", equalTo(soid.toStringFormal()))
                .body("token", equalTo(token))
                .body("created_by", equalTo(user.getString()))
                .body("require_login", equalTo(false))
                .body("has_password", equalTo(false))
                .body("password", nullValue())
        .when()
                .get(SINGLE_URL_SHARE_RESOURCE, key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.access", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));

        assertHasCaller(payload, user);
    }

    @SuppressWarnings("unchecked")
    private static void assertHasCaller(Map<String, Object> payload, UserID user)
    {
        Map<String, Object> caller = (Map<String, Object>)payload.get("caller");

        assertTrue(caller.containsKey("email"));
        assertEquals(user.getString(), caller.get("acting_as"));
        assertTrue(caller.containsKey("device"));
    }

    @Test
    public void getURLInfo_shouldReturnPropertyValues()
            throws Exception
    {
        String key2 = mkUrl(soid, token, user, true, "fake_password", EXPIRY_EPOCH);

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("key", equalTo(key2))
                .body("require_login", equalTo(true))
                .body("has_password", equalTo(true))
                .body("password", nullValue())
                .body("expires", equalTo(EXPIRY))
        .when()
                .get(SINGLE_URL_SHARE_RESOURCE, key2);
    }

    @Test
    public void createURL_should201GivenWriteAccess()
            throws Exception
    {
        mockTime(5000L);

        LocationMatchers matchers = new LocationMatchers(
                String.format("https://localhost:%s/v1.4/links/", sparta.getListeningPort()));

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
                .contentType(JSON)
                .content(urlContent)
        .expect()
                .statusCode(201)
                .header(HttpHeaders.Names.LOCATION, matchers._locationMatcher)
                .body("key", matchers._keyMatcher)
                .body("soid", equalTo(soid.toStringFormal()))
                .body("token", not(isEmptyOrNullString()))
                .body("created_by", equalTo(user.getString()))
                .body("require_login", equalTo(false))
                .body("has_password", equalTo(false))
        .when()
                .post(URL_SHARES_RESOURCE);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.create", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertTrue(payload.containsKey("key"));
        assertEquals(soid.toStringFormal(), payload.get("soid"));
        assertFalse(payload.containsKey("set_password"));
        assertFalse(payload.containsKey("password"));
        assertFalse(payload.containsValue("fake_password"));
        assertEquals("false", payload.get("require_login"));
        assertFalse(payload.containsKey("expires"));

        assertHasCaller(payload, user);
    }

    @Test
    public void createURL_shouldReturnProperties()
            throws Exception
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
                .contentType(JSON)
                .content(urlContentWithFields)
        .expect()
                .statusCode(201)
                .body("require_login", equalTo(true))
                .body("has_password", equalTo(true))
                .body("password", nullValue())
                .body("expires", equalTo(EXPIRY))
        .when()
                .post(URL_SHARES_RESOURCE);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.create", payload.get("event"));
        assertTrue(payload.containsKey("key"));
        assertEquals("true", payload.get("require_login"));
        assertEquals("true", payload.get("set_password"));
        assertFalse(payload.containsKey("password"));
        assertFalse(payload.containsValue("fake_password"));
        assertEquals(Long.toString(EXPIRY_EPOCH), payload.get("expiry"));
    }

    @Test
    public void createURL_shouldFollowAnchor()
            throws Exception
    {
        givenWriteAccess()
                .contentType(JSON)
                .content(urlContentToAnchor)
        .expect()
                .statusCode(201)
                .body("soid", equalTo(new RestObject(sid).toStringFormal()))
        .when()
                .post(URL_SHARES_RESOURCE);
    }

    @Test
    public void createURL_should401GivenNonDelegatedService()
    {
        givenSecret("fake_service", deploymentSecret)
                .contentType(JSON)
                .content(urlContent)
        .expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when()
                .post(URL_SHARES_RESOURCE);
    }

    @Test
    public void createURL_should400OnBadSOID()
            throws Exception
    {
        String[] soids = {
                "badSOID",
                "root",
                "appdata",
                "badSID0000000000000000000000000badOID",
                "badSID0000000000000000000000000badOID00000000000000000000000000",
        };

        for (String soid : soids) {
            givenWriteAccess()
                    .contentType(JSON)
                    .content(formatJSONContent(ImmutableMap.of("soid", soid)))
            .expect()
                    .statusCode(400)
                    .body("type", equalTo("BAD_ARGS"))
            .when()
                    .post(URL_SHARES_RESOURCE);
        }
    }

    @Test
    public void createURL_should403IfNotJoinedOwner()
            throws Exception
    {
        SID another = mkShare("another_share", other);
        RestObject[] soids = {
                new RestObject(another),
                new RestObject(SID.rootSID(other), SID.storeSID2anchorOID(another)),
        };

        for (RestObject soid : soids)
        {
            // the caller may be an admin, but the caller is not a joined owner of the new share.
            givenAdminAccess()
                    .contentType(JSON)
                    .content(formatJSONContent(ImmutableMap.of("soid", soid.toStringFormal())))
            .expect()
                    .statusCode(403)
                    .body("type", equalTo("FORBIDDEN"))
            .when()
                    .post(URL_SHARES_RESOURCE);
        }
    }

    @Test
    public void updateURLInfo_should200GivenWriteAccess()
            throws Exception
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
                .contentType(JSON)
                .content(urlContentWithFields)
        .expect()
                .statusCode(200)
                .body("key", equalTo(key))
                .body("token", not(equalTo(token)))
                .body("has_password", equalTo(true))
                .body("password", nullValue())
                .body("expires", equalTo(EXPIRY))
                .body("require_login", equalTo(true))
        .when()
                .put(SINGLE_URL_SHARE_RESOURCE, key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.update", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));
        assertEquals("true", payload.get("set_password"));
        assertFalse(payload.containsKey("password"));
        assertFalse(payload.containsValue("fake_password"));
        assertEquals(Long.toString(EXPIRY_EPOCH), payload.get("expiry"));
        assertEquals("true", payload.get("require_login"));

        assertHasCaller(payload, user);

        assertAccessTokenDeleted(token);
    }

    @Test
    public void updateURLInfo_should400GivenNoUpdates()
            throws Exception
    {
        givenWriteAccess()
                .contentType(JSON)
                .content(urlContent)
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(SINGLE_URL_SHARE_RESOURCE, key);
    }

    @Test
    public void setURLPassword_should204GivenWriteAccess() throws Exception {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
                .contentType(JSON)
                .content("\"quoted\\\\/\\u1234\"")
        .expect()
                .statusCode(204)
                .body(isEmptyOrNullString())
        .when()
                .put(URL_PASSWORD_RESOURCE, key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.set_password", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));
        assertFalse(payload.containsKey("password"));
        assertFalse(payload.containsValue("fake"));

        assertHasCaller(payload, user);

        assertAccessTokenDeleted(token);

        assertTrue(validatePassword("quoted\\/\u1234"));
    }

    boolean validatePassword(String password) throws Exception
    {
        try {
        sqlTrans.begin();
            try {
                factUrlShare.create(key).validatePassword(password.getBytes(StandardCharsets.UTF_8));
                sqlTrans.commit();
            } catch (Exception e) {
                sqlTrans.rollback();
                throw e;
            } finally {
                sqlTrans.cleanUp();
            }
            return true;
        } catch (ExBadCredential e) {
            return false;
        }
    }

    @Test
    public void setURLPassword_should400GivenEmptyPassword()
    {
        givenWriteAccess()
                .contentType(JSON)
                .content("")
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(URL_PASSWORD_RESOURCE, key);
    }

    @Test
    public void setURLExpires_should204_iso8601()
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
                .contentType(JSON)
                .content(GsonProvider.GSON.toJson(new Date(EXPIRY_EPOCH)))
        .expect()
                .statusCode(204)
                .body(isEmptyOrNullString())
        .when()
                .put(URL_EXPIRES_RESOURCE, key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.set_expiry", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));
        assertEquals(Long.toString(EXPIRY_EPOCH), payload.get("expiry"));

        assertHasCaller(payload, user);

        assertAccessTokenDeleted(token);
    }

    @Test
    public void setURLExpires_should204_epoch()
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
                .contentType(JSON)
                .content(Long.toString(EXPIRY_EPOCH))
        .expect()
                .statusCode(204)
                .body(isEmptyOrNullString())
        .when()
                .put(URL_EXPIRES_RESOURCE, key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.set_expiry", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));
        assertEquals(Long.toString(EXPIRY_EPOCH), payload.get("expiry"));

        assertHasCaller(payload, user);

        assertAccessTokenDeleted(token);
    }

    @Test
    public void setURLExpires_should400GivenBadInput()
    {
        givenWriteAccess()
                .contentType(JSON)
                .content("bad input")
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(URL_EXPIRES_RESOURCE, key);
    }

    @Test
    public void setURLRequireLogin_should204GivenWriteAccess()
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
        .expect()
                .statusCode(204)
                .body(isEmptyOrNullString())
        .when()
                .put(URL_REQUIRE_LOGIN_RESOURCE, key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.set_require_login", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));
        assertEquals("true", payload.get("require_login"));

        assertHasCaller(payload, user);

        assertAccessTokenDeleted(token);
    }

    @Test
    public void removeURL_should204GivenWriteAccess()
            throws Exception
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
        .expect()
                .statusCode(204)
                .body(isEmptyOrNullString())
        .when()
                .delete(SINGLE_URL_SHARE_RESOURCE, key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.delete", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));

        assertHasCaller(payload, user);

        assertAccessTokenDeleted(token);
    }

    @Test
    public void removeURL_should204GivenAdminAccess()
            throws Exception
    {
        givenAdminAccess()
        .expect()
                .statusCode(204)
                .body(isEmptyOrNullString())
        .when()
                .delete(SINGLE_URL_SHARE_RESOURCE, key);
    }

    @Test
    public void removeURLPassword_should204GivenWriteAccess()
            throws Exception
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
        .expect()
                .statusCode(204)
                .body(isEmptyOrNullString())
        .when()
                .delete(URL_PASSWORD_RESOURCE, key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.remove_password", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));

        assertHasCaller(payload, user);
    }

    @Test
    public void removeURLExpires_should204GivenWriteAccess()
            throws Exception
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
        .expect()
                .statusCode(204)
                .body(isEmptyOrNullString())
        .when()
                .delete(URL_EXPIRES_RESOURCE, key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.remove_expiry", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));

        assertHasCaller(payload, user);

        assertAccessTokenDeleted(token);
    }

    @Test
    public void removeURLRequireLogin_should200GivenWriteAccess()
            throws Exception
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
        .expect()
                .statusCode(204)
                .body(isEmptyOrNullString())
        .when()
                .delete(URL_REQUIRE_LOGIN_RESOURCE, key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.set_require_login", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));
        assertEquals("false", payload.get("require_login"));

        assertHasCaller(payload, user);
    }

    @Test
    public void allRoutesExceptCreateAndRemove_shouldSucceedGivenAdminAccess() throws Exception
    {
        for (TestPath path : allPaths()) {
            // skip createURL because it should 403 with admin access
            if (path._method == HttpMethod.POST) { continue; }

            // skip removeURL because it destroys the link and causes other routes to fail
            if (path._method == HttpMethod.DELETE
                    && path._path.equals(SINGLE_URL_SHARE_RESOURCE)) { continue; }

            path.begin(givenAdminAccess())
            .expect()
                    .statusCode(either(equalTo(200)).or(equalTo(204)))
            .when()
                    .run(key);
        }
    }

    @Test
    public void allRoutes_should403GivenInsufficientAccess() throws Exception
    {
        for (TestPath path : allPaths()) {
            RequestSpecification givenInsufficientAccess = path._method == HttpMethod.GET
                    // get requests only requires read access
                    ? givenLinkSharingAccess()
                    : givenReadAccess();

            path.begin(givenInsufficientAccess)
            .expect()
                    .statusCode(403)
                    .body("type", equalTo("FORBIDDEN"))
            .when()
                    .run(key);
        }
    }

    @Test
    public void allRoutes_should403IfNotOwner() throws Exception
    {
        addUser(sid, other, Permissions.EDITOR);

        for (TestPath path : allPaths()) {
            path.begin(givenOtherAccess())
            .expect()
                    .statusCode(403)
                    .body("type", equalTo("FORBIDDEN"))
            .when()
                    .run(key);
        }
    }

    @Test
    public void allRoutes_should404IfNotMember() throws Exception
    {
        for (TestPath path : allPaths()) {
            path.begin(givenOtherAccess())
            .expect()
                    .statusCode(404)
                    .body("type", equalTo("NOT_FOUND"))
            .when()
                    .run(key);
        }
    }

    private String formatJSONContent(Map<String, Object>fields)
    {
        return fields.entrySet().stream()
                .map(entry -> String.format("\"%s\":\"%s\"",
                        entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private void assertAccessTokenDeleted(String token)
    {
        assertNull(bifrostInj.getInstance(AccessTokenDAO.class).findByToken(token));
    }

    private Set<TestPath> allPaths()
    {
        return ImmutableSet.of(
                new TestPath(HttpMethod.GET, SINGLE_URL_SHARE_RESOURCE),
                new TestPath(HttpMethod.POST, URL_SHARES_RESOURCE)
                        .withContent(JSON, urlContent),
                new TestPath(HttpMethod.POST, URL_SHARES_RESOURCE)
                        .withContent(JSON, urlContentToAnchor),
                new TestPath(HttpMethod.POST, URL_SHARES_RESOURCE)
                        .withContent(JSON, urlContentToOtherAnchor),
                new TestPath(HttpMethod.PUT, SINGLE_URL_SHARE_RESOURCE)
                        .withContent(JSON, urlContentWithFields),
                new TestPath(HttpMethod.DELETE, SINGLE_URL_SHARE_RESOURCE),
                new TestPath(HttpMethod.PUT, URL_PASSWORD_RESOURCE)
                        .withContent(JSON, "fake"),
                new TestPath(HttpMethod.DELETE, URL_PASSWORD_RESOURCE),
                new TestPath(HttpMethod.PUT, URL_EXPIRES_RESOURCE)
                        .withContent(JSON, "7000"),
                new TestPath(HttpMethod.DELETE, URL_EXPIRES_RESOURCE),
                new TestPath(HttpMethod.PUT, URL_REQUIRE_LOGIN_RESOURCE),
                new TestPath(HttpMethod.DELETE, URL_REQUIRE_LOGIN_RESOURCE));
    }

    private static class TestPath
    {
        private final HttpMethod _method;
        private final String _path;

        private RequestSpecification _request;
        private ResponseSpecification _response;

        public TestPath(HttpMethod method, String path)
        {
            _method = method;
            _path = path;
        }

        public TestPath withContent(ContentType contentType, String content)
        {
            return new TestPath(_method, _path)
            {
                @Override
                public TestPath begin(RequestSpecification request)
                {
                    return super.begin(request
                            .contentType(contentType)
                            .content(content));
                }
            };
        }

        public TestPath begin(RequestSpecification request)
        {
            _request = request;
            return this;
        }

        public TestPath expect()
        {
            _response = _request.expect();
            return this;
        }

        public TestPath statusCode(int statusCode)
        {
            _response = _response.statusCode(statusCode);
            return this;
        }

        public TestPath statusCode(Matcher<? super Integer> statusCode)
        {
            _response = _response.statusCode(statusCode);
            return this;
        }

        public TestPath body(String key, Matcher<?>matcher)
        {
            _response = _response.body(key, matcher);
            return this;
        }

        public TestPath when()
        {
            _response = _response.when();
            return this;
        }

        // used for debugging
        public TestPath logEverything()
        {
            _response = _response.log().everything();
            return this;
        }

        public Response run(Object... pathParams)
        {
            return _path.equals(URL_SHARES_RESOURCE)
                    ? _method.apply(_response, _path)
                    : _method.apply(_response, _path, pathParams);
        }
    }

    private enum HttpMethod
    {
        GET(ResponseSpecification::get),
        POST(ResponseSpecification::post),
        PUT(ResponseSpecification::put),
        DELETE(ResponseSpecification::delete);

        private HttpMethodImpl _impl;

        HttpMethod(HttpMethodImpl impl)
        {
            _impl = impl;
        }

        Response apply(ResponseSpecification response, String path, Object... pathParams)
        {
            return _impl.apply(response, path, pathParams);
        }

        @FunctionalInterface
        private interface HttpMethodImpl
        {
            Response apply(ResponseSpecification response, String path, Object... pathParams);
        }
    }
}
