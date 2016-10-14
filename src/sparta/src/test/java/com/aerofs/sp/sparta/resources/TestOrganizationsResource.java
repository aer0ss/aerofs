/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.DID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.jayway.restassured.http.ContentType;
import org.junit.Test;

import static com.jayway.restassured.RestAssured.expect;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

public class TestOrganizationsResource extends AbstractResourceTest
{
    private final String RESOURCE_BASE = "/v1.2/organizations";
    private final String RESOURCE = RESOURCE_BASE + "/2";
    private final String BAD_RESOURCE = RESOURCE_BASE + "/abc123";
    private final String STORAGE_AGENT_RESOURCE= "/v1.4/organizations/{orgid}/storage_agent";

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
        Organization org = factUser.create(admin.getString()).getOrganization();
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
        Organization org = factUser.create(admin.getString()).getOrganization();
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

    @Test
    public void shouldReturnStorageAgentToken() throws Exception
    {
        givenSecret("bunker", deploymentSecret)
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(200)
                .body("token", any(String.class))
        .when().log().everything()
                .post(STORAGE_AGENT_RESOURCE, OrganizationID.PRIVATE_ORGANIZATION.toHexString());
    }

    @Test
    public void shouldReturn401OnNonServiceRequestsForStorageAgent() throws Exception
    {
        givenReadAccess()
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(401)
        .when()
                .post(STORAGE_AGENT_RESOURCE, OrganizationID.PRIVATE_ORGANIZATION.toHexString());

        givenAdminAccess()
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(401)
        .when()
                .post(STORAGE_AGENT_RESOURCE, OrganizationID.PRIVATE_ORGANIZATION.toHexString());

        givenSecret("bunker", deploymentSecret, user, DID.generate())
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(401)
        .when()
                .post(STORAGE_AGENT_RESOURCE, OrganizationID.PRIVATE_ORGANIZATION.toHexString());
    }
}

