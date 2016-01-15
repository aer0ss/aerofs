package com.aerofs.sp.sparta.resources;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.id.RestObject;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.restless.providers.GsonProvider;
import com.aerofs.sp.server.AccessCodeProvider;
import com.aerofs.sp.server.Zelda;
import com.google.common.collect.ImmutableMap;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

import static com.aerofs.sp.sparta.resources.SharedFolderResource.X_REAL_IP;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * N.B. the routes covered in this test suite are currently undocumented.
 *
 * TODO (AT): implement and add test coverage for internal failures and ensure access codes and
 *   access tokens are cleaned up.
 */
public class TestLegacyUrlShareResource extends AbstractResourceTest
{
    private static final String URL_SHARES_RESOURCE = "/v1.4/shares/{sid}/urls";

    private String token;

    private SID sid;
    private RestObject soid;

    private String key;
    private String urlContent;
    private String urlContentWithFields;

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
    public void listURLs_should200GivenReadAccess()
            throws Exception
    {
        mkUrl(soid, token, user, false, null, null);
        mkUrl(soid, token, user, false, null, null);
        mkUrl(soid, token, user, false, null, null);

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("urls", iterableWithSize(4))
        .when()
                .get(URL_SHARES_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void listURLs_should403GivenInsufficientAccess() throws Exception
    {
        givenLinkSharingAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .get(URL_SHARES_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void listURLs_should403IfNotOwner() throws Exception
    {
        addUser(sid, other, Permissions.EDITOR);

        givenOtherAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .get(URL_SHARES_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void listURLs_should404IfNotMember() throws Exception
    {
        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .get(URL_SHARES_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void listURLs_should404IfStoreNotFound() throws Exception
    {
        // seems excessive, but I'd rather not dig into a failing test only to find conflicting sids
        SID sid2;
        do {
            sid2 = SID.generate();
        } while (sid.equals(sid2));

        givenWriteAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .get(URL_SHARES_RESOURCE, sid2.toStringFormal());
    }

    @Test
    public void createURL_should201GivenWriteAccess()
            throws Exception
    {
        mockTime(5000L);

        LocationMatchers matchers = new LocationMatchers(
                String.format("https://localhost:%s/v1.4/links/",
                        sparta.getListeningPort()));

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
                .post(URL_SHARES_RESOURCE, sid.toStringFormal());

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
    public void createURL_should401GivenNonDelegatedService()
    {
        givenSecret("fake_service", deploymentSecret)
                .contentType(JSON)
                .content(urlContent)
        .expect()
                .statusCode(401)
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
        };

        for (String soid : soids) {
            givenWriteAccess()
                    .contentType(JSON)
                    .content(formatJSONContent(ImmutableMap.of("soid", soid)))
            .expect()
                    .statusCode(400)
                    .body("type", equalTo("BAD_ARGS"))
            .when()
                    .post(URL_SHARES_RESOURCE, sid.toStringFormal());
        }
    }

    @Test
    public void createURL_should403GivenInsufficientAccess() throws Exception
    {
        givenReadAccess()
                .contentType(JSON)
                .content(urlContent)
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .post(URL_SHARES_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void createURL_should403IfNotOwner() throws Exception
    {
        addUser(sid, other, Permissions.EDITOR);

        givenOtherAccess()
                .contentType(JSON)
                .content(urlContent)
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .post(URL_SHARES_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void createURL_should404IfNotMember() throws Exception
    {
        givenOtherAccess()
                .contentType(JSON)
                .content(urlContent)
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .post(URL_SHARES_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void createURL_should400OnStoreMismatch() throws Exception
    {
        // seems excessive, but I'd rather not dig into a failing test only to find conflicting sids
        SID sid2;
        do {
            sid2 = SID.generate();
        } while (sid.equals(sid2));

        givenWriteAccess()
                .contentType(JSON)
                .content(urlContent)
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .post(URL_SHARES_RESOURCE, sid2.toStringFormal());
    }

    private String formatJSONContent(Map<String, Object>fields)
    {
        return fields.entrySet().stream()
                .map(entry -> String.format("\"%s\":\"%s\"",
                        entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }
}
