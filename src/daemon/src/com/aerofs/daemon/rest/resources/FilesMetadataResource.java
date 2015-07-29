package com.aerofs.daemon.rest.resources;

import com.aerofs.base.id.RestObject;
import com.aerofs.daemon.rest.event.*;
import com.aerofs.daemon.rest.event.EIObjectInfo.Type;
import com.aerofs.daemon.rest.util.Fields;
import com.aerofs.rest.api.File;
import com.aerofs.rest.auth.OAuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.Version;
import com.aerofs.restless.util.EntityTagSet;
import org.jboss.netty.handler.codec.http.HttpHeaders;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;

// In the legacy world, clients also served metadata requests about files. Now only handled by
// Polaris. TODO(AS): Remove after legacy is completely obsolete.
// N.B. In Java to inherit annotations, the annotation itself has to be annotated with
// @Inherited. That is not possible here.
@Path(Service.VERSION + "/files")
@Produces(MediaType.APPLICATION_JSON)
public class FilesMetadataResource extends FilesResource
{
    @Since("0.9")
    @GET
    @Path("/{file_id}")
    public Response metadata(@Auth OAuthToken token,
                             @PathParam("file_id") RestObject object,
                             @Context Version version,
                             @QueryParam("fields") Fields fields)
    {
        return new EIObjectInfo(_imce, token, object, Type.FILE,
                version.compareTo(new Version(1, 2)) < 0 ? null : fields)
                .execute();
    }

    @Since("0.10")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Auth OAuthToken token,
                           @Context Version version,
                           File file) throws IOException
    {
        checkArgument(file != null);
        checkArgument(file.parent != null);
        checkArgument(file.name != null);
        return new EICreateObject(_imce, token, version, file.parent, file.name, false).execute();
    }


    @Since("0.10")
    @PUT
    @Path("/{file_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response move(@Auth OAuthToken token,
                         @PathParam("file_id") RestObject object,
                         @HeaderParam(HttpHeaders.Names.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
                         File file) throws IOException
    {
        checkArgument(file != null);
        checkArgument(file.parent != null);
        checkArgument(file.name != null);
        return new EIMoveObject(_imce, token, object, file.parent, file.name, ifMatch).execute();
    }

    @Since("0.10")
    @DELETE
    @Path("/{file_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response delete(@Auth OAuthToken token,
                           @PathParam("file_id") RestObject object,
                           @HeaderParam(HttpHeaders.Names.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch)
            throws IOException
    {
        return new EIDeleteObject(_imce, token, object, ifMatch).execute();
    }

    @Since("1.2")
    @GET
    @Path("/{file_id}/path")
    public Response path(@Auth OAuthToken token,
                         @PathParam("file_id") RestObject object)
    {
        return new EIObjectPath(_imce,  token, object).execute();
    }
}
