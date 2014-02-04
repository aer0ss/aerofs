/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.id.SIndex;
import com.jayway.restassured.http.ContentType;
import org.junit.Test;

import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;

public class TestExceptions extends AbstractRestTest
{
    public TestExceptions(boolean useProxy)
    {
        super(useProxy);
    }

    @Test
    public void shouldReturn500OnRuntimeException() throws Exception
    {
        doThrow(new NullPointerException())
        .when(acl).checkThrows_(any(UserID.class), any(SIndex.class), any(Permissions.class));

        givenAccess()
        .expect()
                .statusCode(500)
                .body("type", equalTo("INTERNAL_ERROR"))
                .body("message", equalTo("Internal error while servicing request"))
        .when().log().everything().get("/v0.9/children");
    }

    @Test
    public void shouldReturn40OnIllegalArgumentException() throws Exception
    {
        doThrow(new IllegalArgumentException())
                .when(acl).checkThrows_(any(UserID.class), any(SIndex.class), any(Permissions.class));

        givenAccess()
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
                .body("message", startsWith("Invalid parameter"))
        .when().log().everything().get("/v0.9/children");
    }

    @Test
    public void shouldReturn400WhenPassedInvalidParam() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
                .body("message", equalTo("Invalid parameter: folder_id"))
        .when().log().everything().get("/v0.9/folders/lolwut");
    }

    @Test
    public void shouldReturn400WhenPassedInvalidJSON() throws Exception
    {
        givenAccess()
                .contentType(ContentType.JSON)
                .content("{\"broken\": 42,")
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
                .body("message", equalTo("Invalid JSON input"))
        .when().log().everything().post("/v0.10/folders");
    }
}
