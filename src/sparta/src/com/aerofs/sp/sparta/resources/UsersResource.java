/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.FullName;
import com.aerofs.rest.util.AuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.util.EntityTagSet;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.sparta.Transactional;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.SQLException;

import static com.aerofs.sp.sparta.resources.SharedFolderResource.aclEtag;

@Path(Service.VERSION + "/users")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class UsersResource
{
    User.Factory _factUser;

    @Inject
    public UsersResource(User.Factory factUser)
    {
        _factUser = factUser;
    }

    @Since("1.1")
    @GET
    @Path("/{email}")
    public Response get(@Auth AuthToken token,
            @PathParam("email") User user)
            throws ExNotFound, SQLException
    {
        User caller = _factUser.create(token.user);
        throwIfNotSelfOrAdminOf(caller, user);

        FullName n = user.getFullName();
        return Response.ok()
                .entity(new com.aerofs.rest.api.User(user.id().getString(), n._first, n._last,
                        listShares(user)))
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{email}/shares")
    public Response listShares(@Auth AuthToken token,
            @PathParam("email") User user,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
            throws ExNotFound, SQLException
    {
        User caller = _factUser.create(token.user);
        throwIfNotSelfOrAdminOf(caller, user);

        EntityTag etag = new EntityTag(aclEtag(caller), true);
        if (ifNoneMatch.isValid() && ifNoneMatch.matches(etag)) {
            return Response.notModified(etag).build();
        }

        return Response.ok()
                .entity(listShares(user))
                .tag(etag)
                .build();
    }

    private static void throwIfNotSelfOrAdminOf(User caller, User target)
            throws ExNotFound, SQLException
    {
        if (!(caller.equals(target) || caller.isAdminOf(target))) {
            throw new ExNotFound("No such user");
        }
    }

    private static ImmutableCollection<com.aerofs.rest.api.SharedFolder> listShares(User user)
            throws ExNotFound, SQLException
    {
        ImmutableList.Builder<com.aerofs.rest.api.SharedFolder> bd = ImmutableList.builder();
        for (SharedFolder sf : user.getSharedFolders()) {
            // filter out root store
            if (sf.id().isUserRoot()) continue;
            bd.add(new com.aerofs.rest.api.SharedFolder(sf.id().toStringFormal(), sf.getName(),
                    SharedFolderResource.listMembers(sf)));
        }
        return bd.build();
    }
}
