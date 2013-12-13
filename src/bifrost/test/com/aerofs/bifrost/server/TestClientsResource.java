/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.bifrost.oaaas.model.Client;
import com.jayway.restassured.response.Response;
import static com.jayway.restassured.path.json.JsonPath.from;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 */
public class TestClientsResource extends BifrostTest
{
    @Test
    public void shouldRejectPostWithoutRequiredParameters() throws Exception
    {
        // this request is missing resource_server_key
        expect()
                .statusCode(400)
        .given()
                .formParam("client_name", "new-app-name")
                .formParam("redirect_uri", "aerofs://redirect")
                .post(CLIENTS_URL);

        // this request is missing client_name
        expect()
                .statusCode(400)
        .given()
                .formParam("resource_server_key", RESOURCEKEY)
                .formParam("redirect_uri", "aerofs://redirect")
                .post(CLIENTS_URL);

        // this request is missing redirect_uri
        expect()
                .statusCode(400)
                .given()
                .formParam("client_name", "new-app-name")
                .formParam("resource_server_key", RESOURCEKEY)
                .post(CLIENTS_URL);
    }

    @Test
    public void shouldRejectPostWithBadResourceServerKey() throws Exception
    {
        expect()
                .statusCode(400)
        .given()
                .formParam("client_name", "new-app-name")
                .formParam("resource_server_key", "this-is-not-a-resource-server")
                .post(CLIENTS_URL);
    }

    @Test
    public void shouldCreateNewClientOnPostAndReturnProperJsonDoc() throws Exception
    {
        String client_name = "new-app-name";

        Response response = given()
                .formParam("client_name", client_name)
                .formParam("resource_server_key", RESOURCEKEY)
                .formParam("redirect_uri", "aerofs://redirect")
                .post(CLIENTS_URL);

        assertEquals(200, response.getStatusCode());

        String jsonResponse = response.asString();
        String r_client_id = from(jsonResponse).get("client_id");
        String r_secret = from(jsonResponse).get("secret");

        assertNotNull(r_client_id);
        assertNotNull(r_secret);

        Client client = _clientRepository.findByClientId(r_client_id);

        assertEquals(r_secret, client.getSecret());
        assertEquals(client_name, client.getName());
    }

    @Test
    public void shouldListClientsOnGet() throws Exception
    {
        Response response = given().get(CLIENTS_URL);

        assertEquals(200, response.getStatusCode());

        /**
         * The json response should have a field "clients" which is an array of objects with the
         * following properties:
         * - client_id
         * - resource_server_key
         * - client_name
         * - secret
         * - redirect_uri
         * - description (optional)
         * - contact_email (optional)
         * - contact_name (optional)
         */
        String json = response.asString();

        List<HashMap<String,String>> clients = from(json).get("clients");
        assertNotNull(clients);
        assertTrue(clients.size() >= 1);

        for (HashMap<String,String> client : clients) {
            assertNotNull(client.get("client_id"));
            assertNotNull(client.get("resource_server_key"));
            assertNotNull(client.get("client_name"));
            assertNotNull(client.get("secret"));
            assertNotNull(client.get("redirect_uri"));

            if (client.get("client_id").equals(CLIENTID)) {
                assertEquals(CLIENTNAME, client.get("client_name"));
                assertEquals(RESOURCEKEY, client.get("resource_server_key"));
            }
        }
    }

    @Test
    public void shouldGet404OnClientInfoWithBadId() throws Exception
    {
        expect()
                .statusCode(404)
        .given()
                .get(CLIENTS_URL + "/" + "nothing-to-see-here-folks");
    }

    @Test
    public void shouldGetClientInfoOnGetById() throws Exception
    {
        Response response = given().get(CLIENTS_URL + "/" + CLIENTID);
        assertEquals(200, response.getStatusCode());

        String json = response.asString();
        assertEquals(CLIENTID, from(json).get("client_id"));
        assertEquals(CLIENTSECRET, from(json).get("secret"));
        assertEquals(CLIENTREDIRECT, from(json).get("redirect_uri"));
        assertEquals(RESOURCEKEY, from(json).get("resource_server_key"));
    }

    @Test
    public void shouldRejectDeleteToNonexistentClient() throws Exception
    {
        expect()
                .statusCode(400)
        .given()
                .delete(CLIENTS_URL + "/i-am-not-a-client");
    }

    @Test
    public void shouldDeleteClient() throws Exception
    {
        // create a client first, and make sure it's in the db
        Response response = given()
                .formParam("client_name", "new-client-w00t")
                .formParam("resource_server_key", RESOURCEKEY)
                .formParam("redirect_uri", "aerofs://redirect")
                .post(CLIENTS_URL);

        assertEquals(200, response.getStatusCode());

        String jsonResponse = response.asString();
        String r_client_id = from(jsonResponse).get("client_id");

        assertNotNull(_clientRepository.findByClientId(r_client_id));

        // then delete the client, and make sure it's not in the db
        expect()
                .statusCode(200)
        .given()
                .delete(CLIENTS_URL + "/" + r_client_id);

        assertNull(_clientRepository.findByClientId(r_client_id));
    }
}
