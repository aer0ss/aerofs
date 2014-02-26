/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.util.AuthToken;
import com.aerofs.rest.util.AuthToken.Scope;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class AbstractResource
{
    @Inject
    protected IIMCExecutor _imce;

    protected static void requirePermissionOnFolder(Scope scope, AuthToken token, SID sid)
    {
        if (!token.hasFolderPermission(scope, sid)) {
            throw new WebApplicationException(Response
                    .status(Status.FORBIDDEN)
                    .entity(new Error(Error.Type.FORBIDDEN, "Token lacks required scope"))
                    .build());
        }
    }

    protected static void requirePermissionOnFolder(Scope scope, AuthToken token, RestObject object)
    {
        requirePermissionOnFolder(scope, token, object.sid);
    }
}
