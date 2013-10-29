package com.aerofs.bifrost.server;

import com.jayway.restassured.response.Response;
import org.junit.Test;

import java.util.Map;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 */
public class TestAuthorizationRequest extends BifrostTest
{
    @Test
    public void testShouldFailWithoutParams()
    {
        expect().statusCode(400)
        .when().get(AUTH_URL);
    }

    @Test
    public void testShouldFailWrongResponseType()
    {
        given()
                .param("client_id", CLIENTID)
                .param("redirect_uri", CLIENTREDIRECT)
                .param("response_type", "bla bloo blee bla bla")
        .expect()
                .statusCode(302)
                .and()
                .response().header("Location", containsString("error="))
        .when().get(AUTH_URL);
    }

    @Test
    public void testShouldReturnAuthState()
    {
        String authState = oauthBegin();
        assertNotNull(authState);
        assertTrue(authState.length() > 0);
    }

    @Test
    public void testShouldReturnCodeForCredential()
    {
        String authState = oauthBegin();
        Response response = given()
                .formParam("j_username", "foo")
                .formParam("j_password", "bar")
                .formParam("AUTH_STATE", authState)
            .post(AUTH_URL);

        assertEquals(response.getStatusCode(), 303);
        Map<String, String> q = extractQuery(response.getHeader("Location"));
        assertTrue(q.containsKey("code"));
        assertTrue(q.get("code").length() > 0);
    }

    @Test
    public void testShouldReturnState()
    {
        String authState = oauthBegin();
        Response response = given()
                .formParam("j_username", "foo")
                .formParam("j_password", "bar")
                .formParam("AUTH_STATE", authState)
                .post(AUTH_URL);

        assertEquals(response.getStatusCode(), 303);
        Map<String, String> q = extractQuery(response.getHeader("Location"));
        assertTrue(q.containsKey("state"));
        assertEquals(q.get("state"), "client_state");

        assertTrue(q.containsKey("code"));
        assertTrue(q.get("code").length() > 0);
    }
}
