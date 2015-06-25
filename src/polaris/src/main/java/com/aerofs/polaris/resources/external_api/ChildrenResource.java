package com.aerofs.polaris.resources.external_api;

import com.aerofs.auth.server.AeroOAuthPrincipal;
import com.aerofs.base.id.RestObject;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.polaris.external_api.metadata.MetadataBuilder;
import com.aerofs.polaris.external_api.rest.util.Since;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.aerofs.polaris.external_api.Constants.EXTERNAL_API_VERSION;

@Path(EXTERNAL_API_VERSION + "/children")
@Produces(MediaType.APPLICATION_JSON)
public class ChildrenResource
{
    private final MetadataBuilder metadataBuilder;

    public ChildrenResource(@Context MetadataBuilder metadataBuilder)
    {
        this.metadataBuilder = metadataBuilder;
    }

    @Since("0.8")
    @GET
    public Response listUserRoot(@Context AeroOAuthPrincipal principal)
    {
        return list(principal, new RestObject(SID.rootSID(principal.getUser()), OID.ROOT));
    }

    @Since("0.9")
    @GET
    @Path("/{folder_id}")
    public Response list(@Context AeroOAuthPrincipal principal,
                         @PathParam("folder_id") RestObject object)
    {
        return metadataBuilder.children(principal, object, true);
    }
}
