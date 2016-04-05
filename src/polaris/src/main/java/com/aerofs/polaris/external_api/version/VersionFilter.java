package com.aerofs.polaris.external_api.version;

import com.aerofs.base.Loggers;
import com.aerofs.polaris.external_api.rest.util.Version;
import com.aerofs.rest.api.Error;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static com.aerofs.polaris.external_api.Constants.HIGHEST_SUPPORTED_VERSION;
import static com.aerofs.polaris.external_api.Constants.REQUEST_VERSION;

public class VersionFilter implements ContainerRequestFilter
{
    private static final Logger l = Loggers.getLogger(VersionFilter.class);
    private final Version _version;

    VersionFilter(String ver)
    {
        _version = Version.fromStringNullable(ver);
        Preconditions.checkArgument(_version != null, "Version in @Since annotation cannot be null");
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException
    {
        String path = "/" + requestContext.getUriInfo().getPath();
        l.trace("path {} ", path);

        Version verFromRequest = Version.fromRequestPath(path);
        l.trace("version {} ", verFromRequest);

        if (verFromRequest != null && isSupportedVersion(verFromRequest)
                && _version.compareTo(verFromRequest) <= 0) {
            l.debug("accept version {} [{}]", verFromRequest, _version);
            requestContext.setProperty(REQUEST_VERSION, verFromRequest);
            return;
        }
        l.warn("reject version {} [{}]", path, _version);
        requestContext.abortWith(Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new Error(Error.Type.NOT_FOUND, "No such resource"))
                .build());
    }

    private boolean isSupportedVersion(Version version)
    {
        return version.compareTo(HIGHEST_SUPPORTED_VERSION) <= 0;
    }
}
