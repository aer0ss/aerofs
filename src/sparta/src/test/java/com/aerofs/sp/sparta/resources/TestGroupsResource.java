/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.id.GroupID;
import com.aerofs.ids.SID;
import com.aerofs.rest.api.GroupMember;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.internal.mapper.ObjectMapperType;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;

public class TestGroupsResource extends AbstractResourceTest
{
    private final String BASE_RESOURCE = "/v1.3/groups/";
    private final String RESOURCE = BASE_RESOURCE + "{gid}";
    private final String MEMBER_BASE_RESOURCE = RESOURCE + "/members";
    private final String MEMBER_RESOURCE = MEMBER_BASE_RESOURCE + "/{email}";
    private final String SHARES_BASE_RESOURCE = RESOURCE + "/shares";
    private GroupID _groupID;
    private SID _sid;

    @Before
    public void setUpGroups()
            throws Exception
    {
        _groupID = mkGroup("A Group");
        _sid = mkShare("A Folder", user);
    }

    @Test
    public void shouldReturn201OnGroupCreation()
    {
        givenAdminAccess()
                .contentType(ContentType.JSON)
                .content(new com.aerofs.rest.api.Group(null, "another group", null), ObjectMapperType.GSON)
                .expect()
        .statusCode(201)
                .header(Names.LOCATION, startsWith(
                        "https://localhost:" + sparta.getListeningPort() + BASE_RESOURCE))
                .body("name", equalTo("another group"))
                .body("members", emptyIterable())
        .when()
                .post(BASE_RESOURCE);
    }

    @Test
    public void shouldReturn403WhenNonAdminCreatesGroup()
    {
        givenWriteAccess()
                .contentType(ContentType.JSON)
                .content(new com.aerofs.rest.api.Group(null, "another group", null),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .post(BASE_RESOURCE);
    }

    @Test
    public void shouldReturn400WhenGroupCreatedWithoutName()
    {
        givenAdminAccess()
                .contentType(ContentType.JSON)
                .content(new com.aerofs.rest.api.Group(null, null, null), ObjectMapperType.GSON)
        .expect()
                .statusCode(400)
        .when()
                .post(BASE_RESOURCE);
    }

    @Test
    public void shouldAcceptSharedSecretToCreateGroup()
    {
        givenSecret("polaris", deploymentSecret)
            .contentType(ContentType.JSON)
            .content(new com.aerofs.rest.api.Group(null, "A Group", null),
                    ObjectMapperType.GSON)
        .expect()
            .statusCode(201)
            .header(Names.LOCATION, startsWith(
                    "https://localhost:" + sparta.getListeningPort() + BASE_RESOURCE))
            .body("name", equalTo("A Group"))
            .body("members", emptyIterable())
        .when()
            .post(BASE_RESOURCE);
    }

    @Test
    public void shouldReturnGroup()
            throws Exception
    {
        addUserToGroup(_groupID, other);

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(_groupID.getString()))
                .body("members.email", contains(other.getString()))
        .when()
                .get(RESOURCE, _groupID.getString());
    }

    @Test
    public void shouldReturn404WhenRequestedGroupNotFound()
            throws Exception
    {
        givenWriteAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such group"))
        .when()
                .get(RESOURCE, "123");
    }

    @Test
    public void shouldReturn404WhenDifferentOrgRequestsGroup()
            throws Exception
    {
        givenOtherOrgAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such group"))
        .when()
                .get(RESOURCE, _groupID.getString());
    }

    @Test
    public void shouldDeleteGroup()
            throws Exception
    {
        givenAdminAccess()
        .expect()
                .statusCode(204)
        .when()
                .delete(RESOURCE, _groupID.getString());
    }

    @Test
    public void shouldReturn404WhenDeletedGroupNotFound()
            throws Exception
    {
        givenAdminAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such group"))
        .when()
                .delete(RESOURCE, "123");
    }

    @Test
    public void shouldReturn404WhenDifferentOrgDeletesGroup()
            throws Exception
    {
        givenOtherOrgAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such group"))
        .when()
                .delete(RESOURCE, _groupID.getString());
    }

    @Test
    public void deletingGroupShouldRequireAdmin()
            throws Exception
    {
        givenWriteAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .delete(RESOURCE, _groupID.getString());
    }

    @Test
    public void shouldListSharesForGroup()
            throws Exception
    {
        addGroup(_sid, _groupID, Permissions.EDITOR, user);
        addUserToGroup(_groupID, other);

        givenWriteAccess()
        .expect()
                .statusCode(200)
                .root("[0]")
                .body("id", equalTo(_sid.toStringFormal()))
                .body("groups[0].id", equalTo(_groupID.getString()))
                .body("groups[0].permissions", contains("WRITE"))
        .when()
                .get(SHARES_BASE_RESOURCE, _groupID.getString());
    }

    @Test
    public void shouldListMembersOfGroup()
            throws Exception
    {
        addUserToGroup(_groupID, user);

        givenWriteAccess()
        .expect()
                .statusCode(200)
                .body("email", hasItem(user.getString()))
        .when()
                .get(MEMBER_BASE_RESOURCE, _groupID.getString());
    }

    @Test
    public void shouldListMemberOfGroup()
            throws Exception
    {
        addUserToGroup(_groupID, user);

        givenWriteAccess()
        .expect()
                .statusCode(200)
                .body("email", equalTo(user.getString()))
        .when()
                .get(MEMBER_RESOURCE, _groupID.getString(), user.getString());
    }

    @Test
    public void shouldReturn404WhenMemberNotInGroup()
            throws Exception
    {
        givenWriteAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("User is not a member of group"))
        .when()
                .get(MEMBER_RESOURCE, _groupID.getString(), user.getString());
    }

    @Test
    public void shouldAddMemberToGroup()
            throws Exception
    {
        givenAdminAccess()
                .contentType(ContentType.JSON)
                .content(new GroupMember(user.getString(), "A", "User"), ObjectMapperType.GSON)
        .expect()
                .statusCode(201)
                .header(Names.LOCATION, equalTo("https://localhost:" + sparta.getListeningPort() +
                        BASE_RESOURCE + _groupID.getString() + "/members/" + user.getString()))
                .body("email", equalTo(user.getString()))
        .when()
                .post(MEMBER_BASE_RESOURCE, _groupID.getString());
    }

    @Test
    public void shouldReturn409WhenMemberAlreadyInGroup()
            throws Exception
    {
        addUserToGroup(_groupID, user);

        givenAdminAccess()
                .contentType(ContentType.JSON)
                .content(new GroupMember(user.getString(), "A", "User"), ObjectMapperType.GSON)
        .expect()
                .statusCode(409)
                .body("type", equalTo("CONFLICT"))
        .when()
                .post(MEMBER_BASE_RESOURCE, _groupID.getString());
    }

    @Test
    public void shouldDeleteMemberFromGroup()
            throws Exception
    {
        addUserToGroup(_groupID, user);

        givenAdminAccess()
        .expect()
                .statusCode(204)
        .when()
                .delete(MEMBER_RESOURCE, _groupID.getString(), user.getString());
    }

    @Test
    public void shouldReturn404WhenDeletingNonMember()
            throws Exception
    {
        givenAdminAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("User is not a member of group"))
        .when()
                .delete(MEMBER_RESOURCE, _groupID.getString(), user.getString());
    }

    @Test
    public void modifyingGroupsShouldRequireAdmin()
            throws Exception
    {
        givenWriteAccess()
                .contentType(ContentType.JSON)
                .content(new GroupMember(user.getString(), "A", "User"), ObjectMapperType.GSON)
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .post(MEMBER_BASE_RESOURCE, _groupID.getString());

        givenWriteAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .delete(MEMBER_RESOURCE, _groupID.getString(), user.getString());
    }

    @Test
    public void shouldRequireGroupReadScope()
            throws Exception
    {
        addUserToGroup(_groupID, user);
        String resourceWithGroupId = BASE_RESOURCE + _groupID.getString();
        String[] needReadScope = {
                resourceWithGroupId,
                resourceWithGroupId + "/members",
                resourceWithGroupId + "/shares",
                resourceWithGroupId + "/members/" + user.getString()};

        for (String location : needReadScope) {
            givenNoGroupAccess()
            .expect()
                    .statusCode(403)
                    .body("type", equalTo("FORBIDDEN"))
            .when()
                    .get(location);
        }
    }

    @Test
    public void shouldListGroups()
            throws Exception
    {
        addUserToGroup(_groupID, user);

        givenReadAccess()
        .expect()
                .statusCode(200)
                .root("[0]")
                .body("id", equalTo(_groupID.getString()))
                .body("members[0].email", equalTo(user.getString()))
        .when()
                .get(BASE_RESOURCE);
    }
}
