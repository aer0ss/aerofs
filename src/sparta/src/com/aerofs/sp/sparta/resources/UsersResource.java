/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Version;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.rest.api.Invitation;
import com.aerofs.rest.util.AuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.util.EntityTagSet;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.ACLNotificationPublisher;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.user.User.PendingSharedFolder;
import com.aerofs.sp.sparta.Transactional;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.security.MessageDigest;
import java.sql.SQLException;

import static com.aerofs.sp.sparta.resources.SharedFolderResource.aclEtag;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.listMembers;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.listPendingMembers;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@Path(Service.VERSION + "/users")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class UsersResource
{
    private final User.Factory _factUser;
    private final ACLNotificationPublisher _aclPublisher;

    @Inject
    public UsersResource(User.Factory factUser, ACLNotificationPublisher aclPublisher)
    {
        _factUser = factUser;
        _aclPublisher = aclPublisher;
    }

    @Since("1.1")
    @GET
    @Path("/{email}")
    public Response get(@Auth AuthToken token,
            @PathParam("email") User user)
            throws ExNotFound, SQLException
    {
        User caller = _factUser.create(token.user);
        throwIfNotSelfOrTSOf(caller, user);

        FullName n = user.getFullName();
        return Response.ok()
                .entity(new com.aerofs.rest.api.User(user.id().getString(), n._first, n._last,
                        listShares(user, null), listInvitations(user)))
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{email}/shares")
    public Response listShares(@Auth AuthToken token, @PathParam("email") User user,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
            throws ExNotFound, SQLException
    {
        User caller = _factUser.create(token.user);
        throwIfNotSelfOrTSOf(caller, user);

        MessageDigest md = BaseSecUtil.newMessageDigestMD5();
        // TODO: it'd be nice if there was an epoch for pending members to avoid this hashing...
        ImmutableCollection<com.aerofs.rest.api.SharedFolder> shares = listShares(user, md);
        EntityTag etag = new EntityTag(aclEtag(caller) + BaseUtil.hexEncode(md.digest()), true);
        if (ifNoneMatch.isValid() && ifNoneMatch.matches(etag)) {
            return Response.notModified(etag).build();
        }

        return Response.ok()
                .entity(shares)
                .tag(etag)
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{email}/invitations")
    public Response listInvitations(@Auth AuthToken token,
            @PathParam("email") User user)
            throws ExNotFound, SQLException
    {
        User caller = _factUser.create(token.user);
        throwIfNotSelfOrTSOf(caller, user);

        return Response.ok()
                .entity(listInvitations(user))
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{email}/invitations/{sid}")
    public Response getInvitation(@Auth AuthToken token,
            @PathParam("email") User user,
            @PathParam("sid") SharedFolder sf)
            throws ExNotFound, SQLException
    {
        User caller = _factUser.create(token.user);
        throwIfNotSelfOrTSOf(caller, user);

        if (sf.getStateNullable(user) != SharedFolderState.PENDING) {
            throw new ExNotFound("No such invitation");
        }

        return Response.ok()
                .entity(invitation(sf, user))
                .build();
    }

    @Since("1.1")
    @POST
    @Path("/{email}/invitations/{sid}")
    public Response acceptInvitation(@Auth AuthToken token,
            @PathParam("email") User user,
            @PathParam("sid") SharedFolder sf,
            @Context Version version)
            throws Exception
    {
        User caller = _factUser.create(token.user);
        throwIfNotSelfOrTSOf(caller, user);

        if (sf.getStateNullable(user) != SharedFolderState.PENDING) {
            throw new ExNotFound("No such invitation");
        }

        _aclPublisher.publish_(sf.setState(user, SharedFolderState.JOINED));

        String location = Service.DUMMY_LOCATION
                + 'v' + version
                + "/shares/" + sf.id().toStringFormal();

        return Response.created(URI.create(location))
                .entity(new com.aerofs.rest.api.SharedFolder(sf.id().toStringFormal(), sf.getName(user),
                        listMembers(sf), listPendingMembers(sf, null)))
                .build();
    }

    @Since("1.1")
    @DELETE
    @Path("/{email}/invitations/{sid}")
    public Response ignoreInvitation(@Auth AuthToken token,
            @PathParam("email") User user,
            @PathParam("sid") SharedFolder sf)
            throws Exception
    {
        User caller = _factUser.create(token.user);
        throwIfNotSelfOrTSOf(caller, user);

        if (sf.getStateNullable(user) != SharedFolderState.PENDING) {
            throw new ExNotFound("No such invitation");
        }

        try {
            checkState(sf.removeUser(user).isEmpty());
        } catch (ExNoAdminOrOwner e) {
            throw new ExBadArgs(e.getMessage());
        }

        return Response.noContent()
                .build();
    }

    public static boolean isSelfOrTSOf(User caller, User target)
            throws ExNotFound, SQLException
    {
        return caller.equals(target)
                || caller.id().equals(target.getOrganization().id().toTeamServerUserID());
    }

    private static void throwIfNotSelfOrTSOf(User caller, User target)
            throws ExNotFound, SQLException
    {
        if (!isSelfOrTSOf(caller, target)) throw new ExNotFound("No such user");
    }

    static ImmutableCollection<com.aerofs.rest.api.SharedFolder> listShares(User user,
            MessageDigest md)
            throws ExNotFound, SQLException
    {
        ImmutableList.Builder<com.aerofs.rest.api.SharedFolder> bd = ImmutableList.builder();
        for (SharedFolder sf : user.getSharedFolders()) {
            // filter out root store
            if (sf.id().isUserRoot()) continue;
            bd.add(new com.aerofs.rest.api.SharedFolder(sf.id().toStringFormal(), sf.getName(user),
                    SharedFolderResource.listMembers(sf),
                    SharedFolderResource.listPendingMembers(sf, md)));
        }
        return bd.build();
    }

    static Invitation invitation(SharedFolder sf, User invitee)
            throws ExNotFound, SQLException
    {
        return new Invitation(sf.id().toStringFormal(), sf.getName(invitee),
                sf.getSharerNullable(invitee).id().getString(),
                sf.getPermissionsNullable(invitee).toArray());
    }

    static ImmutableCollection<Invitation> listInvitations(User user)
            throws ExNotFound, SQLException
    {
        ImmutableList.Builder<com.aerofs.rest.api.Invitation> bd = ImmutableList.builder();
        for (PendingSharedFolder p : user.getPendingSharedFolders()) {
            bd.add(invitation(p._sf, user));
        }
        return bd.build();
    }
}
