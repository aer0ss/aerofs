/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.id.SID;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.junit.Test;

import static com.jayway.restassured.RestAssured.expect;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;

@SuppressWarnings("unchecked")
public class TestUsersResources extends AbstractResourceTest
{
    static {
        LogUtil.setLevel(Level.INFO);
        LogUtil.enableConsoleLogging();
    }

    private final String RESOURCE = "/v1.1/users/{email}";

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
                .body("members.permissions", hasItems(hasItems("WRITE", "MANAGE"), hasItems("WRITE")))
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
}
