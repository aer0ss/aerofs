package com.aerofs.trifrost.resources;

import com.aerofs.trifrost.ServerConfiguration;
import com.aerofs.trifrost.TrifrostTestResource;
import com.aerofs.trifrost.Utilities;
import com.aerofs.trifrost.api.Invitation;
import com.aerofs.trifrost.api.VerifiedDevice;
import com.jayway.restassured.RestAssured;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.jayway.restassured.RestAssured.given;

public final class TestInviteResource {
    static { RestAssured.config = Utilities.newRestAssuredConfig(); }
    @Rule public RuleChain profileServer = TrifrostTestResource.toRuleChain();


    @Test
    public void invitedUserShouldActivate() throws Exception {
        VerifiedDevice dev = ProfileUtils.createUser("activator@test.foo");

        given().header(ProfileUtils.authHeader(dev))
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .post(ServerConfiguration.inviteUrl("activateme@test.foo"));

        VerifiedDevice activated = ProfileUtils.createUser("activateme@test.foo");
        given().header(ProfileUtils.authHeader(activated))
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .post(ServerConfiguration.inviteUrl("third_party@testfoo"))
                .then().statusCode(Response.Status.OK.getStatusCode());

    }

    @Test
    public void regularInviteShouldSucceed() throws Exception {
        VerifiedDevice dev = ProfileUtils.createUser("requestVerif@test.foo");

        given().header(ProfileUtils.authHeader(dev))
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .post(ServerConfiguration.inviteUrl("invitee@b.c"))
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given() .header(ProfileUtils.authHeader(dev))
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .post(ServerConfiguration.inviteUrl("invitee@b.c"))
                .then()
                .statusCode(Response.Status.OK.getStatusCode());
    }

    @Test
    public void inviteShouldRequireAuth() throws Exception {
        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(new Invitation("doesnt@matter.whatever"))
                .then()
                .statusCode(Response.Status.FORBIDDEN.getStatusCode());
    }

}