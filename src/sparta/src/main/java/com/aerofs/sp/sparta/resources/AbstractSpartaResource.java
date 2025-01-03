/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.ids.SID;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.aerofs.rest.auth.IAuthToken;
import com.aerofs.oauth.Scope;
import com.aerofs.sp.server.lib.user.User;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import java.sql.SQLException;

public class AbstractSpartaResource
{
    protected static void requirePermission(Scope scope, IAuthToken token)
    {
        if (!token.hasPermission(scope)) {
            throw new WebApplicationException(tokenScope().build());
        }
    }

    static String aclEtag(User user) throws SQLException
    {
        return String.format("%08x", user.getACLEpoch());
    }

    protected static void requirePermissionOnFolder(Scope scope, IAuthToken token, SID sid)
    {
        if (!token.hasFolderPermission(scope, sid)) {
            throw new WebApplicationException(tokenScope().build());
        }
    }

    protected static ResponseBuilder tokenScope()
    {
        return Response
                .status(Status.FORBIDDEN)
                .entity(new Error(Type.FORBIDDEN, "Token lacks required scope"));
    }

}
