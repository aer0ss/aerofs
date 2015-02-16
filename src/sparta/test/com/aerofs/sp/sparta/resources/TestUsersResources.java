/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.base.acl.Permissions;
import com.aerofs.ids.SID;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.internal.mapper.ObjectMapperType;
import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Response;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.junit.Test;

import javax.ws.rs.core.Response.Status;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.path.json.JsonPath.from;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
public class TestUsersResources extends AbstractResourceTest
{
    private final String RESOURCE_BASE = "/v1.1/users";
    private final String RESOURCE = RESOURCE_BASE + "/{email}";
    private final String QUOTA_RESOURCE = "/v1.2/users/{email}/quota";
    private final String TWO_FACTOR_RESOURCE = "/v1.3/users/{email}/two_factor";

    @Test
    public void shouldReturn401WhenTokenMissing() throws Exception
    {
        expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when().log().everything()
                .get(RESOURCE, user.getString());
    }

    @Test
    public void shouldReturn404ForNonExistingUser() throws Exception
    {
        givenReadAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().log().everything()
                .get(RESOURCE, "totallynotavaliduserid");
    }

    @Test
    public void shouldGetSelf() throws Exception
    {
        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("email", equalTo(user.getString()))
                .body("first_name", equalTo("User"))
                .body("last_name", equalTo("Foo"))
                .body("shares", emptyIterable())
        .when().log().everything()
                .get(RESOURCE, user.getString());
    }

    @Test
    public void shouldGetSelfImplicit() throws Exception
    {
        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("email", equalTo(user.getString()))
                .body("first_name", equalTo("User"))
                .body("last_name", equalTo("Foo"))
                .body("shares", emptyIterable())
        .when().log().everything()
                .get(RESOURCE, "me");
    }

    @Test
    public void shouldGetOtherUserOfSameOrgIfAdmin() throws Exception
    {
        givenAdminAccess()
        .expect()
                .statusCode(200)
                .body("email", equalTo(user.getString()))
                .body("first_name", equalTo("User"))
                .body("last_name", equalTo("Foo"))
                .body("shares", emptyIterable())
        .when().log().everything()
                .get(RESOURCE, user.getString());
    }

    @Test
    public void shouldReturn404WhenGetByOther() throws Exception
    {
        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().log().everything()
                .get(RESOURCE, user.getString());
    }

    @Test
    public void shouldListOwnShares() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("id", hasItem(equalTo(sid.toStringFormal())))
                .body("name", hasItem(equalTo("Test")))
                .body("members.email", hasItem(hasItem(user.getString())))
                .body("members.permissions", hasItem(hasItem(hasItems("WRITE", "MANAGE"))))
        .when().log().everything()
                .get(RESOURCE + "/shares", user.getString());
    }

    @Test
    public void shouldListSharesWhenAdminOf() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenAdminAccess()
        .expect()
                .statusCode(200)
                .body("id", hasItem(equalTo(sid.toStringFormal())))
                .body("name", hasItem(equalTo("Test")))
                .body("members.email", hasItem(hasItem(user.getString())))
                .body("members.permissions", hasItem(hasItem(hasItems("WRITE", "MANAGE"))))
        .when().log().everything()
                .get(RESOURCE + "/shares", user.getString());
    }

    @Test
    public void shouldReturn404WhenListSharesByOther() throws Exception
    {
        mkShare("Test", user.getString());

        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().log().everything()
                .get(RESOURCE + "/shares", user.getString());
    }

    @Test
    public void shouldListInvitations() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, other, Permissions.EDITOR);

        givenOtherAccess()
        .expect()
                .statusCode(200)
                .body("share_id", hasItem(equalTo(sid.toStringFormal())))
                .body("share_name", hasItem(equalTo("Test")))
                .body("invited_by", hasItem(equalTo(user.getString())))
                .body("permissions", hasItem(hasItems("WRITE")))
        .when().log().everything()
                .get(RESOURCE + "/invitations", other.getString());
    }

    @Test
    public void shouldReturn404WhenListInvitationsByOther() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, other, Permissions.EDITOR);

        givenReadAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such user"))
        .when().log().everything()
                .get(RESOURCE + "/invitations", other.getString());
    }

    @Test
    public void shouldGetInvitation() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, other, Permissions.EDITOR);

        givenOtherAccess()
        .expect()
                .statusCode(200)
                .body("share_id", equalTo(sid.toStringFormal()))
                .body("share_name", equalTo("Test"))
                .body("invited_by", equalTo(user.getString()))
                .body("permissions", hasItems("WRITE"))
        .when().log().everything()
                .get(RESOURCE + "/invitations/{sid}", other.getString(), sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenGetInvitationByOther() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, other, Permissions.EDITOR);

        givenReadAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such user"))
        .when().log().everything()
                .get(RESOURCE + "/invitations/{sid}", other.getString(), sid.toStringFormal());
    }

    @Test
    public void shouldAcceptInvitation() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, other, Permissions.EDITOR);

        givenOtherAccess()
        .expect()
                .statusCode(201)
                .header(Names.LOCATION, "https://localhost:" + sparta.getListeningPort()
                        + "/v1.1/shares/" + sid.toStringFormal())
                .body("id", equalTo(sid.toStringFormal()))
                .body("name", equalTo("Test"))
                .body("is_external", equalTo(false))
                .body("members.email", hasItems(user.getString(), other.getString()))
                .body("members.permissions", hasItems(hasItems("WRITE", "MANAGE"),
                        hasItems("WRITE")))
                .body("pending", emptyIterable())
        .when().log().everything()
                .post(RESOURCE + "/invitations/{sid}", other.getString(), sid.toStringFormal());
    }

    @Test
    public void shouldAcceptInvitationAsExternal() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, other, Permissions.EDITOR);

        givenOtherAccess()
                .queryParam("external", "1")
        .expect()
                .statusCode(201)
                .header(Names.LOCATION, "https://localhost:" + sparta.getListeningPort()
                        + "/v1.1/shares/" + sid.toStringFormal())
                .body("id", equalTo(sid.toStringFormal()))
                .body("name", equalTo("Test"))
                .body("is_external", equalTo(true))
                .body("members.email", hasItems(user.getString(), other.getString()))
                .body("members.permissions", hasItems(hasItems("WRITE", "MANAGE"),
                        hasItems("WRITE")))
                .body("pending", emptyIterable())
        .when().log().everything()
                .post(RESOURCE + "/invitations/{sid}", other.getString(), sid.toStringFormal());
    }

    @Test
    public void shouldReturn400WhenAcceptingInvitationWithInvalidExternalParam() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, other, Permissions.EDITOR);

        givenOtherAccess()
                .queryParam("external", "hellatru")
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
                .body("message", equalTo("Invalid parameter: Invalid boolean value. Expected \"0\" or \"1\""))
        .when().log().everything()
                .post(RESOURCE + "/invitations/{sid}", other.getString(), sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenAcceptNonExistingInvitation() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such invitation"))
        .when().log().everything()
                .post(RESOURCE + "/invitations/{sid}", other.getString(), sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenAcceptInvitationByOther() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenReadAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such user"))
        .when().log().everything()
                .post(RESOURCE + "/invitations/{sid}", other.getString(), sid.toStringFormal());
    }

    @Test
    public void shouldIgnoreInvitation() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, other, Permissions.EDITOR);

        givenOtherAccess()
        .expect()
                .statusCode(204)
        .when().log().everything()
                .delete(RESOURCE + "/invitations/{sid}", other.getString(), sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenIgnoreNonExistingInvitation() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such invitation"))
        .when().log().everything()
                .delete(RESOURCE + "/invitations/{sid}", other.getString(), sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenIgnoreInvitationByOther() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenReadAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such user"))
        .when().log().everything()
                .delete(RESOURCE + "/invitations/{sid}", other.getString(), sid.toStringFormal());
    }


    @Test
    public void create_shouldSucceed() throws Exception
    {
        User u = newUser();

        Response response = givenAdminAccess()
                .content(userAttributes(u), ObjectMapperType.GSON)
                .contentType(ContentType.JSON)
                .post(RESOURCE_BASE);
        assertEquals(Status.CREATED.getStatusCode(), response.getStatusCode());
        assertEquals(
                "https://localhost:" + sparta.getListeningPort() + "/v1.1/users/"
                        + u.id().getString(),
                response.getHeader(Names.LOCATION));

        JsonPath jsonResponse = from(response.asString());
        assertEquals(jsonResponse.get("email"), u.id().getString());
        assertEquals(jsonResponse.get("first_name"), "firsty");
        assertEquals(jsonResponse.get("last_name"), "lasty");

        // newly created user is readable:
        givenAdminAccess()
        .expect()
                .statusCode(200)
                .body("email", equalTo(u.id().getString()))
                .body("first_name", equalTo("firsty"))
                .body("last_name", equalTo("lasty"))
                .body("shares", emptyIterable())
        .when()
                .get(RESOURCE, u.id().getString());
    }

    @Test
    public void create_shouldFailDupe() throws Exception
    {
        User u = createApiUser();

        // dupe create attempt:
        givenAdminAccess()
                .content(userAttributes(u), ObjectMapperType.GSON)
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(409)
        .when()
                .post(RESOURCE_BASE);
    }

    @Test
    public void create_shouldFailNoBody() throws Exception
    {
        // dupe create attempt:
        givenAdminAccess()
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(415)
        .when()
                .post(RESOURCE_BASE);
    }

    @Test
    public void create_shouldFailForNonAdmin() throws Exception
    {
        givenWriteAccess()
                .content(userAttributes(newUser()), ObjectMapperType.GSON)
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(403)
        .when()
                .post(RESOURCE_BASE);
    }

    @Test
    public void update_shouldSucceedForSelf()
    {
        givenWriteAccess()
                .content(ImmutableMap.of("email", user.getString(), "first_name", "Test",
                        "last_name", "Test"), ObjectMapperType.GSON)
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(200)
        .when()
                .put(RESOURCE, user.getString());
    }

    @Test
    public void update_shouldSucceedForAdmin()
    {
        User u = createApiUser();

        Response response = givenAdminAccess()
                .content(ImmutableMap.of("email", u.id().getString(), "first_name", "Test",
                        "last_name", "Test"), ObjectMapperType.GSON)
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(200)
        .when()
                .put(RESOURCE, u.id().getString());

        JsonPath jsonResp = from(response.asString());
        assertEquals(jsonResp.get("first_name"), "Test");
        assertEquals(jsonResp.get("last_name"), "Test");
    }

    @Test
    public void update_shouldFailNewUser()
    {
        User u = newUser();
        givenAdminAccess()
                .content(ImmutableMap.of("email", u.id().getString(), "first_name", "Test",
                        "last_name", "Test"), ObjectMapperType.GSON)
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .put(RESOURCE, u.id().getString());
    }

    @Test
    public void update_shouldFailNonAdmin()
    {
        User u = createApiUser();
        givenWriteAccess()
                .content(ImmutableMap.of("email", u.id().getString(), "first_name", "Test",
                        "last_name", "Test"), ObjectMapperType.GSON)
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .put(RESOURCE, u.id().getString());
    }

    @Test
    public void delete_shouldSucceed()
    {
        User u = createApiUser();
        givenAdminAccess()
        .expect()
                .statusCode(204)
        .when()
                .delete(RESOURCE, u.id().getString());
    }

    @Test
    public void delete_shouldFailNonExistentUser()
    {
        User u = newUser();
        givenAdminAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .delete(RESOURCE, u.id().getString());
    }

    @Test
    public void delete_shouldFailNotFound()
    {
        User u = createApiUser();
        givenWriteAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .delete(RESOURCE, u.id().getString());
    }

    @Test
    public void delete_shouldFailNoPerm()
    {
        User u = createApiUser();
        givenReadAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .delete(RESOURCE, u.id().getString());
    }

    // tests for updatePassword(..)

    @Test
    public void updatePassword_shouldSucceed() throws Exception
    {
        User u = createApiUser();
        givenAdminAccess()
                .contentType(ContentType.JSON)
                .body("\"new password\"")
        .expect()
                .statusCode(204)
        .when()
                .put(RESOURCE + "/password", u.id().getString());

        verify(passwordManagement).setPassword(eq(u.id()), any(byte[].class));
    }

    @Test
    public void updatePassword_shouldFailNoBody()
    {
        User u = createApiUser();
        givenAdminAccess()
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(RESOURCE + "/password", u.id().getString());
    }

    @Test
    public void updatePassword_shouldFailEmptyBody()
    {
        User u = createApiUser();
        givenAdminAccess()
                .contentType(ContentType.JSON)
                .body("")
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(RESOURCE + "/password", u.id().getString());
    }

    @Test
    public void updatePassword_shouldFailNonExistentUser()
    {
        User u = newUser();
        givenAdminAccess()
                .contentType(ContentType.JSON)
                .body("\"new password\"")
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .put(RESOURCE + "/password", u.id().getString());
    }

    @Test
    public void updatePassword_shouldFailNoPerm()
    {
        User u = createApiUser();
        givenReadAccess()
                .contentType(ContentType.JSON)
                .body("\"new password\"")
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .put(RESOURCE + "/password", u.id().getString());
    }

    // tests for deletePassword(..)

    @Test
    public void deletePassword_shouldSucceed() throws Exception
    {
        User u = createApiUser();
        givenAdminAccess()
        .expect()
                .statusCode(204)
        .when()
                .delete(RESOURCE + "/password", u.id().getString());
        verify(passwordManagement).revokePassword(eq(u.id()));
    }

    @Test
    public void deletePassword_shouldFailNonExistentUser()
    {
        User u = newUser();
        givenAdminAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .delete(RESOURCE + "/password", u.id().getString());
    }

    @Test
    public void deletePassword_shouldFailNoPerm()
    {
        User u = createApiUser();
        givenReadAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .delete(RESOURCE + "/password", u.id().getString());
    }

    @Test
    public void shouldGetOwnQuotaUsage() throws Exception
    {
        long bytesUsed = 42L * (long)1E9;
        long bytesAllowed = 9000L * (long)1E9;

        sqlTrans.begin();
        User _user = factUser.create(user);
        _user.setBytesUsed(bytesUsed);
        _user.getOrganization().setQuotaPerUser(bytesAllowed);
        sqlTrans.commit();

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("bytes_used", equalTo(bytesUsed))
                .body("bytes_allowed", equalTo(bytesAllowed))
        .when().log().everything()
                .get(QUOTA_RESOURCE, user.getString());
    }

    @Test
    public void shouldGetOtherUsersQuotaUsageIfAdmin() throws Exception
    {
        long bytesUsed = 42L * (long)1E9;
        long bytesAllowed = 9000L * (long)1E9;

        sqlTrans.begin();
        User _user = factUser.create(user);
        _user.setBytesUsed(bytesUsed);
        _user.getOrganization().setQuotaPerUser(bytesAllowed);
        sqlTrans.commit();

        givenAdminAccess()
        .expect()
                .statusCode(200)
                .body("bytes_used", equalTo(bytesUsed))
                .body("bytes_allowed", equalTo(bytesAllowed))
        .when().log().everything()
                .get(QUOTA_RESOURCE, user.getString());
    }

    @Test
    public void getTwoFactor_shouldSucceed() throws Exception
    {
        sqlTrans.begin();
        User _user = factUser.create(user);
        _user.setupTwoFactor();
        _user.enableTwoFactorEnforcement();
        sqlTrans.commit();

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("enforce", equalTo(true))
        .when().log().everything()
                .get(TWO_FACTOR_RESOURCE, user.getString());

        // Disable afterward to avoid two-factor auth messing with other tests
        sqlTrans.begin();
        _user.disableTwoFactorEnforcement();
        sqlTrans.commit();

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("enforce", equalTo(false))
        .when().log().everything()
                .get(TWO_FACTOR_RESOURCE, user.getString());
    }

    @Test
    public void deleteTwoFactor_shouldSucceed() throws Exception
    {
        sqlTrans.begin();
        User _user = factUser.create(user);
        _user.setupTwoFactor();
        _user.enableTwoFactorEnforcement();
        assertTrue(_user.shouldEnforceTwoFactor());
        sqlTrans.commit();

        givenAdminAccess()
        .expect()
                .statusCode(204)
        .when().log().everything()
                .delete(TWO_FACTOR_RESOURCE, user.getString());

        sqlTrans.begin();
        assertFalse(_user.shouldEnforceTwoFactor());
        sqlTrans.commit();
    }

    @Test
    public void deleteTwoFactor_shouldFailNoPerm() throws Exception
    {
        sqlTrans.begin();
        User _user = factUser.create(user);
        _user.setupTwoFactor();
        _user.enableTwoFactorEnforcement();
        assertTrue(_user.shouldEnforceTwoFactor());
        sqlTrans.commit();

        givenReadAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when().log().everything()
                .delete(TWO_FACTOR_RESOURCE, user.getString());

        sqlTrans.begin();
        assertTrue(_user.shouldEnforceTwoFactor());
        _user.disableTwoFactorEnforcement();
        sqlTrans.commit();

    }

    private ImmutableMap<String, String> userAttributes(User u)
    {
        return ImmutableMap.of(
                "email", u.id().getString(),
                "first_name", "firsty",
                "last_name", "lasty");
    }

    /**
     * Generate a new User object, use it to create a user via the /users API,
     * and return the User object.
     */
    private User createApiUser()
    {
        User u = newUser();
        givenAdminAccess()
                .content(userAttributes(u), ObjectMapperType.GSON)
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(Status.CREATED.getStatusCode())
        .when()
                .post(RESOURCE_BASE);
        return u;
    }
}

