package com.aerofs.sp.sparta.resources;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.id.GroupID;
import com.aerofs.ids.SID;
import com.aerofs.rest.api.SFGroupMember;
import com.aerofs.sp.common.SharedFolderState;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.internal.mapper.ObjectMapperType;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.junit.Test;

import static org.hamcrest.Matchers.*;

@SuppressWarnings("unchecked")
public class TestSharedFolderGroups extends AbstractResourceTest {
    private static final String V13_RESOURCE = "/v1.3/shares/{sid}/";
    private static final String GROUPS_RESOURCE = V13_RESOURCE + "groups/";
    private static final String SINGLE_GROUP_RESOURCE = GROUPS_RESOURCE + "{gid}";

    @Test
    public void shouldReturnGroupMembers() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addGroup(sid, gid, Permissions.EDITOR, user);
        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("permissions", hasItems(hasItems("WRITE")))
                .body("id", hasItem(gid.getString()))
        .when().log().everything()
                .get(GROUPS_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldReturn304WhenListGroupsWithEtagMatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addGroup(sid, gid, Permissions.EDITOR, user);
        givenReadAccess()
                .header(Names.IF_NONE_MATCH, getEtag(sid))
        .expect()
                .statusCode(304)
        .when().log().everything()
                .get(GROUPS_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldReturnGroupsWithEtagMismatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addGroup(sid, gid, Permissions.EDITOR, user);
        givenReadAccess()
                .header(Names.IF_NONE_MATCH, "\"deadbeef\"")
        .expect()
                .statusCode(200)
                .body("permissions", hasItems(hasItems("WRITE")))
                .body("id", hasItem(gid.getString()))
        .when().log().everything()
                .get(GROUPS_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenNonMemberTriesToListGroupMembers() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .get(GROUPS_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldAddGroupMembers() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");

        givenWriteAccess()
                .contentType(ContentType.JSON)
                .body(new SFGroupMember(gid.getString(), "A group",
                        new String[]{"WRITE"}), ObjectMapperType.GSON)
        .expect()
                .statusCode(201)
                .header(Names.LOCATION,
                        "https://localhost:" + sparta.getListeningPort() + "/v1.3/shares/" + sid.toStringFormal() +
                                "/groups/" + gid.getString())
                .body("permissions", hasItem("WRITE"))
                .body("id", equalTo(gid.getString()))
        .when().log().everything()
                .post(GROUPS_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldAddGroupMemberWithEtagMatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");

        givenWriteAccess()
                .header(Names.IF_MATCH, getEtag(sid))
                .contentType(ContentType.JSON)
                .body(new SFGroupMember(gid.getString(), "A group",
                        new String[]{"WRITE"}), ObjectMapperType.GSON)
        .expect()
                .statusCode(201)
                .header(Names.LOCATION,
                        "https://localhost:" + sparta.getListeningPort() + "/v1.3/shares/" + sid.toStringFormal() +
                                "/groups/" + gid.getString())
                .body("permissions", hasItem("WRITE"))
                .body("id", equalTo(gid.getString()))
        .when().log().everything()
                .post(GROUPS_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenGroupDoesNotExist() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenWriteAccess()
                .contentType(ContentType.JSON)
                .body(new SFGroupMember("123", "A group", new String[]{"WRITE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such group"))
        .when().log().everything()
                .post(GROUPS_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldReturn409WhenGroupMemberAlreadyExists() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addGroup(sid, gid, Permissions.EDITOR, user);

        givenWriteAccess()
                .contentType(ContentType.JSON)
                .body(new SFGroupMember(gid.getString(), null, new String[]{"WRITE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(409)
                .body("type", equalTo("CONFLICT"))
                .body("message", equalTo("Member already exists"))
        .when().log().everything()
                .post(GROUPS_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldReturn412WhenAddGroupWithEtagMismatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");

        givenWriteAccess()
                .contentType(ContentType.JSON)
                .header(Names.IF_MATCH, "\"deadbeef\"")
                .body(new SFGroupMember(gid.getString(), "A group",
                        new String[]{"WRITE"}), ObjectMapperType.GSON)
        .expect()
                .statusCode(412)
                .header(Names.ETAG, getEtag(sid))
        .when().log().everything()
                .post(GROUPS_RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldReturn403WhenEditorModifiesGroup() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addUser(sid, other, Permissions.EDITOR);

        givenOtherAccess()
                .contentType(ContentType.JSON)
                .body(new SFGroupMember(gid.getString(), null, new String[]{"WRITE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
                .body("message", equalTo("Not allowed to manage members of this shared folder"))
        .when().log().everything()
                .post(GROUPS_RESOURCE, sid.toStringFormal());

        addGroup(sid, gid, Permissions.EDITOR, user);
        givenOtherAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
                .body("message", equalTo("Not allowed to manage members of this shared folder"))
        .when().log().everything()
                .delete(SINGLE_GROUP_RESOURCE, sid.toStringFormal(), gid.getString());

        givenOtherAccess()
                .contentType(ContentType.JSON)
                .content(new SFGroupMember(null, null, new String[]{"MANAGE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
                .body("message", equalTo("Not allowed to manage members of this shared folder"))
        .when().log().everything()
                .put(SINGLE_GROUP_RESOURCE, sid.toStringFormal(), gid.getString());
    }

    @Test
    public void shouldGetGroup() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addGroup(sid, gid, Permissions.EDITOR, user);

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(gid.getString()))
                .body("permissions", hasItems("WRITE"))
        .when().log().everything()
                .get(SINGLE_GROUP_RESOURCE, sid.toStringFormal(), gid.getString());
    }

    @Test
    public void shouldReturn404WhenNonMemberTriesToAccessGroups() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addGroup(sid, gid, Permissions.EDITOR, user);

        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .get(SINGLE_GROUP_RESOURCE, sid.toStringFormal(), gid.getString());

        givenOtherAccess()
                .contentType(ContentType.JSON)
                .body(new SFGroupMember(gid.getString(), null, new String[]{"WRITE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .post(GROUPS_RESOURCE, sid.toStringFormal());

        givenOtherAccess()
                .contentType(ContentType.JSON)
                .content(new SFGroupMember(null, null, new String[]{"MANAGE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .put(SINGLE_GROUP_RESOURCE, sid.toStringFormal(), gid.getString());

        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .delete(SINGLE_GROUP_RESOURCE, sid.toStringFormal(), gid.getString());
    }

    @Test
    public void shouldUpdateGroup() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addGroup(sid, gid, Permissions.EDITOR, user);

        givenWriteAccess()
                .contentType(ContentType.JSON)
                .content(new SFGroupMember(null, null, new String[]{"MANAGE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(200)
                .body("id", equalTo(gid.getString()))
                .body("permissions", hasItems("MANAGE"))
        .when().log().everything()
                .put(SINGLE_GROUP_RESOURCE, sid.toStringFormal(), gid.getString());
    }

    @Test
    public void shouldUpdateGroupWithEtagMatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addGroup(sid, gid, Permissions.EDITOR, user);

        givenWriteAccess()
                .contentType(ContentType.JSON)
                .header(Names.IF_MATCH, getEtag(sid))
                .content(new SFGroupMember(null, null, new String[]{"MANAGE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(200)
                .body("id", equalTo(gid.getString()))
                .body("permissions", hasItems("MANAGE"))
        .when().log().everything()
                .put(SINGLE_GROUP_RESOURCE, sid.toStringFormal(), gid.getString());
    }

    @Test
    public void shouldReturn412WhenUpdateGroupWithEtagMismatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addGroup(sid, gid, Permissions.EDITOR, user);

        givenWriteAccess()
                .contentType(ContentType.JSON)
                .header(Names.IF_MATCH, "\"deadbeef\"")
                .content(new SFGroupMember(null, null, new String[]{"MANAGE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(412)
                .header(Names.ETAG, getEtag(sid))
        .when().log().everything()
                .put(SINGLE_GROUP_RESOURCE, sid.toStringFormal(), gid.getString());
    }

    @Test
    public void shouldDeleteGroup() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addGroup(sid, gid, Permissions.EDITOR, user);

        givenWriteAccess()
        .expect()
                .statusCode(204)
        .when().log().everything()
                .delete(SINGLE_GROUP_RESOURCE, sid.toStringFormal(), gid.getString());
    }

    @Test
    public void shouldDeleteGroupWithEtagMatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addGroup(sid, gid, Permissions.EDITOR, user);

        givenWriteAccess()
                .header(Names.IF_MATCH, getEtag(sid))
        .expect()
                .statusCode(204)
        .when().log().everything()
                .delete(SINGLE_GROUP_RESOURCE, sid.toStringFormal(), gid.getString());
    }

    @Test
    public void shouldReturn412WhenDeleteGroupWithEtagMismatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addGroup(sid, gid, Permissions.EDITOR, user);

        givenWriteAccess()
                .header(Names.IF_MATCH, "\"deadbeef\"")
        .expect()
                .statusCode(412)
                .header(Names.ETAG, getEtag(sid))
        .when().log().everything()
                .delete(SINGLE_GROUP_RESOURCE, sid.toStringFormal(), gid.getString());
    }

    @Test
    public void shouldIncludeGroupPermissions() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        GroupID gid = mkGroup("A Group");
        addUserToGroup(gid, other);
        addGroup(sid, gid, Permissions.VIEWER, user);
        sqlTrans.begin();
        factSF.create(sid).setState(factUser.create(other), SharedFolderState.JOINED);
        sqlTrans.commit();

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("permissions", emptyIterable())
        .when().log().everything()
                .get(V13_RESOURCE + "members/{email}", sid.toStringFormal(), other.getString());

        addUser(sid, other, Permissions.EDITOR);

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("permissions", hasItems("WRITE"))
        .when().log().everything()
                .get(V13_RESOURCE + "members/{email}", sid.toStringFormal(), other.getString());
    }

}
