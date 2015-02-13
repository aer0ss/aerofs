package com.aerofs.daemon.rest.resources;


import com.aerofs.base.NoObfuscation;
import com.aerofs.ids.UserID;
import com.aerofs.labeling.L;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.restless.Version;
import com.google.inject.Inject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.List;

import static com.aerofs.daemon.rest.RestService.HIGHEST_SUPPORTED_VERSION;

/**
 * Non-authenticated version information for use by gateway
 */
@Path("/version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource
{
    /**
     * WARNING: this is pretty hacky
     *
     * We hijack the version detection mechanism to allow sharded TS to report
     * which subset of users they can service requests for.
     *
     * NB: this is a restrictive filter, on top of the already existing org-based
     * filtering and may not be used to service users outside of the org. The
     * value will only be taken into account if it comes from a Team Server.
     */
    @NoObfuscation
    public static class TeamServerInfo extends Version
    {
        public final List<UserID> users;

        public TeamServerInfo(List<UserID> users)
        {
            super(HIGHEST_SUPPORTED_VERSION.major, HIGHEST_SUPPORTED_VERSION.minor);
            // NB: havre will reject response bodies larger than 4Mb so we need to be proactive about
            // clipping the list if it is too large: better a TS that serves API request for a subset
            // of users in the shard than no requests at all
            // In practice the size of a shard will most likely be well under 10k users as a number of
            // other components would probably fail before that number can be reached...
            this.users = users.subList(0, Math.min(users.size(), 10000));
        }
    }

    private final Version _version;

    @Inject
    public VersionResource()
    {
        _version = L.isMultiuser()
                ? new TeamServerInfo(Cfg.usersInShard())
                : HIGHEST_SUPPORTED_VERSION;
    }

    @GET
    public Response getHighestSupportedVersion()
    {
        return Response.ok()
                .entity(_version)
                .build();
    }
}
