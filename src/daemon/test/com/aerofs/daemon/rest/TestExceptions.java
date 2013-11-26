/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.id.SIndex;
import org.junit.Test;

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

        givenAcces()
        .expect()
                .statusCode(500)
                .body("type", equalTo("INTERNAL_ERROR"))
                .body("message", equalTo("Internal error while servicing request"))
        .when().log().everything().get("/v0.9/children");
    }
}
