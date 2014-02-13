/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.id.SID;
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

@SuppressWarnings("unchecked")
public class TestUsersResources extends AbstractResourceTest
{
    static {
        LogUtil.setLevel(Level.INFO);
        LogUtil.enableConsoleLogging();
    }
    private final String RESOURCE_BASE = "/v1.1/users";
    private final String RESOURCE = RESOURCE_BASE + "/{email}";

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
    public void shouldReturn304WhenEtagMatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        String etag = sharesEtag(user);
        givenReadAccess()
                .header(Names.IF_NONE_MATCH, etag)
        .expect()
                .statusCode(304)
                .header(Names.ETAG, etag)
        .when().log().everything()
                .get(RESOURCE + "/shares", user.getString());
    }

    @Test
    public void shouldReturn200WhenEtagMismatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        String etag = sharesEtag(user);
        givenReadAccess()
                .header(Names.IF_NONE_MATCH, "totallynotavalidetag")
        .expect()
                .statusCode(200)
                .body("id", hasItem(equalTo(sid.toStringFormal())))
                .body("name", hasItem(equalTo("Test")))
                .body("members.email", hasItem(hasItem(user.getString())))
                .body("members.permissions", hasItem(hasItem(hasItems("WRITE", "MANAGE"))))
                .header(Names.ETAG, etag)
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
                .body("members.email", hasItems(user.getString(), other.getString()))
                .body("members.permissions", hasItems(hasItems("WRITE", "MANAGE"),
                        hasItems("WRITE")))
                .body("pending", emptyIterable())
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
        User u = createApiUser();

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
    public void delete_shouldFailNoPerm()
    {
        User u = createApiUser();
        givenReadAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .delete(RESOURCE, u.id().getString());
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

