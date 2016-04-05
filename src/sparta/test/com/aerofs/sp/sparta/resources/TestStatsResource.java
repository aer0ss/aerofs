/*
 * Copyright (c) Air Computing Inc., 2016.
 */

package com.aerofs.sp.sparta.resources;

import org.junit.Test;

import static org.hamcrest.Matchers.*;

public class TestStatsResource extends AbstractResourceTest
{
    private final String BASE_RESOURCE = "/v1.4/stats";

    @Test
    public void count_shouldReturn401ForInvalidAuthToken() throws Exception
    {
        givenAccess("thisisnotavalidtoken")
        .expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when().log().everything()
                .get(BASE_RESOURCE + "/users");
    }

    @Test
    public void shouldReturn403WhenNonAdminAccessesStats()
    {
        givenWriteAccess()
        .expect()
                .statusCode(403)
        .when().log().everything()
                .get(BASE_RESOURCE + "/users");

        givenWriteAccess()
        .expect()
                .statusCode(403)
        .when().log().everything()
                .get(BASE_RESOURCE + "/devices");

        givenWriteAccess()
        .expect()
                .statusCode(403)
        .when().log().everything()
                .get(BASE_RESOURCE + "/groups");

        givenWriteAccess()
        .expect()
                .statusCode(403)
        .when().log().everything()
                .get(BASE_RESOURCE + "/shares");
    }

    @Test
    public void shouldReturn200WhenAdminAccessesStats()
    {
        givenAdminAccess()
                .expect()
                .statusCode(200)
                .when().log().everything()
                .get(BASE_RESOURCE + "/users");

        givenAdminAccess()
                .expect()
                .statusCode(200)
                .when().log().everything()
                .get(BASE_RESOURCE + "/devices");

        givenAdminAccess()
                .expect()
                .statusCode(200)
                .when().log().everything()
                .get(BASE_RESOURCE + "/groups");

        givenAdminAccess()
                .expect()
                .statusCode(200)
                .when().log().everything()
                .get(BASE_RESOURCE + "/shares");
    }
}