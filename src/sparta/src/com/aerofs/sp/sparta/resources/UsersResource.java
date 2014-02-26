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
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.rest.api.Invitation;
import com.aerofs.rest.util.AuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.util.EntityTagSet;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.ACLNotificationPublisher;
import com.aerofs.sp.server.CommandDispatcher;
import com.aerofs.sp.server.PasswordManagement;
import com.aerofs.sp.server.UserManagement;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.user.User.Factory;
import com.aerofs.sp.server.lib.user.User.PendingSharedFolder;
import com.aerofs.sp.sparta.Transactional;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.Collection;

import static com.aerofs.sp.sparta.resources.SharedFolderResource.aclEtag;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.listMembers;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.listPendingMembers;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@Path(Service.VERSION + "/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class UsersResource
{
    private static Logger l = LoggerFactory.getLogger(UsersResource.class);
    private final User.Factory _factUser;
    private final ACLNotificationPublisher _aclPublisher;
    private final VerkehrAdmin _verkehrAdmin;
    private final CommandDispatcher _commandDispatcher;
    private PasswordManagement _passwordManagement;

    @Inject
    public UsersResource(Factory factUser, ACLNotificationPublisher aclPublisher,
            CommandDispatcher commandDispatcher, VerkehrAdmin verkerhAdmin,
            PasswordManagement passwordManagement)
    {
        _factUser = factUser;
        _aclPublisher = aclPublisher;
        _verkehrAdmin = verkerhAdmin;
        _commandDispatcher = commandDispatcher;
        _commandDispatcher.setAdminClient(_verkehrAdmin);
        _passwordManagement = passwordManagement;
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
                .entity(new com.aerofs.rest.api.SharedFolder(sf.id().toStringFormal(),
                        sf.getName(user), listMembers(sf), listPendingMembers(sf, null)))
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

    private static void throwIfNotSelfOrTSOf(User caller, User target)
            throws ExNotFound, SQLException
    {
        if (!UserManagement.isSelfOrTSOf(caller, target)) throw new ExNotFound("No such user");
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

    @Since("1.1")
    @POST
    public Response create(
            @Auth AuthToken auth,
            com.aerofs.rest.api.User attrs,
            @Context Version version) throws Exception
    {
        checkArgument(attrs.email != null, "Request body missing required field: email");
        checkArgument(attrs.firstName != null, "Request body missing required field: first_name");
        checkArgument(attrs.lastName != null, "Request body missing required field: last_name");

        User caller = _factUser.create(auth.user);
        User newUser = _factUser.createFromExternalID(attrs.email);

        // We are using "Team Server"-ness as a simple way to distinguish elevated callers.
        if (newUser.exists()) {
            return Response.status(Status.CONFLICT)
                    .entity("User cannot be created")
                    .build();
        }

        if (!caller.id().isTeamServerID()) {
            return Response.status(Status.FORBIDDEN)
                    .entity("Insufficient admin privilege to create a user")
                    .build();
        }

        newUser.save(new byte[0], new FullName(attrs.firstName, attrs.lastName));
        // Admins can only create users in their (the admin's) org:
        newUser.setOrganization(caller.getOrganization(), AuthorizationLevel.USER);

        // notify TS of user creation (for root store auto-join)
        _aclPublisher.publish_(newUser.getOrganization().id().toTeamServerUserID());

        l.info("Created user {}", newUser);

        String location = Service.DUMMY_LOCATION
                + 'v' + version
                + "/users/" + newUser.id().getString();
        return Response.created(URI.create(location))
                .entity(new com.aerofs.rest.api.User(newUser.id().getString(), attrs.firstName,
                        attrs.lastName, listShares(newUser, null), listInvitations(newUser)))
                .build();
    }

    @Since("1.1")
    @PUT
    @Path("/{email}")
    public Response update(
            @Auth AuthToken auth,
            @PathParam("email")User target,
            com.aerofs.rest.api.User attrs) throws Exception
    {
        if (!target.exists()) throw new ExNotFound("No such user");
        checkArgument(attrs.firstName != null, "Request body missing required first_name");
        checkArgument(attrs.lastName != null, "Request body missing required last_name");

        User caller = _factUser.create(auth.user);
        throwIfNotSelfOrTSOf(caller, target);

        FullName fullName = FullName.fromExternal(attrs.firstName, attrs.lastName);
        target.setName(fullName);
        Collection<Device> peerDevices = target.getPeerDevices();

        for (Device peer : peerDevices) {
            l.info("API update: inval user cache for " + peer.id().toStringFormal());
            _commandDispatcher.enqueueCommand(peer.id(), CommandType.INVALIDATE_USER_NAME_CACHE);
        }

        l.warn("Updated user {}", attrs);
        return Response.ok()
                .entity(new com.aerofs.rest.api.User(target.id().getString(), attrs.firstName,
                        attrs.lastName, listShares(target, null), listInvitations(target)))
                .build();
    }

    @Since("1.1")
    @DELETE
    @Path("/{email}")
    public Response delete(
            @Auth AuthToken auth,
            @PathParam("email")User target)
            throws Exception
    {
        if (!target.exists()) throw new ExNotFound("No such user");

        User caller = _factUser.create(auth.user);
        throwIfNotSelfOrTSOf(caller, target);

        l.debug("API: user {} attempt delete {}", caller, target);
        UserManagement.deactivateByTS(caller, target, false, _commandDispatcher, _aclPublisher);
        l.info("Deleted user {}", target.id().getString());

        return Response.noContent()
                .build();
    }

    @Since("1.1")
    @PUT
    @Path("/{email}/password")
    public Response updatePassword(
            @Auth AuthToken auth,
            @PathParam("email")User target,
            String newCredential) throws Exception
    {
        checkArgument(!Strings.isNullOrEmpty(newCredential), "Cannot set an empty password value");

        User caller = _factUser.create(auth.user);
        throwIfNotSelfOrTSOf(caller, target);

        l.debug("API: user {} requests password update for {}", caller, target);
        _passwordManagement.setPassword(target.id(), newCredential.getBytes("UTF-8"));

        return Response.noContent().build();
    }

    @Since("1.1")
    @DELETE
    @Path("/{email}/password")
    public Response deletePassword(
            @Auth AuthToken auth,
            @PathParam("email")User target)
            throws Exception
    {
        User caller = _factUser.create(auth.user);
        throwIfNotSelfOrTSOf(caller, target);

        l.debug("API: user {} requests password delete for {}", caller, target);
        _passwordManagement.revokePassword(target.id());

        return Response.noContent().build();
    }
}
