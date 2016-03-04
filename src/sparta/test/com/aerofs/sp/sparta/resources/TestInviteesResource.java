package com.aerofs.sp.sparta.resources;

import static com.jayway.restassured.RestAssured.expect;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.Test;

import com.aerofs.rest.api.Invitee;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.internal.mapper.ObjectMapperType;

public class TestInviteesResource extends AbstractResourceTest {
    private final String RESOURCE_BASE = "/v1.3/invitees";
    private final String RESOURCE = RESOURCE_BASE + "/{email}";

    @Test
    public void shouldReturn401WhenTokenMissing() throws Exception {
        expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when().log().everything()
                .get(RESOURCE, user.getString());
    }

    @Test
    public void shouldReturn404ForNonExistingUser() throws Exception {
        givenAdminAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().log().everything()
                .get(RESOURCE, "totallynotavaliduserid");
    }

    @Test
    public void shouldGetExisting() throws Exception {
        givenAdminAccess()
        .expect()
                .statusCode(200)
                .body("email_to", equalTo(user.getString()))
        .when().log().everything()
                .get(RESOURCE, user.getString());
    }

    @Test
    public void shouldReturn403WhenGetByOther() throws Exception {
        givenOtherAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when().log().everything()
                .get(RESOURCE, user.getString());
    }

    @Test
    public void shouldCreateNewInvitee() {
        givenAdminAccess()
                .content(new Invitee("new@bar.baz", ":2", null), ObjectMapperType.GSON)
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(201)
                .body("email_to", equalTo("new@bar.baz")).body("signup_code", notNullValue())
        .when().log().everything()
                .post(RESOURCE_BASE);
    }

    @Test
    public void shouldReturn409ForCreateExistingInvitee() {
        givenAdminAccess()
                .content(new Invitee(user.getString(), ":2", null), ObjectMapperType.GSON)
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(409)
                .body("type", equalTo("CONFLICT"))
        .when().log().everything()
                .post(RESOURCE_BASE);
    }

    @Test
    public void shouldDeleteExistingInvitee() {
        givenAdminAccess()
        .expect()
                .statusCode(204)
        .when().log().everything()
                .delete(RESOURCE, user.getString());
    }

    @Test
    public void should403WithNewUserInviteRestrictions() {

        prop.setProperty("signup_restriction", "ADMIN_INVITED");
        givenOtherAccess()
                .content(new Invitee("new@bar.baz", "other@bar.baz", null), ObjectMapperType.GSON)
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(403)
        .when()
                .post(RESOURCE_BASE);
        prop.setProperty("signup_restriction", "");
    }
}
