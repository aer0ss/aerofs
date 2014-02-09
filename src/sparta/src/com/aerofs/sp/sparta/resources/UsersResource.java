/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.lib.user.User;
import com.google.inject.Inject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.SQLException;

@Path(Service.VERSION + "/users")
@Produces(MediaType.APPLICATION_JSON)
public class UsersResource
{
    private final SQLThreadLocalTransaction _sqlTrans;
    private final User.Factory _factUser;

    @Inject
    public UsersResource(SQLThreadLocalTransaction sqlTrans, User.Factory factUser)
    {
        _sqlTrans = sqlTrans;
        _factUser = factUser;
    }

    @Since("1.1")
    @GET
    public Response list() throws SQLException
    {
        _sqlTrans.begin();
        // TODO
        _sqlTrans.commit();

        return Response.ok()
                .entity(new Error(Type.INTERNAL_ERROR, "Not implemented"))
                .build();
    }
}
