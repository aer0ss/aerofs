/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.audit.client.AuditClient.AuditableEvent;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.restless.Version;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.oauth.Scope;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.rest.api.Invitation;
import com.aerofs.rest.api.Quota;
import com.aerofs.rest.util.IAuthToken;
import com.aerofs.rest.util.IUserAuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.util.EntityTagSet;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.ACLNotificationPublisher;
import com.aerofs.sp.server.CommandDispatcher;
import com.aerofs.sp.server.PasswordManagement;
import com.aerofs.sp.server.UserManagement;
import com.aerofs.sp.server.audit.AuditCaller;
import com.aerofs.sp.server.audit.AuditFolder;
import com.aerofs.sp.server.email.TwoFactorEmailer;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.user.User.Factory;
import com.aerofs.sp.server.lib.user.User.PendingSharedFolder;
import com.aerofs.sp.sparta.Transactional;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.Collection;

import static com.aerofs.sp.server.CommandUtil.createCommandMessage;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.aclEtag;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.listMembers;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.listPendingMembers;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@Path(Service.VERSION + "/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class UsersResource extends AbstractSpartaResource
{
    private static Logger l = LoggerFactory.getLogger(UsersResource.class);
    private final User.Factory _factUser;
    private final ACLNotificationPublisher _aclPublisher;
    private final CommandDispatcher _commandDispatcher;
    private final PasswordManagement _passwordManagement;
    private final AuditClient _audit;
    private final TwoFactorEmailer _twoFactorEmailer;

    @Inject
    public UsersResource(Factory factUser, ACLNotificationPublisher aclPublisher,
            CommandDispatcher commandDispatcher, VerkehrClient verkehrClient,
            PasswordManagement passwordManagement, AuditClient audit,
            TwoFactorEmailer twoFactorEmailer)
    {
        _factUser = factUser;
        _aclPublisher = aclPublisher;
        _commandDispatcher = commandDispatcher;
        _commandDispatcher.setVerkehrClient(verkehrClient);
        _passwordManagement = passwordManagement;
        _audit = audit;
        _twoFactorEmailer = twoFactorEmailer;
    }

    private AuditableEvent audit(User caller, IUserAuthToken token, AuditTopic topic, String event)
            throws SQLException, ExNotFound
    {
        return _audit.event(topic, event)
                .embed("caller", new AuditCaller(caller.id(), token.issuer(), token.uniqueId()));
    }

    @Since("1.1")
    @GET
    @Path("/{email}")
    public Response get(@Auth IUserAuthToken token,
            @PathParam("email") User user)
            throws ExNotFound, SQLException
    {
        requirePermission(Scope.READ_USER, token);
        User caller = _factUser.create(token.user());
        throwIfNotSelfOrTSOf(caller, user);

        FullName n = user.getFullName();
        return Response.ok()
                .entity(new com.aerofs.rest.api.User(user.id().getString(), n._first, n._last,
                        listShares(user, null, token), listInvitations(user, token)))
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{email}/shares")
    public Response listShares(@Auth IUserAuthToken token, @PathParam("email") User user,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
            throws ExNotFound, SQLException
    {
        User caller = _factUser.create(token.user());
        throwIfNotSelfOrTSOf(caller, user);

        MessageDigest md = BaseSecUtil.newMessageDigestMD5();
        // TODO: it'd be nice if there was an epoch for pending members to avoid this hashing...
        ImmutableCollection<com.aerofs.rest.api.SharedFolder> shares = listShares(user, md, token);
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
    public Response listInvitations(@Auth IUserAuthToken token,
            @PathParam("email") User user)
            throws ExNotFound, SQLException
    {
        requirePermission(Scope.MANAGE_INVITATIONS, token);
        User caller = _factUser.create(token.user());
        throwIfNotSelfOrTSOf(caller, user);

        return Response.ok()
                .entity(listInvitations(user, token))
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{email}/invitations/{sid}")
    public Response getInvitation(@Auth IUserAuthToken token,
            @PathParam("email") User user,
            @PathParam("sid") SharedFolder sf)
            throws ExNotFound, SQLException
    {
        requirePermission(Scope.MANAGE_INVITATIONS, token);
        User caller = _factUser.create(token.user());
        throwIfNotSelfOrTSOf(caller, user);

        if (sf.getStateNullable(user) != SharedFolderState.PENDING) {
            throw new ExNotFound("No such invitation");
        }

        return Response.ok()
                .entity(invitation(sf, user))
                .build();
    }

    private static boolean toBool(String s)
    {
        checkArgument(ImmutableSet.of("0", "1").contains(s),
                "Invalid boolean value. Expected \"0\" or \"1\"");
        return s.equals("1");
    }

    @Since("1.1")
    @POST
    @Path("/{email}/invitations/{sid}")
    public Response acceptInvitation(@Auth IUserAuthToken token,
            @PathParam("email") User user,
            @PathParam("sid") SharedFolder sf,
            @QueryParam("external") @DefaultValue("0") String external,
            @Context Version version)
            throws Exception
    {
        requirePermission(Scope.MANAGE_INVITATIONS, token);
        User caller = _factUser.create(token.user());
        throwIfNotSelfOrTSOf(caller, user);

        if (sf.getStateNullable(user) != SharedFolderState.PENDING) {
            throw new ExNotFound("No such invitation");
        }

        // TODO: extract and reuse code from SPService.joinSharedFolderImpl?
        ImmutableCollection<UserID> affected = sf.setState(user, SharedFolderState.JOINED);
        sf.setExternal(user, toBool(external));

        _aclPublisher.publish_(affected);

        audit(caller, token, AuditTopic.SHARING, "folder.join")
                .embed("folder", new AuditFolder(sf.id(), sf.getName(caller)))
                .add("target", user.id())
                .embed("role", sf.getPermissionsNullable(user).toArray())
                .publish();

        String location = Service.DUMMY_LOCATION
                + 'v' + version
                + "/shares/" + sf.id().toStringFormal();

        return Response.created(URI.create(location))
                .entity(new com.aerofs.rest.api.SharedFolder(sf.id().toStringFormal(),
                        sf.getName(user), listMembers(sf), listPendingMembers(sf, null),
                        sf.isExternal(user)))
                .build();
    }

    @Since("1.1")
    @DELETE
    @Path("/{email}/invitations/{sid}")
    public Response ignoreInvitation(@Auth IUserAuthToken token,
            @PathParam("email") User user,
            @PathParam("sid") SharedFolder sf)
            throws Exception
    {
        requirePermission(Scope.MANAGE_INVITATIONS, token);
        User caller = _factUser.create(token.user());
        throwIfNotSelfOrTSOf(caller, user);

        if (sf.getStateNullable(user) != SharedFolderState.PENDING) {
            throw new ExNotFound("No such invitation");
        }

        try {
            checkState(sf.removeUser(user).isEmpty());
        } catch (ExNoAdminOrOwner e) {
            throw new ExBadArgs(e.getMessage());
        }

        audit(caller, token, AuditTopic.SHARING, "folder.delete_invitation")
                .embed("folder", new AuditFolder(sf.id(), sf.getName(caller)))
                .add("target", user.id())
                .publish();

        return Response.noContent()
                .build();
    }

    private static void throwIfNotSelfOrTSOf(User caller, User target)
            throws ExNotFound, SQLException
    {
        if (!UserManagement.isSelfOrTSOf(caller, target)) throw new ExNotFound("No such user");
    }

    static ImmutableCollection<com.aerofs.rest.api.SharedFolder> listShares(User user,
            MessageDigest md, IAuthToken token)
            throws ExNotFound, SQLException
    {
        ImmutableList.Builder<com.aerofs.rest.api.SharedFolder> bd = ImmutableList.builder();
        for (SharedFolder sf : user.getJoinedFolders()) {
            // filter out root store
            if (sf.id().isUserRoot()) continue;
            com.aerofs.rest.api.SharedFolder s = new com.aerofs.rest.api.SharedFolder(
                    sf.id().toStringFormal(), sf.getName(user),
                    SharedFolderResource.listMembers(sf),
                    SharedFolderResource.listPendingMembers(sf, md),
                    sf.isExternal(user));
            if (token.hasFolderPermission(Scope.READ_ACL, sf.id())) bd.add(s);
        }
        return bd.build();
    }

    static Invitation invitation(SharedFolder sf, User invitee)
            throws ExNotFound, SQLException
    {
        User sharer = sf.getSharerNullable(invitee);
        Permissions permissions = sf.getPermissionsNullable(invitee);
        return new Invitation(sf.id().toStringFormal(), sf.getName(invitee),
                sharer == null ? null : sharer.id().getString(),
                permissions == null ? null : permissions.toArray());
    }

    static ImmutableCollection<Invitation> listInvitations(User user, IAuthToken token)
            throws ExNotFound, SQLException
    {
        ImmutableList.Builder<com.aerofs.rest.api.Invitation> bd = ImmutableList.builder();
        if (token.hasPermission(Scope.MANAGE_INVITATIONS)) {
            for (PendingSharedFolder p : user.getPendingSharedFolders()) {
                bd.add(invitation(p._sf, user));
            }
        }
        return bd.build();
    }

    @Since("1.1")
    @POST
    public Response create(
            @Auth IUserAuthToken auth,
            com.aerofs.rest.api.User attrs,
            @Context Version version) throws Exception
    {
        requirePermission(Scope.WRITE_USER, auth);
        checkArgument(attrs.email != null, "Request body missing required field: email");
        checkArgument(attrs.firstName != null, "Request body missing required field: first_name");
        checkArgument(attrs.lastName != null, "Request body missing required field: last_name");

        User caller = _factUser.create(auth.user());
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

        audit(caller, auth, AuditTopic.USER, "user.create")
                .add("email", newUser.id())
                .publish();

        String location = Service.DUMMY_LOCATION
                + 'v' + version
                + "/users/" + newUser.id().getString();
        return Response.created(URI.create(location))
                .entity(new com.aerofs.rest.api.User(newUser.id().getString(), attrs.firstName,
                        attrs.lastName, listShares(newUser, null, auth),
                        listInvitations(newUser, auth)))
                .build();
    }

    @Since("1.1")
    @PUT
    @Path("/{email}")
    public Response update(
            @Auth IUserAuthToken auth,
            @PathParam("email") User target,
            com.aerofs.rest.api.User attrs) throws Exception
    {
        requirePermission(Scope.WRITE_USER, auth);
        checkArgument(attrs.firstName != null, "Request body missing required first_name");
        checkArgument(attrs.lastName != null, "Request body missing required last_name");

        User caller = _factUser.create(auth.user());
        throwIfNotSelfOrTSOf(caller, target);

        FullName fullName = FullName.fromExternal(attrs.firstName, attrs.lastName);
        target.setName(fullName);
        Collection<Device> peerDevices = target.getPeerDevices();

        for (Device peer : peerDevices) {
            l.info("API update: inval user cache for " + peer.id().toStringFormal());
            _commandDispatcher.enqueueCommand(peer.id(), createCommandMessage(
                    CommandType.INVALIDATE_USER_NAME_CACHE));
        }

        audit(caller, auth, AuditTopic.USER, "user.update")
                .add("email", target.id())
                .publish();

        l.warn("Updated user {}", attrs);
        return Response.ok()
                .entity(new com.aerofs.rest.api.User(target.id().getString(), attrs.firstName,
                        attrs.lastName, listShares(target, null, auth),
                        listInvitations(target, auth)))
                .build();
    }

    @Since("1.1")
    @DELETE
    @Path("/{email}")
    public Response delete(
            @Auth IUserAuthToken auth,
            @PathParam("email") User target)
            throws Exception
    {
        requirePermission(Scope.WRITE_USER, auth);
        User caller = _factUser.create(auth.user());
        throwIfNotSelfOrTSOf(caller, target);

        l.debug("API: user {} attempt delete {}", caller, target);
        UserManagement.deactivateByTS(caller, target, false, _commandDispatcher, _aclPublisher);
        l.info("Deleted user {}", target.id().getString());

        audit(caller, auth, AuditTopic.USER, "user.delete")
                .add("email", target.id())
                .publish();

        return Response.noContent()
                .build();
    }

    @Since("1.1")
    @PUT
    @Path("/{email}/password")
    public Response updatePassword(
            @Auth IUserAuthToken auth,
            @PathParam("email") User target,
            String newCredential) throws Exception
    {
        requirePermission(Scope.MANAGE_PASSWORD, auth);
        checkArgument(!Strings.isNullOrEmpty(newCredential), "Cannot set an empty password value");

        User caller = _factUser.create(auth.user());
        throwIfNotSelfOrTSOf(caller, target);

        l.debug("API: user {} requests password update for {}", caller, target);
        _passwordManagement.setPassword(target.id(), newCredential.getBytes("UTF-8"));

        audit(caller, auth, AuditTopic.USER, "user.password.update")
                .add("email", target.id())
                .publish();

        return Response.noContent().build();
    }

    @Since("1.1")
    @DELETE
    @Path("/{email}/password")
    public Response deletePassword(
            @Auth IUserAuthToken auth,
            @PathParam("email") User target)
            throws Exception
    {
        requirePermission(Scope.MANAGE_PASSWORD, auth);
        User caller = _factUser.create(auth.user());
        throwIfNotSelfOrTSOf(caller, target);

        l.debug("API: user {} requests password delete for {}", caller, target);
        _passwordManagement.revokePassword(target.id());

        audit(caller, auth, AuditTopic.USER, "user.password.revoke")
                .add("email", target.id())
                .publish();

        return Response.noContent().build();
    }

    @Since("1.2")
    @GET
    @Path("/{email}/quota")
    public Response getQuota(@Auth IUserAuthToken auth, @PathParam("email") User user)
            throws SQLException, ExNotFound
    {
        requirePermission(Scope.READ_USER, auth);
        User caller = _factUser.create(auth.user());
        throwIfNotSelfOrTSOf(caller, user);

        return Response.ok()
                .entity(new Quota(
                        user.getBytesUsed(),
                        user.getOrganization().getQuotaPerUser()))
                .build();
    }

    private class TwoFactor
    {
        public final Boolean enforce;
        public TwoFactor(Boolean enforce)
        {
            this.enforce = enforce;
        }
    }

    @Since("1.3")
    @GET
    @Path("/{email}/two_factor")
    public Response getTwoFactorEnabled(@Auth IUserAuthToken auth, @PathParam("email") User user)
            throws SQLException, ExNotFound
    {
        requirePermission(Scope.READ_USER, auth);
        User caller = _factUser.create(auth.user());
        throwIfNotSelfOrTSOf(caller, user);

        return Response.ok()
                .entity(new TwoFactor(user.shouldEnforceTwoFactor()))
                .build();
    }

    @Since("1.3")
    @DELETE
    @Path("/{email}/two_factor")
    public Response disableTwoFactor(
            @Auth IUserAuthToken auth,
            @PathParam("email") User user)
            throws Exception
    {
        requirePermission(Scope.MANAGE_PASSWORD, auth);
        User caller = _factUser.create(auth.user());
        throwIfNotSelfOrTSOf(caller, user);
        user.disableTwoFactorEnforcement();
        audit(caller, auth, AuditTopic.USER, "user.2fa.disable")
                .add("user", user.id())
                .publish();
        _twoFactorEmailer.sendTwoFactorDisabledEmail(user.id().getString(),
                user.getFullName()._first);
        return Response.noContent().build();
    }
}
