package com.aerofs.sp.sparta.resources;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.id.RestObject;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
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

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aerofs.sp.sparta.resources.SharedFolderResource.X_REAL_IP;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.apache.http.HttpStatus.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * N.B. the routes covered in this test suite are currently undocumented.
 *
 * TODO (AT): implement and add test coverage for internal failures and ensure access codes and
 *   access tokens are cleaned up.
 */
public class TestUrlShareResource extends AbstractResourceTest
{
    private static final String BASE_RESOURCE = "/v1.4/shares";
    private static final String URL_SHARES_RESOURCE = BASE_RESOURCE + "/{sid}/urls";
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
                "expires", 8000L));
    }

    @Test
    public void getURLInfo_should200GivenReadAccess()
            throws Exception
    {
        mockTime(5000L);

        givenReadAccess()
                .header(X_REAL_IP, "4.2.2.2")
        .expect()
                .statusCode(SC_OK)
                .body("key", equalTo(key))
                .body("soid", equalTo(soid.toStringFormal()))
                .body("token", equalTo(token))
                .body("created_by", equalTo(user.getString()))
                .body("require_login", equalTo(false))
                .body("has_password", equalTo(false))
                .body("password", nullValue())
                .body("expires", equalTo(0))
        .when()
                .get(SINGLE_URL_SHARE_RESOURCE, sid.toStringFormal(), key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.access", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));

        Map caller = (Map)payload.get("caller");

        assertTrue(caller.containsKey("email"));
        assertEquals(user.getString(), caller.get("acting_as"));
        assertTrue(caller.containsKey("device"));
    }

    @Test
    public void getURLInfo_shouldReturnPropertyValues()
            throws Exception
    {
        mockTime(5000L);

        String key2 = mkUrl(soid, token, user, true, "fake_password", 8000L);

        givenReadAccess()
        .expect()
                .statusCode(SC_OK)
                .body("key", equalTo(key2))
                .body("require_login", equalTo(true))
                .body("has_password", equalTo(true))
                .body("password", nullValue())
                .body("expires", equalTo(3))
        .when()
                .get(SINGLE_URL_SHARE_RESOURCE, sid.toStringFormal(), key2);
    }

    @Test
    public void listURLs_should200GivenReadAccess()
            throws Exception
    {
        mkUrl(soid, token, user, false, null, null);
        mkUrl(soid, token, user, false, null, null);
        mkUrl(soid, token, user, false, null, null);

        givenReadAccess()
        .expect()
                .statusCode(SC_OK)
                .body("urls", iterableWithSize(4))
        .when()
                .get(URL_SHARES_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void createURL_should201GivenWriteAccess()
            throws Exception
    {
        mockTime(5000L);

        LocationMatchers matchers = new LocationMatchers(
                String.format("https://localhost:%s%s/%s/urls/",
                        sparta.getListeningPort(), BASE_RESOURCE, sid.toStringFormal()));

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
                .contentType(JSON)
                .content(urlContent)
        .expect()
                .statusCode(SC_CREATED)
                .header(HttpHeaders.Names.LOCATION, matchers._locationMatcher)
                .body("key", matchers._keyMatcher)
                .body("soid", equalTo(soid.toStringFormal()))
                .body("token", not(isEmptyOrNullString()))
                .body("created_by", equalTo(user.getString()))
                .body("require_login", equalTo(false))
                .body("has_password", equalTo(false))
                .body("expires", equalTo(0))
        .when()
                .post(URL_SHARES_RESOURCE, sid.toStringFormal());

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

        Map caller = (Map)payload.get("caller");

        assertTrue(caller.containsKey("email"));
        assertEquals(user.getString(), caller.get("acting_as"));
        assertTrue(caller.containsKey("device"));
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
                .statusCode(SC_CREATED)
                .body("require_login", equalTo(true))
                .body("has_password", equalTo(true))
                .body("password", nullValue())
                .body("expires", equalTo(3))
        .when()
                .post(URL_SHARES_RESOURCE, sid.toStringFormal());

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.create", payload.get("event"));
        assertTrue(payload.containsKey("key"));
        assertEquals("true", payload.get("require_login"));
        assertEquals("true", payload.get("set_password"));
        assertFalse(payload.containsKey("password"));
        assertFalse(payload.containsValue("fake_password"));
        assertEquals("8000", payload.get("expiry"));
    }

    @Test
    public void createURL_should401GivenNonDelegatedService()
    {
        givenSecret("fake_service", deploymentSecret)
                .contentType(JSON)
                .content(urlContent)
        .expect()
                .statusCode(SC_UNAUTHORIZED)
                .body("type", equalTo("UNAUTHORIZED"))
        .when()
                .post(URL_SHARES_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void createURL_should400OnBadSOID()
            throws Exception
    {
        SID sid2 = mkShare("fake_share_2", user);
        OID oid2 = SID.storeSID2anchorOID(sid2);

        String[] soids = {
                "badSOID",
                "root",
                "appdata",
                "badSID0000000000000000000000000badOID",
                "badSID0000000000000000000000000badOID00000000000000000000000000",
                new RestObject(sid2).toStringFormal(),      // object in another store
                new RestObject(sid, oid2).toStringFormal(), // anchor in the same store
        };

        for (String soid : soids) {
            givenWriteAccess()
                    .contentType(JSON)
                    .content(formatJSONContent(ImmutableMap.of("soid", soid)))
            .expect()
                    .statusCode(SC_BAD_REQUEST)
                    .body("type", equalTo("BAD_ARGS"))
            .when()
                    .post(URL_SHARES_RESOURCE, sid.toStringFormal());
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
                .statusCode(SC_OK)
                .body("key", equalTo(key))
                .body("token", not(equalTo(token)))
                .body("has_password", equalTo(true))
                .body("password", nullValue())
                .body("expires", equalTo(3))
                .body("require_login", equalTo(true))
        .when()
                .put(SINGLE_URL_SHARE_RESOURCE, sid.toStringFormal(), key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.update", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));
        assertEquals("true", payload.get("set_password"));
        assertFalse(payload.containsKey("password"));
        assertFalse(payload.containsValue("fake_password"));
        assertEquals("8000", payload.get("expiry"));
        assertEquals("true", payload.get("require_login"));

        Map caller = (Map)payload.get("caller");

        assertTrue(caller.containsKey("email"));
        assertEquals(user.getString(), caller.get("acting_as"));
        assertTrue(caller.containsKey("device"));
    }

    @Test
    public void updateURLInfo_should400GivenNoUpdates()
            throws Exception
    {
        givenWriteAccess()
                .contentType(JSON)
                .content(urlContent)
        .expect()
                .statusCode(SC_BAD_REQUEST)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(SINGLE_URL_SHARE_RESOURCE, sid.toStringFormal(), key);
    }

    @Test
    public void setURLPassword_should200GivenWriteAccess()
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
                .contentType(JSON)
                .content("fake")
        .expect()
                .statusCode(SC_OK)
                .body("key", equalTo(key))
                .body("token", not(equalTo(token)))
                .body("has_password", equalTo(true))
                .body("password", nullValue())
                .body("expires", equalTo(0))
                .body("require_login", equalTo(false))
        .when()
                .put(URL_PASSWORD_RESOURCE, sid.toStringFormal(), key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.set_password", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));
        assertFalse(payload.containsKey("password"));
        assertFalse(payload.containsValue("fake"));

        Map caller = (Map)payload.get("caller");

        assertTrue(caller.containsKey("email"));
        assertEquals(user.getString(), caller.get("acting_as"));
        assertTrue(caller.containsKey("device"));
    }

    @Test
    public void setURLPassword_should400GivenEmptyPassword()
    {
        givenWriteAccess()
                .contentType(JSON)
                .content("")
        .expect()
                .statusCode(SC_BAD_REQUEST)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(URL_PASSWORD_RESOURCE, sid.toStringFormal(), key);
    }

    @Test
    public void setURLExpires_should200GivenWriteAccess()
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
                .contentType(JSON)
                .content("8000")
        .expect()
                .statusCode(SC_OK)
                .body("key", equalTo(key))
                .body("token", not(equalTo(token)))
                .body("has_password", equalTo(false))
                .body("password", nullValue())
                .body("expires", equalTo(3))
                .body("require_login", equalTo(false))
        .when()
                .put(URL_EXPIRES_RESOURCE, sid.toStringFormal(), key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.set_expiry", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));
        assertEquals("8000", payload.get("expiry"));

        Map caller = (Map)payload.get("caller");

        assertTrue(caller.containsKey("email"));
        assertEquals(user.getString(), caller.get("acting_as"));
        assertTrue(caller.containsKey("device"));
    }

    @Test
    public void setURLExpires_should400GivenBadInput()
    {
        givenWriteAccess()
                .contentType(JSON)
                .content("bad input")
        .expect()
                .statusCode(SC_BAD_REQUEST)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(URL_EXPIRES_RESOURCE, sid.toStringFormal(), key);
    }

    @Test
    public void setURLRequireLogin_should200GivenWriteAccess()
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
        .expect()
                .statusCode(SC_OK)
                .body("key", equalTo(key))
                .body("token", not(equalTo(token)))
                .body("has_password", equalTo(false))
                .body("password", nullValue())
                .body("expires", equalTo(0))
                .body("require_login", equalTo(true))
        .when()
                .put(URL_REQUIRE_LOGIN_RESOURCE, sid.toStringFormal(), key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.set_require_login", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));
        assertEquals("true", payload.get("require_login"));

        Map caller = (Map)payload.get("caller");

        assertTrue(caller.containsKey("email"));
        assertEquals(user.getString(), caller.get("acting_as"));
        assertTrue(caller.containsKey("device"));
    }

    @Test
    public void removeURL_should204GivenWriteAccess()
            throws Exception
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
        .expect()
                .statusCode(SC_NO_CONTENT)
        .when()
                .delete(SINGLE_URL_SHARE_RESOURCE, sid.toStringFormal(), key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.delete", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));

        Map caller = (Map)payload.get("caller");

        assertTrue(caller.containsKey("email"));
        assertEquals(user.getString(), caller.get("acting_as"));
        assertTrue(caller.containsKey("device"));
    }

    @Test
    public void removeURLPassword_should200GivenWriteAccess()
            throws Exception
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
        .expect()
                .statusCode(SC_OK)
                .body("key", equalTo(key))
                .body("has_password", equalTo(false))
        .when()
                .delete(URL_PASSWORD_RESOURCE, sid.toStringFormal(), key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.remove_password", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));

        Map caller = (Map)payload.get("caller");

        assertTrue(caller.containsKey("email"));
        assertEquals(user.getString(), caller.get("acting_as"));
        assertTrue(caller.containsKey("device"));
    }

    @Test
    public void removeURLExpires_should200GivenWriteAccess()
            throws Exception
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
        .expect()
                .statusCode(SC_OK)
                .body("key", equalTo(key))
                .body("token", not(equalTo(token)))
                .body("expires", equalTo(0))
        .when()
                .delete(URL_EXPIRES_RESOURCE, sid.toStringFormal(), key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.remove_expiry", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));

        Map caller = (Map)payload.get("caller");

        assertTrue(caller.containsKey("email"));
        assertEquals(user.getString(), caller.get("acting_as"));
        assertTrue(caller.containsKey("device"));
    }

    @Test
    public void removeURLRequireLogin_should200GivenWriteAccess()
            throws Exception
    {
        mockTime(5000L);

        givenWriteAccess()
                .header(X_REAL_IP, "4.2.2.2")
        .expect()
                .statusCode(SC_OK)
                .body("key", equalTo(key))
                .body("require_login", equalTo(false))
        .when()
                .delete(URL_REQUIRE_LOGIN_RESOURCE, sid.toStringFormal(), key);

        Map<String, Object> payload = auditClient.getLastEventPayloadAndReset();

        assertEquals("LINK", payload.get("topic"));
        assertEquals("link.set_require_login", payload.get("event"));
        assertEquals("4.2.2.2", payload.get("ip"));
        assertEquals("5000", payload.get("timestamp"));
        assertEquals(key, payload.get("key"));
        assertEquals("false", payload.get("require_login"));

        Map caller = (Map)payload.get("caller");

        assertTrue(caller.containsKey("email"));
        assertEquals(user.getString(), caller.get("acting_as"));
        assertTrue(caller.containsKey("device"));
    }

    @Test
    public void allRoutes_should403GivenInsufficientAccess() throws Exception
    {
        for (TestPath path : allPaths()) {
            path.begin(path._method == HttpMethod.GET
                    // get requests only requires read access
                    ? givenLinkSharingAccess()
                    : givenReadAccess())
            .expect()
                    .statusCode(SC_FORBIDDEN)
                    .body("type", equalTo("FORBIDDEN"))
            .when()
                    .run(sid.toStringFormal(), key);
        }
    }

    @Test
    public void allRoutes_should403IfNotOwner() throws Exception
    {
        addUser(sid, other, Permissions.EDITOR);

        for (TestPath path : allPaths()) {
            path.begin(givenOtherAccess())
            .expect()
                    .statusCode(SC_FORBIDDEN)
                    .body("type", equalTo("FORBIDDEN"))
            .when()
                    .run(sid.toStringFormal(), key);
        }
    }

    @Test
    public void allRoutes_should404IfNotMember() throws Exception
    {
        for (TestPath path : allPaths()) {
            path.begin(givenOtherAccess())
            .expect()
                    .statusCode(SC_NOT_FOUND)
                    .body("type", equalTo("NOT_FOUND"))
            .when()
                    .run(sid.toStringFormal(), key);
        }
    }

    @Test
    public void allRoutes_should404IfStoreNotFound() throws Exception
    {
        // seems excessive, but I'd rather not dig into a failing test only to find conflicting sids
        SID sid2;
        do {
            sid2 = SID.generate();
        } while (sid.equals(sid2));

        for (TestPath path : allPaths()) {
            path.begin(givenWriteAccess())
            .expect()
                    .statusCode(SC_NOT_FOUND)
                    .body("type", equalTo("NOT_FOUND"))
            .when()
                    .run(sid2.toStringFormal(), key);
        }
    }

    @Test
    public void allRoutes_should404IfLinkedObjectNotInStore() throws Exception
    {
        SID sid2 = mkShare("fake_share2", user);

        // exclude createURL & listStoreURLs because it doesn't make sense
        for (TestPath path : allPaths().stream()
                .filter(testCase -> !testCase._path.equals(URL_SHARES_RESOURCE))
                .collect(Collectors.toSet())) {
            path.begin(givenWriteAccess())
            .expect()
                    .statusCode(SC_NOT_FOUND)
                    .body("type", equalTo("NOT_FOUND"))
            .when()
                    .run(sid2.toStringFormal(), key);
        }
    }

    private String formatJSONContent(Map<String, Object>fields)
    {
        return fields.entrySet().stream()
                .map(entry -> String.format("\"%s\":\"%s\"",
                        entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private Set<TestPath> allPaths()
    {
        return ImmutableSet.of(
                new TestPath(HttpMethod.GET, URL_SHARES_RESOURCE),
                new TestPath(HttpMethod.GET, SINGLE_URL_SHARE_RESOURCE),
                new TestPath(HttpMethod.POST, URL_SHARES_RESOURCE)
                        .withContent(JSON, urlContent),
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
                    ? _method.apply(_response, _path, pathParams[0])
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
