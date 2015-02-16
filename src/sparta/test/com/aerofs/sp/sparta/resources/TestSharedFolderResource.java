/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;


import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.rest.api.Member;
import com.aerofs.rest.api.PendingMember;
import com.aerofs.rest.api.SharedFolder;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.internal.mapper.ObjectMapperType;
import com.jayway.restassured.specification.RequestSpecification;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Base64;
import java.util.Date;
import java.util.Random;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

@SuppressWarnings("unchecked")
public class TestSharedFolderResource extends AbstractResourceTest
{
    private final String BASE_RESOURCE = "/v1.1/shares/";
    private final String RESOURCE = BASE_RESOURCE + "{sid}";

    @Test
    public void shouldReturn401WhenTokenMissing() throws Exception
    {
        expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when().log().everything()
                .get(RESOURCE, SID.generate().toStringFormal());
    }

    @Test
    public void shouldReturn401WhenCertSerialUnknown() throws Exception
    {
        Long serial = (long)new Random().nextInt(1000);
        givenCert(DID.generate(), user, serial)
        .expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when().log().everything()
                .get(RESOURCE, SID.generate().toStringFormal());
    }

    private static final Base64.Encoder base64 = Base64.getEncoder();

    private static String encode(UserID user)
    {
        return BaseUtil.utf2string(base64.encode(BaseUtil.string2utf(user.getString())));
    }

    private RequestSpecification givenCert(DID did, UserID user, long serial)
    {
        return given()
                .header(Names.AUTHORIZATION,
                        "Aero-Device-Cert " + encode(user) + " " + did.toStringFormal())
                .header("DName", "CN=" + BaseSecUtil.getCertificateCName(user, did))
                .header("Serial", Long.toString(serial, 16))
                .header("Verify", "SUCCESS");
    }

    private RequestSpecification givenSecret(String service, String secret, DID did, UserID user)
    {
        return given()
                .header(Names.AUTHORIZATION,
                        "Aero-Delegated-User-Device "
                                + service + " " + secret + " "
                                + encode(user) + " " + did.toStringFormal());
    }

    private RequestSpecification givenSecret(String service, String secret)
    {
        return given()
                .header(Names.AUTHORIZATION,
                        "Aero-Service-Shared-Secret " + service + " " + secret);
    }

    @Test
    public void shouldReturn401WhenCertRevoked() throws Exception
    {
        DID did = DID.generate();
        Long serial = (long)new Random().nextInt(1000);

        sqlTrans.begin();
        CertificateDatabase certdb = inj.getInstance(CertificateDatabase.class);
        certdb.insertCertificate(serial, did, new Date());
        certdb.revokeCertificate(serial);
        sqlTrans.commit();

        givenCert(did, user, serial)
        .expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when().log().everything()
                .get(RESOURCE, SID.generate().toStringFormal());
    }

    @Test
    public void shouldReturn401WhenServiceSharedSecretInvalid() throws Exception
    {
        givenSecret("polaris", "notasharedsecret")
        .expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when().log().everything()
                .get(RESOURCE, SID.generate().toStringFormal());
    }

    @Test
    public void shouldReturn401WhenServiceSharedSecretMismatch() throws Exception
    {
        givenSecret("polaris", UniqueID.generate().toStringFormal())
        .expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when().log().everything()
                .get(RESOURCE, SID.generate().toStringFormal());
    }

    @Test
    public void shouldReturn401WhenDelegatedSharedSecretInvalid() throws Exception
    {
        givenSecret("polaris", "notasharedsecret", DID.generate(), user)
                .expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
                .when().log().everything()
                .get(RESOURCE, SID.generate().toStringFormal());
    }

    @Test
    public void shouldReturn401WhenDelegatedSharedSecretMismatch() throws Exception
    {
        givenSecret("polaris", UniqueID.generate().toStringFormal(), DID.generate(), user)
                .expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
                .when().log().everything()
                .get(RESOURCE, SID.generate().toStringFormal());
    }

    @Test
    public void shouldReturn401WhenCertDNameInvalid() throws Exception
    {
        DID did = DID.generate();
        Long serial = (long)new Random().nextInt(1000);

        sqlTrans.begin();
        CertificateDatabase certdb = inj.getInstance(CertificateDatabase.class);
        certdb.insertCertificate(serial, did, new Date());
        sqlTrans.commit();

        given()
                .header(Names.AUTHORIZATION,
                        "Aero-Device-Cert " + encode(user) + " " + did.toStringFormal())
                .header("DName", "CN=foo")
                .header("Serial", Long.toString(serial, 16))
                .header("Verify", "SUCCESS")
        .expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when().log().everything()
                .get(RESOURCE, SID.generate().toStringFormal());
    }

    @Test
    public void shouldReturn404ForNonExistingShare() throws Exception
    {
        givenReadAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .get(RESOURCE, SID.generate().toStringFormal());
    }

    @Test
    public void shouldListRootStore() throws Exception
    {
        SID sid = SID.rootSID(user);

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(sid.toStringFormal()))
                .body("members.email", hasItem(user.getString()))
                .body("members.permissions", hasItem(hasItems("MANAGE", "WRITE")))
                .body("pending", emptyIterable())
        .when()
                .get(RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldListRootStoreWithServiceSharedSecret() throws Exception
    {
        SID sid = SID.rootSID(user);

        givenSecret("polaris", deploymentSecret)
        .expect()
                .statusCode(200)
                .body("id", equalTo(sid.toStringFormal()))
                .body("members.email", hasItem(user.getString()))
                .body("members.permissions", hasItem(hasItems("MANAGE", "WRITE")))
                .body("pending", emptyIterable())
        .when().log().everything()
                .get(RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldListRootStoreWithDelegatedSharedSecret() throws Exception
    {
        SID sid = SID.rootSID(user);

        givenSecret("polaris", deploymentSecret, DID.generate(), user)
        .expect()
                .statusCode(200)
                .body("id", equalTo(sid.toStringFormal()))
                .body("members.email", hasItem(user.getString()))
                .body("members.permissions", hasItem(hasItems("MANAGE", "WRITE")))
                .body("pending", emptyIterable())
        .when()
                .get(RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldListRootStoreWithCert() throws Exception
    {
        SID sid = SID.rootSID(user);
        DID did = DID.generate();
        Long serial = (long)new Random().nextInt(1000);

        sqlTrans.begin();
        inj.getInstance(CertificateDatabase.class)
                .insertCertificate(serial, did, new Date());
        sqlTrans.commit();

        givenCert(did, user, serial)
        .expect()
                .statusCode(200)
                .body("id", equalTo(sid.toStringFormal()))
                .body("members.email", hasItem(user.getString()))
                .body("members.permissions", hasItem(hasItems("MANAGE", "WRITE")))
                .body("pending", emptyIterable())
        .when()
                .get(RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldListRootStoreImplicit() throws Exception
    {
        SID sid = SID.rootSID(user);

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(sid.toStringFormal()))
                .body("members.email", hasItem(user.getString()))
                .body("members.permissions", hasItem(hasItems("MANAGE", "WRITE")))
                .body("pending", emptyIterable())
        .when()
                .get(RESOURCE, "root");
    }

    @Test
    public void shouldListRootStoreWhenGivenUserID() throws Exception
    {
        SID sid = SID.rootSID(user);

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(sid.toStringFormal()))
                .body("members.email", hasItem(user.getString()))
                .body("members.permissions", hasItem(hasItems("MANAGE", "WRITE")))
                .body("pending", emptyIterable())
        .when()
                .get(RESOURCE, user.getString());
    }

    @Test
    public void shouldGetSharedFolder() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(sid.toStringFormal()))
                .body("name", equalTo("Test"))
                .body("members.email", hasItem(user.getString()))
                .body("members.permissions", hasItem(hasItems("MANAGE", "WRITE")))
                .body("pending", emptyIterable())
        .when()
                .get(RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenNonMemberTriesToGetSharedFolder() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .get(RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldListMembers() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        addUser(sid, other, Permissions.EDITOR);

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("email", hasItems(user.getString(), other.getString()))
                .body("permissions", hasItems(hasItems("WRITE", "MANAGE"), hasItems("WRITE")))
        .when()
                .get(RESOURCE + "/members", sid.toStringFormal());
    }

    @Test
    public void shouldListMembersWithEtagMismatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        addUser(sid, other, Permissions.EDITOR);

        givenReadAccess()
                .header(Names.IF_NONE_MATCH, "totallynotavalidetag")
        .expect()
                .statusCode(200)
                .body("email", hasItems(user.getString(), other.getString()))
                .body("permissions", hasItems(hasItems("WRITE", "MANAGE"), hasItems("WRITE")))
                .header(Names.ETAG, membersEtag(user))
        .when()
                .get(RESOURCE + "/members", sid.toStringFormal());
    }

    @Test
    public void shouldReturn304WhenListMembersWithEtagMatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        addUser(sid, other, Permissions.EDITOR);

        givenReadAccess()
                .header(Names.IF_NONE_MATCH, membersEtag(user))
        .expect()
                .statusCode(304)
                .header(Names.ETAG, membersEtag(user))
        .when()
                .get(RESOURCE + "/members", sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenNonMemberTriesToListMembers() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .get(RESOURCE + "/members", sid.toStringFormal());
    }

    @Test
    public void shouldGetMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("email", equalTo(user.getString()))
                .body("permissions", hasItems("WRITE", "MANAGE"))
        .when()
                .get(RESOURCE + "/members/{email}", sid.toStringFormal(), user.getString());
    }

    @Test
    public void shouldGetMemberWithEtagMismatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenReadAccess()
                .header(Names.IF_NONE_MATCH, "totallynotavalidetag")
        .expect()
                .statusCode(200)
                .body("email", equalTo(user.getString()))
                .body("permissions", hasItems("WRITE", "MANAGE"))
                .header(Names.ETAG, membersEtag(user))
        .when()
                .get(RESOURCE + "/members/{email}", sid.toStringFormal(), user.getString());
    }

    @Test
    public void shouldReturn304WhenGetMemberWithEtagMatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenReadAccess()
                .header(Names.IF_NONE_MATCH, membersEtag(user))
        .expect()
                .statusCode(304)
                .header(Names.ETAG, membersEtag(user))
        .when()
                .get(RESOURCE + "/members/{email}", sid.toStringFormal(), user.getString());
    }

    @Test
    public void shouldReturn404WhenNonMemberTriesToGetMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .get(RESOURCE + "/members/{email}", sid.toStringFormal(), user.getString());
    }

    @Test
    public void shouldUpdateMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenWriteAccess()
                .contentType(ContentType.JSON)
                .content(new Member(null, null, null, new String[]{"MANAGE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(200)
                .body("email", equalTo(user.getString()))
                .body("permissions", hasItems("MANAGE"))
        .when().log().everything()
                .put(RESOURCE + "/members/{email}", sid.toStringFormal(), user.getString());
    }

    @Test
    public void shouldUpdateMemberWithEtagMatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenWriteAccess()
                .header(Names.IF_MATCH, membersEtag(user))
                .contentType(ContentType.JSON)
                .content(new Member(null, null, null, new String[]{"MANAGE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(200)
                .body("email", equalTo(user.getString()))
                .body("permissions", hasItems("MANAGE"))
                .header(Names.ETAG, not(membersEtag(user)))
        .when().log().everything()
                .put(RESOURCE + "/members/{email}", sid.toStringFormal(), user.getString());
    }

    @Test
    public void shouldReturn412WhenUpdateMemberWithEtagMismatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenWriteAccess()
                .header(Names.IF_MATCH, "\"deadbeef\"")
                .contentType(ContentType.JSON)
                .content(new Member(null, null, null, new String[]{"MANAGE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(412)
                .header(Names.ETAG, membersEtag(user))
        .when().log().everything()
                .put(RESOURCE + "/members/{email}", sid.toStringFormal(), user.getString());
    }

    @Test
    public void shouldReturn400WhenTryingToRevokeLastOwner() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenWriteAccess()
                .contentType(ContentType.JSON)
                .content(new Member(null, null, null, new String[]{"WRITE"}), ObjectMapperType.GSON)
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
                .body("message", equalTo("There must be at least one owner per shared folder"))
        .when().log().everything()
                .put(RESOURCE + "/members/{email}", sid.toStringFormal(), user.getString());
    }

    @Test
    public void shouldReturn404WhenNonMemberTriesToUpdateMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenOtherAccess()
                .contentType(ContentType.JSON)
                .content(new Member(null, null, null, new String[]{"WRITE"}), ObjectMapperType.GSON)
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .put(RESOURCE + "/members/{email}", sid.toStringFormal(), user.getString());
    }

    @Test
    public void shouldReturn403WhenEditorTriesToUpdateMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        addUser(sid, other, Permissions.EDITOR);

        givenOtherAccess()
                .contentType(ContentType.JSON)
                .content(new Member(null, null, null, new String[]{"MANAGE"}),
                        ObjectMapperType.GSON)
                .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when().log().everything()
                .put(RESOURCE + "/members/{email}", sid.toStringFormal(), user.getString());
    }

    @Test
    public void shouldAllowMemberToLeave() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        addUser(sid, other, Permissions.EDITOR);

        givenOtherAccess()
        .expect()
                .statusCode(204)
        .when().log().everything()
                .delete(RESOURCE + "/members/{email}", sid.toStringFormal(), other.getString());
    }

    @Test
    public void shouldAllowOwnerToDeleteMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        addUser(sid, other, Permissions.EDITOR);

        givenWriteAccess()
        .expect()
                .statusCode(204)
        .when().log().everything()
                .delete(RESOURCE + "/members/{email}", sid.toStringFormal(), other.getString());
    }

    @Test
    public void shouldDeleteMemberWithEtagMatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        addUser(sid, other, Permissions.EDITOR);

        givenWriteAccess()
                .header(Names.IF_MATCH, membersEtag(other))
        .expect()
                .statusCode(204)
        .when().log().everything()
                .delete(RESOURCE + "/members/{email}", sid.toStringFormal(), other.getString());
    }

    @Test
    public void shouldReturn412WhenDeleteMemberWithEtagMismatch() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        addUser(sid, other, Permissions.EDITOR);

        givenWriteAccess()
                .header(Names.IF_MATCH, "\"deadbeef\"")
        .expect()
                .statusCode(412)
                .header(Names.ETAG, membersEtag(other))
        .when().log().everything()
                .delete(RESOURCE + "/members/{email}", sid.toStringFormal(), other.getString());
    }

    @Test
    public void shouldReturn403WhenEditorTriesToDeleteMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        addUser(sid, other, Permissions.EDITOR);

        givenOtherAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when().log().everything()
                .delete(RESOURCE + "/members/{email}", sid.toStringFormal(), user.getString());
    }

    @Test
    public void shouldReturn404WhenNonMemberTriesToDeleteMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .delete(RESOURCE + "/members/{email}", sid.toStringFormal(), user.getString());
    }

    @Test
    public void shouldListPendingMembers() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, other, Permissions.EDITOR);

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("email", hasItems(other.getString()))
                .body("first_name", hasItems("Other"))
                .body("last_name", hasItems("Foo"))
                .body("invited_by", hasItems(user.getString()))
                .body("permissions", hasItems(hasItems("WRITE")))
        .when().log().everything()
                .get(RESOURCE + "/pending", sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenNonMemberListPendingMembers() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .get(RESOURCE + "/pending", sid.toStringFormal());
    }

    @Test
    public void shouldGetPendingMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, other, Permissions.EDITOR);

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("email", equalTo(other.getString()))
                .body("first_name", equalTo("Other"))
                .body("last_name", equalTo("Foo"))
                .body("invited_by", equalTo(user.getString()))
                .body("permissions", hasItems("WRITE"))
        .when().log().everything()
                .get(RESOURCE + "/pending/{email}", sid.toStringFormal(), other.getString());
    }

    @Test
    public void shouldReturn404WhenGetNonExistingPendingMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenReadAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such pending member"))
        .when().log().everything()
                .get(RESOURCE + "/pending/{email}", sid.toStringFormal(), other.getString());
    }

    @Test
    public void shouldReturn404WhenNonMemberGetPendingMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, UserID.fromInternal("a@b.c"), Permissions.EDITOR);

        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .get(RESOURCE + "/pending/{email}", sid.toStringFormal(), "a@b.c");
    }

    @Test
    public void shouldAddPendingMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenWriteAccess()
                .contentType(ContentType.JSON)
                .body(new PendingMember(other.getString(), new String[] {"WRITE"}, "Join us"),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(201)
                .header(Names.LOCATION,
                        "https://localhost:" + sparta.getListeningPort() + BASE_RESOURCE +
                                sid.toStringFormal() + "/pending/" + other.getString())
                .body("email", equalTo(other.getString()))
                .body("first_name", equalTo("Other"))
                .body("last_name", equalTo("Foo"))
                .body("invited_by", equalTo(user.getString()))
                .body("permissions", hasItems("WRITE"))
        .when().log().everything()
                .post(RESOURCE + "/pending", sid.toStringFormal());
    }

    @Test
    public void shouldUpdatePermWhenAddPendingMemberTwice() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, UserID.fromInternal("a@b.c"), Permissions.EDITOR);

        givenWriteAccess()
                .contentType(ContentType.JSON)
                .body(new PendingMember(other.getString(), new String[]{"WRITE", "MANAGE"},
                        "Join us"), ObjectMapperType.GSON)
        .expect()
                .statusCode(201)
                .header(Names.LOCATION,
                        "https://localhost:" + sparta.getListeningPort() + BASE_RESOURCE +
                                sid.toStringFormal() + "/pending/" + other.getString())
                .body("email", equalTo(other.getString()))
                .body("invited_by", equalTo(user.getString()))
                .body("permissions", hasItems("WRITE", "MANAGE"))
        .when().log().everything()
                .post(RESOURCE + "/pending", sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenNonMemberAddPendingMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenOtherAccess()
                .contentType(ContentType.JSON)
                .body(new PendingMember("a@b.c", new String[]{"WRITE"}, "Join us"),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .post(RESOURCE + "/pending", sid.toStringFormal());
    }

    @Test
    public void shouldReturn403WhenEditorAddPendingMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        addUser(sid, other, Permissions.EDITOR);

        givenOtherAccess()
                .contentType(ContentType.JSON)
                .body(new PendingMember("a@b.c", new String[]{"WRITE"},
                        "Join us"), ObjectMapperType.GSON)
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
                .body("message", equalTo("Not allowed to manage users for this shared folder"))
        .when().log().everything()
                .post(RESOURCE + "/pending", sid.toStringFormal());
    }

    @Test
    public void shouldRemovePendingMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, other, Permissions.EDITOR);

        givenWriteAccess()
        .expect()
                .statusCode(204)
        .when().log().everything()
                .delete(RESOURCE + "/pending/{email}", sid.toStringFormal(), other.getString());
    }

    @Test
    public void shouldReturn404WhenRemoveNonExistingPendingMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenWriteAccess()
        .expect()
                .statusCode(404)
        .when().log().everything()
                .delete(RESOURCE + "/pending/{email}", sid.toStringFormal(), other.getString());
    }

    @Test
    public void shouldReturn403WhenNonOwnerRemovePendingMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        addUser(sid, other, Permissions.EDITOR);
        invite(user, sid, UserID.fromInternal("a@b.c"), Permissions.EDITOR);

        givenOtherAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
                .body("message", equalTo("Not allowed to manage users for this shared folder"))
        .when().log().everything()
                .delete(RESOURCE + "/pending/{email}", sid.toStringFormal(), "a@b.c");
    }

    @Test
    public void shouldReturn404WhenNonMemberRemovePendingMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, UserID.fromInternal("a@b.c"), Permissions.EDITOR);

        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .delete(RESOURCE + "/pending/{email}", sid.toStringFormal(), "a@b.c");
    }

    @Test
    public void shouldAddMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenAdminAccess()
                .contentType(ContentType.JSON)
                .body(new Member(other.getString(), null, null, new String[] {"WRITE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(201)
                .header(Names.LOCATION,
                        "https://localhost:" + sparta.getListeningPort() + BASE_RESOURCE +
                                sid.toStringFormal() + "/members/" + other.getString())
                .body("email", equalTo(other.getString()))
                .body("first_name", equalTo("Other"))
                .body("last_name", equalTo("Foo"))
                .body("permissions", hasItems("WRITE"))
        .when().log().everything()
                .post(RESOURCE + "/members", sid.toStringFormal());
    }

    @Test
    public void shouldUpdatePermWhenAddMemberAlreadyInvited() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        invite(user, sid, other, Permissions.EDITOR);

        givenAdminAccess()
                .contentType(ContentType.JSON)
                .body(new Member(other.getString(), null, null, new String[]{"WRITE", "MANAGE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(201)
                .header(Names.LOCATION,
                        "https://localhost:" + sparta.getListeningPort() + BASE_RESOURCE +
                                sid.toStringFormal() + "/members/" + other.getString())
                .body("email", equalTo(other.getString()))
                .body("first_name", equalTo("Other"))
                .body("last_name", equalTo("Foo"))
                .body("permissions", hasItems("WRITE", "MANAGE"))
        .when().log().everything()
                .post(RESOURCE + "/members", sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenUserDoesNotExist() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenWriteAccess()
                .contentType(ContentType.JSON)
                .body(new Member("a@b.c", null, null, new String[]{"WRITE", "MANAGE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such user"))
        .when().log().everything()
                .post(RESOURCE + "/members", sid.toStringFormal());
    }

    @Test
    public void shouldReturn409WhenMemberAlreadyExists() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        addUser(sid, other, Permissions.EDITOR);

        givenAdminAccess()
                .contentType(ContentType.JSON)
                .body(new Member(other.getString(), null, null, new String[]{"WRITE", "MANAGE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(409)
                .body("type", equalTo("CONFLICT"))
                .body("message", equalTo("Member already exists"))
        .when().log().everything()
                .post(RESOURCE + "/members", sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenNonMemberAddMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenOtherAccess()
                .contentType(ContentType.JSON)
                .body(new Member(other.getString(), null, null, new String[]{"WRITE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("No such shared folder"))
        .when().log().everything()
                .post(RESOURCE + "/members", sid.toStringFormal());
    }

    @Test
    public void shouldReturn403WhenEditorAddMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());
        addUser(sid, other, Permissions.EDITOR);

        givenOtherAccess()
                .contentType(ContentType.JSON)
                .body(new Member(admin.getString(), null, null, new String[]{"WRITE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
                .body("message", equalTo("Not allowed to manage users for this shared folder"))
        .when().log().everything()
                .post(RESOURCE + "/members", sid.toStringFormal());
    }

    @Test
    public void shouldReturn403WhenNonAdminAddMember() throws Exception
    {
        SID sid = mkShare("Test", user.getString());

        givenWriteAccess()
                .contentType(ContentType.JSON)
                .body(new Member(other.getString(), null, null, new String[]{"WRITE"}),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
                .body("message", equalTo("Not allowed to bypass invitation process"))
        .when().log().everything()
                .post(RESOURCE + "/members", sid.toStringFormal());
    }

    @Test
    public void shouldCreateSharedFolder() throws Exception
    {
        givenWriteAccess()
                .contentType(ContentType.JSON)
                .body(new SharedFolder(null, "Shareme", null, null, false),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(201)
                .header(Names.LOCATION,
                        startsWith("https://localhost:" + sparta.getListeningPort() + BASE_RESOURCE))
                .body("name", equalTo("Shareme"))
                .body("is_external", equalTo(false))
                .body("members.email", hasItem(user.getString()))
                .body("pending", emptyIterable())
        .when().log().everything()
                .post(BASE_RESOURCE);
    }

    @Test
    public void shouldCreateExternalSharedFolder() throws Exception
    {
        givenWriteAccess()
                .contentType(ContentType.JSON)
                .body(new SharedFolder(null, "External", null, null, true),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(201)
                .header(Names.LOCATION,
                        startsWith("https://localhost:" + sparta.getListeningPort() + BASE_RESOURCE))
                .body("name", equalTo("External"))
                .body("is_external", equalTo(true))
                .body("members.email", hasItem(user.getString()))
                .body("pending", emptyIterable())
        .when().log().everything()
                .post(BASE_RESOURCE);
    }
}
