/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.id.RestObject;
import com.aerofs.daemon.rest.event.*;
import com.aerofs.daemon.rest.util.UploadID;
import com.aerofs.rest.auth.OAuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.util.ContentRange;
import com.aerofs.restless.util.EntityTagSet;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;

import javax.ws.rs.*;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

import static com.aerofs.daemon.rest.util.EntityTagParser.parse;

@Path(Service.VERSION + "/files")
@Produces(MediaType.APPLICATION_JSON)
public class FilesResource extends AbstractResource
{
    @Since("0.9")
    @GET
    @Path("/{file_id}/content")
    @Produces({MediaType.APPLICATION_OCTET_STREAM, "multipart/byteranges"})
    public Response content(@Auth OAuthToken token,
            @PathParam("file_id") RestObject object,
            @HeaderParam(Names.IF_RANGE) String ifRange,
            @HeaderParam(Names.RANGE) String range,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
    {
        EntityTag etIfRange = parse(ifRange);
        return new EIFileContent(_imce, token, object, etIfRange, range, ifNoneMatch).execute();
    }

    @Since("0.10")
    @PUT
    @Path("/{file_id}/content")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response upload(@Auth OAuthToken token,
            @PathParam("file_id") RestObject object,
            @HeaderParam(Names.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            @HeaderParam(Names.CONTENT_RANGE) ContentRange range,
            @HeaderParam("Upload-ID") @DefaultValue("") UploadID ulid,
            InputStream body) throws IOException
    {
        try {
            return new EIFileUpload(_imce, token, object, ifMatch, ulid, range, body).execute();
        } finally {
            body.close();
        }
    }
}
