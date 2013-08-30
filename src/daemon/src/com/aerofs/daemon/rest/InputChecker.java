package com.aerofs.daemon.rest;

import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common.PBException.Type;
import com.aerofs.rest.api.Error;
import com.google.inject.Inject;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class InputChecker
{
    @Inject
    public InputChecker()
    {
    }

    public UserID user(String id)
    {
        // TODO: parse @me token when auth enabled
        try {
            return UserID.fromExternal(id);
        } catch (Exception e) {
            throw new WebApplicationException(Response.status(Status.BAD_REQUEST)
                    .entity(new Error(Type.INVALID_EMAIL_ADDRESS.name(), "no such user")).build());
        }
    }

    public RestObject object(String id, UserID userid)
    {
        try {
            return RestObject.fromStringFormal(id, userid);
        } catch (Exception e) {
            throw new WebApplicationException(Response.status(Status.BAD_REQUEST)
                    .entity(new Error(Type.NOT_FOUND.name(), "no such object")).build());
        }
    }
}
