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
import com.aerofs.lib.cfg.CfgLocalUser;
import com.google.common.net.HttpHeaders;
import com.sun.jersey.core.header.MatchingEntityTag;
import com.sun.jersey.core.header.reader.HttpHeaderReader;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.text.ParseException;
import java.util.Set;

@Path(RestService.VERSION + "/files/{object}")
@Produces(MediaType.APPLICATION_JSON)
public class FilesResource
{
    private final IIMCExecutor _imce;
    private final CfgLocalUser _localUser;

    @Inject
    public FilesResource(CoreIMCExecutor imce, CfgLocalUser localUser)
    {
        _imce = imce.imce();
        _localUser = localUser;
    }

    @GET
    public Response metadata(@PathParam("object") RestObjectParam object)
    {
        UserID userid = _localUser.get(); // TODO: get from auth
        return new EIFileInfo(_imce, userid, object.get()).execute();
    }

    @GET
    @Path("/content")
    @Produces({MediaType.APPLICATION_OCTET_STREAM, "multipart/byteranges"})
    public Response content(@PathParam("object") RestObjectParam object,
            @HeaderParam(HttpHeaders.IF_RANGE) String ifRange,
            @HeaderParam(HttpHeaders.RANGE) String range,
            @HeaderParam(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch)
    {
        UserID userid = _localUser.get(); // TODO: get from auth
        EntityTag etIfRange = parseEtag(ifRange);
        Set<MatchingEntityTag> etIfNoneMatch = parseEtags(ifNoneMatch);
        return new EIFileContent(_imce, userid, object.get(), etIfRange, range, etIfNoneMatch).execute();
    }

    private static @Nullable EntityTag parseEtag(String str)
    {
        if (str == null || str.isEmpty()) return null;
        try {
            return EntityTag.valueOf(str);
        } catch (IllegalArgumentException e) {
            // fake entity tag that will never match
            // Returning null would cause Range headers to always be honored when accompanied
            // by invalid If-Range which would be unsafe. The "always mismatch" entity ensures
            // that any Range header will be ignored.
            return new EntityTag("!*") {
                @Override public int hashCode() { return super.hashCode(); }
                @Override public boolean equals(Object o) { return false; }
            };
        }
    }

    private static @Nullable Set<MatchingEntityTag> parseEtags(String str)
    {
        if (str == null || str.isEmpty()) return null;
        try {
            return HttpHeaderReader.readMatchingEntityTag(str);
        } catch (ParseException e) {
            return null;
        }
    }
}

