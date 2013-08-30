/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.InputChecker;
import com.aerofs.daemon.rest.RestObject;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.google.inject.Inject;

import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@javax.ws.rs.Path("/0/users/{user}/list/{object}")
@Produces(MediaType.APPLICATION_JSON)
public class ListingResource
{
    private final IIMCExecutor _imce;
    private final InputChecker _inputChecker;

    @Inject
    public ListingResource(CoreIMCExecutor imce, InputChecker inputChecker)
    {
        _imce = imce.imce();
        _inputChecker = inputChecker;
    }

    @GET
    public Response list(@PathParam("user") String user, @PathParam("object") String object)
    {
        UserID userid = _inputChecker.user(user);
        RestObject obj = _inputChecker.object(object, userid);
        return new EIListChildren(_imce, userid, obj).execute();
    }
}
