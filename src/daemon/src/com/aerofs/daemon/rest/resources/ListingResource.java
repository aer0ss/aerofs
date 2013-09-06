/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.RestObject;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.daemon.rest.event.EIListRoots;
import com.aerofs.daemon.rest.jersey.RestObjectParam;
import com.aerofs.daemon.rest.jersey.UserIDParam;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.google.inject.Inject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/0/list")
@Produces(MediaType.APPLICATION_JSON)
public class ListingResource
{
    private final IIMCExecutor _imce;
    private final CfgLocalUser _localUser;

    @Inject
    public ListingResource(CoreIMCExecutor imce, CfgLocalUser localUser)
    {
        _imce = imce.imce();
        _localUser = localUser;
    }

    @GET
    @Path("/roots/{userid}")
    public Response listRoots(@PathParam("userid") UserIDParam userid)
    {
        return new EIListRoots(_imce, userid.get()).execute();
    }

    @GET
    @Path("/root/{userid}")
    public Response listUserRoot(@PathParam("userid") UserIDParam user)
    {
        UserID userid = _localUser.get(); // TODO: get from auth info
        RestObject object = new RestObject(SID.rootSID(user.get()), OID.ROOT);
        return new EIListChildren(_imce, userid, object).execute();
    }

    @GET
    @Path("/{object}")
    public Response list(@PathParam("object") RestObjectParam object)
    {
        UserID userid = _localUser.get(); // TODO: get from auth info
        return new EIListChildren(_imce, userid, object.get()).execute();
    }
}
