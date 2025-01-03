/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import static com.aerofs.sp.server.CommandUtil.createCommandMessage;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.listGroupMembers;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.listMembers;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.listPendingMembers;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.net.URI;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.aerofs.restless.util.EntityTagSet;
import com.aerofs.servlets.lib.analytics.AnalyticsEvent;
import com.aerofs.servlets.lib.analytics.IAnalyticsClient;
import com.aerofs.servlets.lib.ThreadLocalSFNotifications;
import com.aerofs.sp.server.*;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.audit.client.AuditClient.AuditableEvent;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.oauth.Scope;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.rest.api.Invitation;
import com.aerofs.rest.api.Page;
import com.aerofs.rest.api.Quota;
import com.aerofs.rest.auth.IAuthToken;
import com.aerofs.rest.auth.IUserAuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.Version;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.audit.AuditCaller;
import com.aerofs.sp.server.audit.AuditFolder;
import com.aerofs.sp.server.email.TwoFactorEmailer;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.user.User.Factory;
import com.aerofs.sp.server.lib.user.User.PendingSharedFolder;
import com.aerofs.sp.sparta.Transactional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;

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
    private final IAnalyticsClient _analyticsClient;
    private final ThreadLocalSFNotifications _sfNotif;
    private final SFNotificationPublisher _sfPublisher;

    @Inject
    public UsersResource(Factory factUser, ACLNotificationPublisher aclPublisher, CommandDispatcher commandDispatcher,
             PasswordManagement passwordManagement, AuditClient audit, TwoFactorEmailer twoFactorEmailer,
             IAnalyticsClient analyticsClient, ThreadLocalSFNotifications sfNotif, SFNotificationPublisher sfPublisher)
    {
        _factUser = factUser;
        _aclPublisher = aclPublisher;
        _commandDispatcher = commandDispatcher;
        _passwordManagement = passwordManagement;
        _audit = audit;
        _twoFactorEmailer = twoFactorEmailer;
        _analyticsClient = analyticsClient;
        _sfNotif = sfNotif;
        _sfPublisher = sfPublisher;
    }

    private AuditableEvent audit(User caller, IUserAuthToken token, AuditTopic topic, String event)
            throws SQLException, ExNotFound
    {
        return _audit.event(topic, event)
                .embed("caller", new AuditCaller(caller.id(), token.issuer(), token.uniqueId()));
    }

    private @Nullable User validateAuth(IAuthToken token, Scope scope, User user)
            throws SQLException, ExNotFound
    {
        requirePermission(scope, token);
        if (token instanceof IUserAuthToken) {
            User caller = _factUser.create(((IUserAuthToken) token).user());
            throwIfNotSelfOrTSOf(caller, user);
            return caller;
        }
        return null;
    }

    /**
     * Retrieves a page of Users using the given params
     *
     * @param limit maximum number of users to return in the response.
     * @param after cursor for paginated response. Returned page begins after
     *            this user ID.
     * @param before cursor for paginated response. Returned page ends before
     *            this user ID.
     */
    @Since("1.3")
    @GET
    public Response list(@Auth IAuthToken token, @Context Version version,
            @QueryParam("limit") @DefaultValue("20") int limit, @QueryParam("after") String after,
            @QueryParam("before") String before) throws ExInvalidID, SQLException, ExNotFound {
        requirePermission(Scope.READ_USER, token);
        List<UserID> userIDs = _factUser.listUsers(limit > 0 ? limit + 1 : 0,
                after != null ? UserID.fromExternal(after) : null,
                before != null ? UserID.fromExternal(before) : null);
        boolean hasMore = userIDs.size() == limit + 1;
        if (hasMore) {
            userIDs.remove(limit);
        }
        return Response.ok().entity(new Page<>(hasMore, createUsersList(userIDs))).build();
    }

    @Since("1.1")
    @GET
    @Path("/{email}")
    public Response get(@Auth IAuthToken token, @Context Version version,
                        @PathParam("email") User user)
            throws ExNotFound, SQLException
    {
        requirePermission(Scope.READ_USER, token);
        User caller = _factUser.create(((IUserAuthToken) token).user());
        FullName n = user.getFullName();
        if (UserManagement.isSelfOrTSOf(caller, user)) {
            return Response.ok()
                    .entity(new com.aerofs.rest.api.User(user.id().getString(), n._first, n._last,
                            listShares(user, version, token), listInvitations(user, token)))
                    .build();
        } else {
            return Response.ok()
                    .entity(new com.aerofs.rest.api.User(user.id().getString(), n._first, n._last, null, null))
                    .build();
        }
    }

    @Since("1.3")
    @GET
    @Path("/{email}/devices")
    public Response listDevices(@Auth IAuthToken token, @PathParam("email") User user)
            throws ExNotFound, ExInvalidID, SQLException
    {
        validateAuth(token, Scope.READ_USER, user);

        return Response.ok()
                .entity(user.getDevices().stream().map(d -> {
                    try {
                        return new com.aerofs.rest.api.Device(d.id().toStringFormal(),
                                d.getOwner().id().getString(), d.getName(), d.getOSFamily(),
                                new Date(d.getInstallDate()));
                    } catch (Exception e) {
                        return null;
                    }
                }).filter(d -> d != null).toArray())
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{email}/shares")
    public Response listShares(@Auth IAuthToken token, @Context Version version,
                               @HeaderParam(HttpHeaders.Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch,
                               @PathParam("email") User user)
            throws ExNotFound, SQLException
    {
        validateAuth(token, Scope.READ_ACL, user);
        EntityTag etag = user == null ? null : new EntityTag(aclEtag(user), true);
        if (ifNoneMatch.isValid() && etag != null && ifNoneMatch.matches(etag)) {
            return Response.notModified(etag).build();
        }

        return Response.ok()
                .entity(listShares(user, version, token))
                .tag(etag)
                .build();
    }

    @Since("1.3")
    @GET
    @Path("/{email}/groups")
    public Response listGroups(@Auth IUserAuthToken token, @PathParam("email") User user)
            throws ExNotFound, SQLException
    {
        requirePermission(Scope.READ_USER, token);
        User caller = _factUser.create(token.user());
        throwIfNotSelfOrTSOf(caller, user);

        ImmutableCollection<com.aerofs.rest.api.Group> groups = listGroups(user, token);

        return Response.ok()
                .entity(groups)
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{email}/invitations")
    public Response listInvitations(@Auth IAuthToken token,
                                    @PathParam("email") User user)
            throws ExNotFound, SQLException
    {
        validateAuth(token, Scope.MANAGE_INVITATIONS, user);

        return Response.ok()
                .entity(listInvitations(user, token))
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{email}/invitations/{sid}")
    public Response getInvitation(@Auth IAuthToken token,
                                  @PathParam("email") User user,
                                  @PathParam("sid") SharedFolder sf)
            throws ExNotFound, SQLException
    {
        validateAuth(token, Scope.MANAGE_INVITATIONS, user);

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
        _sfNotif.begin();
        ImmutableCollection<UserID> affected = sf.setState(user, SharedFolderState.JOINED);
        sf.setExternal(user, toBool(external));

        _aclPublisher.publish_(affected);
        _sfPublisher.sendNotifications(_sfNotif.get());
        _sfNotif.clear();

        String[] permissions = sf.getPermissions(user).toArray();

        audit(caller, token, AuditTopic.SHARING, "folder.join")
                .embed("folder", new AuditFolder(sf.id(), sf.getName(caller)))
                .add("target", user.id())
                .embed("role", permissions)
                .publish();

        _analyticsClient.track(AnalyticsEvent.FOLDER_INVITATION_ACCEPT, user.id());

        String location = Service.DUMMY_LOCATION
                + 'v' + version
                + "/shares/" + sf.id().toStringFormal();

        return Response.created(URI.create(location))
                .entity(new com.aerofs.rest.api.SharedFolder(sf.id().toStringFormal(),
                        sf.getName(user), listMembers(sf),
                        version.compareTo(GroupResource.FIRST_GROUP_API_VERSION) >= 0 ? listGroupMembers(sf) : null,
                        listPendingMembers(sf), sf.isExternal(user), permissions, sf.isLocked()))
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
            checkState(sf.removeIndividualUser(user).isEmpty());
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

    static ImmutableCollection<com.aerofs.rest.api.SharedFolder> listShares(User user, Version version, IAuthToken token)
            throws ExNotFound, SQLException
    {
        ImmutableList.Builder<com.aerofs.rest.api.SharedFolder> bd = ImmutableList.builder();
        for (SharedFolder sf : user.getJoinedFolders()) {
            // filter out root store
            if (sf.id().isUserRoot()) continue;
            if (token.hasFolderPermission(Scope.READ_ACL, sf.id())) {
                bd.add(new com.aerofs.rest.api.SharedFolder(
                        sf.id().toStringFormal(), sf.getName(user),
                        listMembers(sf),
                        version.compareTo(GroupResource.FIRST_GROUP_API_VERSION) >= 0 ? listGroupMembers(sf) : null,
                        listPendingMembers(sf),
                        sf.isExternal(user), sf.getPermissions(user).toArray(), sf.isLocked()));
            }
        }
        return bd.build();
    }

    static ImmutableCollection<com.aerofs.rest.api.Group> listGroups(User user, IAuthToken token)
            throws SQLException, ExNotFound
    {
        requirePermission(Scope.READ_GROUPS, token);
        ImmutableList.Builder<com.aerofs.rest.api.Group> bd = ImmutableList.builder();
        for (Group group : user.getGroups()) {
            bd.add(new com.aerofs.rest.api.Group(group.id().getString(), group.getCommonName(),
                    GroupResource.listMembersFor(group)));
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
        checkArgument(attrs != null, "Request body missing");
        checkArgument(attrs.email != null, "Request body missing required field: email");
        checkArgument(attrs.firstName != null, "Request body missing required field: first_name");
        checkArgument(attrs.lastName != null, "Request body missing required field: last_name");

        User caller = _factUser.create(auth.user());
        User newUser = _factUser.createFromExternalID(attrs.email);

        // We are using "Team Server"-ness as a simple way to distinguish elevated callers.
        if (!caller.id().isTeamServerID()) {
            return Response.status(Status.FORBIDDEN)
                    .entity("Insufficient admin privilege to create a user")
                    .build();
        }

        if (newUser.exists()) {
            return Response.status(Status.CONFLICT)
                    .entity("User cannot be created")
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
                        attrs.lastName, listShares(newUser, version, auth),
                        listInvitations(newUser, auth)))
                .build();
    }

    @Since("1.1")
    @PUT
    @Path("/{email}")
    public Response update(
            @Auth IUserAuthToken auth,
            @Context Version version,
            @PathParam("email") User target,
            com.aerofs.rest.api.User attrs) throws Exception
    {
        requirePermission(Scope.WRITE_USER, auth);
        checkArgument(attrs.firstName != null, "Request body missing required field: first_name");
        checkArgument(attrs.lastName != null, "Request body missing required field: last_name");

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
                        attrs.lastName, listShares(target, version, auth),
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
        _sfNotif.begin();
        UserManagement.deactivateByTS(caller, target, false, _commandDispatcher, _aclPublisher);
        l.info("Deleted user {}", target.id().getString());

        _sfPublisher.sendNotifications(_sfNotif.get());
        _sfNotif.clear();

        audit(caller, auth, AuditTopic.USER, "user.delete")
                .add("email", target.id())
                .publish();

        _analyticsClient.track(AnalyticsEvent.USER_DELETE);

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
    public Response getQuota(@Auth IAuthToken token, @PathParam("email") User user)
            throws SQLException, ExNotFound
    {
        validateAuth(token, Scope.READ_USER, user);

        return Response.ok()
                .entity(new Quota(
                        user.getBytesUsed(),
                        user.getOrganization().getQuotaPerUser()))
                .build();
    }

    private class TwoFactor
    {
        @SuppressWarnings("unused")
        public final Boolean enforce;

        public TwoFactor(Boolean enforce) {
            this.enforce = enforce;
        }
    }

    @Since("1.3")
    @GET
    @Path("/{email}/two_factor")
    public Response getTwoFactorEnabled(@Auth IAuthToken token, @PathParam("email") User user)
            throws SQLException, ExNotFound
    {
        validateAuth(token, Scope.READ_USER, user);

        return Response.ok()
                .entity(new TwoFactor(user.shouldEnforceTwoFactor()))
                .build();
    }

    @Since("1.3")
    @DELETE
    @Path("/{email}/two_factor")
    public Response disableTwoFactor(@Auth IUserAuthToken auth,
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

    private Collection<com.aerofs.rest.api.User> createUsersList(Collection<UserID> userIDs) throws SQLException, ExNotFound
    {
        ImmutableList.Builder<com.aerofs.rest.api.User> builder = ImmutableList.builder();
        for (UserID userID : userIDs) {
            FullName fullName = _factUser.create(userID).getFullName();
            builder.add(new com.aerofs.rest.api.User(userID.getString(), fullName._first, fullName._last, null, null));
        }
        return builder.build();
    }
}
