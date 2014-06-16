/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.sp.server.lib.organization.Organization;
import org.junit.Test;

import static com.jayway.restassured.RestAssured.expect;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

public class TestOrganizationsResource extends AbstractResourceTest
{
    private final String RESOURCE_BASE = "/v1.2/organizations";
    private final String RESOURCE = RESOURCE_BASE + "/2";
    private final String BAD_RESOURCE = RESOURCE_BASE + "/abc123";

    @Test
    public void shouldReturn401WhenTokenMissing() throws Exception
    {
        expect()
                .statusCode(401)
        .when().log().everything()
                .get(RESOURCE);
    }

    @Test
    public void shouldReturn404ForNonAdminToken() throws Exception
    {
        givenWriteAccess()
        .expect()
                .statusCode(404)
        .when().log().everything()
                .get(RESOURCE);
    }

    @Test
    public void shouldReturn404IfOrgNotFound() throws Exception
    {
        givenAdminAccess()
        .expect()
                .statusCode(404)
        .when().log().everything()
                .get(BAD_RESOURCE);
    }

    @Test
    public void shouldGetQuota() throws Exception
    {
        long quota = 42 * (long)1E9;

        sqlTrans.begin();
        Organization org = factUser._create(admin.getString()).getOrganization();
        String name = org.getName();
        org.setQuotaPerUser(quota);
        sqlTrans.commit();

        givenAdminAccess()
        .expect()
                .statusCode(200)
                .body("name", equalTo(name))
                .body("quota", equalTo(quota))
        .when().log().everything()
                .get(RESOURCE);
    }

    @Test
    public void shouldNotIncludeQuotaWhenNoQuotaSet() throws Exception
    {
        sqlTrans.begin();
        Organization org = factUser._create(admin.getString()).getOrganization();
        String name = org.getName();
        org.setQuotaPerUser(null);
        sqlTrans.commit();

        givenAdminAccess()
        .expect()
                .statusCode(200)
                .body("name", equalTo(name))
                .body("quota", nullValue())
        .when().log().everything()
                .get(RESOURCE);
    }
}

