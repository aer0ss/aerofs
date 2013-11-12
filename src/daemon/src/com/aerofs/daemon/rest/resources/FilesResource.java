/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.RestService;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.daemon.rest.event.EIFileInfo;
import com.aerofs.daemon.rest.jersey.RestObjectParam;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Since;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;
import com.sun.jersey.core.header.MatchingEntityTag;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Set;

@Path(RestService.VERSION + "/files/{object}")
@Produces(MediaType.APPLICATION_JSON)
public class FilesResource
{
    private final IIMCExecutor _imce;

    @Inject
    public FilesResource(CoreIMCExecutor imce)
    {
        _imce = imce.imce();
    }

    @Since("0.9")
    @GET
    public Response metadata(@Auth AuthenticatedPrincipal principal,
            @PathParam("object") RestObjectParam object)
    {
        UserID userid = principal.getUserID();
        return new EIFileInfo(_imce, userid, object.get()).execute();
    }

    @Since("0.9")
    @GET
    @Path("/content")
    @Produces({MediaType.APPLICATION_OCTET_STREAM, "multipart/byteranges"})
    public Response content(@Auth AuthenticatedPrincipal principal,
            @PathParam("object") RestObjectParam object,
            @HeaderParam(HttpHeaders.IF_RANGE) String ifRange,
            @HeaderParam(HttpHeaders.RANGE) String range,
            @HeaderParam(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch)
    {
        UserID userid = principal.getUserID();
        EntityTag etIfRange = EntityTagUtil.parse(ifRange);
        Set<MatchingEntityTag> etIfNoneMatch = EntityTagUtil.parseSet(ifNoneMatch);
        return new EIFileContent(_imce, userid, object.get(), etIfRange, range, etIfNoneMatch).execute();
    }
}

