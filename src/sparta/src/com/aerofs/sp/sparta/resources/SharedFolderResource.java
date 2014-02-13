/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Version;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.rest.api.*;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.aerofs.rest.util.AuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.util.EntityTagSet;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.ACLNotificationPublisher;
import com.aerofs.sp.server.InvitationHelper;
import com.aerofs.sp.server.UserManagement;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.email.SharedFolderNotificationEmailer;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.SharedFolder.UserPermissionsAndState;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.sharing_rules.ISharingRules;
import com.aerofs.sp.server.sharing_rules.SharingRulesFactory;
import com.aerofs.sp.sparta.Transactional;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkState;

/**
 *
 * GET    /{sid}                    get info
 * GET    /{sid}/members            list members
 * POST   /{sid}/members            add member [admin only, bypass invitation]
 * GET    /{sid}/members/{email}    get permissions
 * PUT    /{sid}/members/{email}    set permissions
 * DELETE /{sid}/members/{email}    remove member (or leave, if self)
 * GET    /{sid}/invited            list pending members
 * POST   /{sid}/invited            invite new member
 * GET    /{sid}/invited/{email}    get permissions and inviter
 * DELETE /{sid}/invited/{email}    revoke invitation
 */
@Path(Service.VERSION + "/shares")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class SharedFolderResource
{
    private final User.Factory _factUser;
    private final SharingRulesFactory _sharingRules;
    private final InvitationHelper _invitationHelper;
    private final SharedFolderNotificationEmailer _sfnEmailer;
    private final ACLNotificationPublisher _aclNotifier;
    private final AuditClient _audit;

    @Inject
    public SharedFolderResource(SharingRulesFactory sharingRules, User.Factory factUser,
            InvitationHelper invitationHelper, ACLNotificationPublisher aclNotifier,
            SharedFolderNotificationEmailer sfnEmailer, AuditClient audit)
    {
        _factUser = factUser;
        _sharingRules = sharingRules;
        _invitationHelper = invitationHelper;
        _aclNotifier = aclNotifier;
        _sfnEmailer = sfnEmailer;
        _audit = audit;
    }

    @Since("1.1")
    @GET
    @Path("/{id}")
    public Response get(@Auth AuthToken token,
            @PathParam("id") SharedFolder sf,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
            throws SQLException, ExNotFound
    {
        User caller = _factUser.create(token.user);
        throwIfNotAMember(sf, caller, "No such shared folder");

        MessageDigest md = BaseSecUtil.newMessageDigestMD5();
        List<PendingMember> pending = listPendingMembers(sf, md);
        // TODO: it'd be nice if there was an epoch for pending members to avoid this hashing...
        EntityTag etag = new EntityTag(aclEtag(caller) + BaseUtil.hexEncode(md.digest()), true);
        if (ifNoneMatch.isValid() && ifNoneMatch.matches(etag)) {
            return Response.notModified(etag).build();
        }

        return Response.ok()
                .entity(new com.aerofs.rest.api.SharedFolder(sf.id().toStringFormal(),
                        sf.getName(caller), listMembers(sf), pending))
                .tag(etag)
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{id}/members")
    public Response listMembers(@Auth AuthToken token,
            @PathParam("id") SharedFolder sf,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
            throws SQLException, ExNotFound
    {
        User caller = _factUser.create(token.user);
        throwIfNotAMember(sf, caller, "No such shared folder");

        // TODO: consider more robust etag
        EntityTag etag = new EntityTag(aclEtag(caller), true);
        if (ifNoneMatch.isValid() && ifNoneMatch.matches(etag)) {
            return Response.notModified(etag).build();
        }

        return Response.ok()
                .entity(listMembers(sf))
                .tag(etag)
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{id}/members/{email}")
    public Response getMember(@Auth AuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("email") User member,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
            throws ExBadArgs, ExNotFound, SQLException
    {
        User caller = _factUser.create(token.user);
        throwIfNotAMember(sf, caller, "No such shared folder");
        Permissions p = throwIfNotAMember(sf, member, "No such member");

        // TODO: consider more robust etag
        EntityTag etag = new EntityTag(aclEtag(member), true);
        if (ifNoneMatch.isValid() && ifNoneMatch.matches(etag)) {
            return Response.notModified(etag).build();
        }

        return Response.ok()
                .entity(toMember(member, p))
                .tag(etag)
                .build();
    }

    @Since("1.1")
    @POST
    @Path("/{id}/members")
    public Response addMember(@Auth AuthToken token,
            @PathParam("id") SharedFolder sf,
            @Context Version version,
            Member member)
            throws Exception
    {
        User caller = _factUser.create(token.user);
        User user;
        try {
            user = _factUser.create(UserID.fromExternal(member.email));
        } catch (ExEmptyEmailAddress e) {
            throw new ExBadArgs("Invalid member email");
        }
        throwIfNotAMember(sf, caller, "No such shared folder");

        if (!user.exists()) throw new ExNotFound("No such user");

        // TODO oauth scopes
        sf.throwIfNoPrivilegeToChangeACL(caller);
        if (!UserManagement.isSelfOrTSOf(caller, user)) {
            throw new ExNoPerm("Not allowed to bypass invitation process");
        }

        boolean exists = false;
        if (sf.getPermissionsNullable(user) != null) {
            if (sf.getStateNullable(user) == SharedFolderState.JOINED) {
                throw new WebApplicationException(Response.status(Status.CONFLICT)
                        .entity(new Error(Type.CONFLICT, "Member already exists"))
                        .build());
            }
            exists = true;
        }

        ISharingRules rules = _sharingRules.create(caller);
        Permissions req = rules.onUpdatingACL(sf, user, Permissions.fromArray(member.permissions));

        if (exists) {
            sf.setState(user, SharedFolderState.JOINED);
            sf.setPermissions(user, req);
        } else {
            sf.addJoinedUser(user, req);
        }

        _aclNotifier.publish_(sf.getJoinedUserIDs());

        _audit.event(AuditTopic.SHARING, "folder.add")
                .add("folder", sf.getName(user))
                .add("sharer", caller.id())
                .add("target", user.id())
                .embed("role", req.toArray())
                .publish();

        // TODO: outside transaction
        _sfnEmailer.sendRoleChangedNotificationEmail(sf, caller, user, Permissions.allOf(), req);

        String location = Service.DUMMY_LOCATION
                + 'v' + version
                + "/shares/" + sf.id().toStringFormal()
                + "/members/" + member.email;

        return Response.created(URI.create(location))
                .entity(toMember(user, req))
                .build();
    }

    @Since("1.1")
    @PUT
    @Path("/{id}/members/{email}")
    public Response updateMember(@Auth AuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("email") User user,
            @HeaderParam(Names.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            Member member)
            throws Exception
    {
        User caller = _factUser.create(token.user);
        throwIfNotAMember(sf, caller, "No such shared folder");
        Permissions oldPermissions = throwIfNotAMember(sf, user, "No such member");

        // TODO: oauth scopes
        sf.throwIfNoPrivilegeToChangeACL(caller);

        // TODO: consider more robust etag
        EntityTag etag = new EntityTag(aclEtag(user), true);
        if (ifMatch.isValid() && !ifMatch.matches(etag)) {
            return Response.status(Status.PRECONDITION_FAILED)
                    .tag(etag)
                    .build();
        }

        ISharingRules rules = _sharingRules.create(user);
        Permissions req = rules.onUpdatingACL(sf, user, Permissions.fromArray(member.permissions));

        ImmutableCollection<UserID> affected;
        try {
            affected = sf.setPermissions(user, req);
        } catch (ExNoAdminOrOwner e) {
            throw new ExBadArgs(e.getMessage());
        }

        // NB: ignore sharing rules warnings for now
        // rules.throwIfAnyWarningTriggered();

        _aclNotifier.publish_(affected);
        _audit.event(AuditTopic.SHARING, "folder.permission.update")
                .add("target_user", user.id())
                .add("admin_user", caller.id())
                .embed("new_role", req.toArray())
                .embed("old_role", oldPermissions != null ? oldPermissions.toArray() : "")
                .add("folder_id", sf.id())
                .add("folder_name", sf.getName(caller))
                .publish();

        // TODO: outside transaction
        _sfnEmailer.sendRoleChangedNotificationEmail(sf, caller, user, oldPermissions, req);

        return Response.ok()
                .entity(toMember(user, req))
                .tag(new EntityTag(aclEtag(user), true))
                .build();
    }

    @Since("1.1")
    @DELETE
    @Path("/{id}/members/{email}")
    public Response removeMember(@Auth AuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("email") User user,
            @HeaderParam(Names.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch)
            throws Exception
    {
        User caller = _factUser.create(token.user);
        throwIfNotAMember(sf, caller, "No such shared folder");

        // TODO: oauth scopes
        if (!caller.equals(user)) sf.throwIfNoPrivilegeToChangeACL(caller);

        EntityTag etag = new EntityTag(aclEtag(user), true);
        if (ifMatch.isValid() && !ifMatch.matches(etag)) {
            return Response.status(Status.PRECONDITION_FAILED)
                    .tag(etag)
                    .build();
        }

        ImmutableCollection<UserID> affected;
        try {
            affected = caller.equals(user)
                    ? sf.setState(user, SharedFolderState.LEFT)
                    : sf.removeUser(user);
        } catch (ExNoAdminOrOwner e) {
            throw new ExBadArgs(e.getMessage());
        }

        _aclNotifier.publish_(affected);
        _audit.event(AuditTopic.SHARING, "folder.permission.delete")
                .add("target_user", user.id())
                .add("admin_user", caller.id())
                .add("folder_id", sf.id())
                .add("folder_name", sf.getName(caller))
                .publish();

        return Response.noContent()
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{id}/pending")
    public Response listPendingMembers(@Auth AuthToken token,
            @PathParam("id") SharedFolder sf,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
            throws SQLException, ExNotFound
    {
        User caller = _factUser.create(token.user);
        throwIfNotAMember(sf, caller, "No such shared folder");


        MessageDigest md = BaseSecUtil.newMessageDigestMD5();
        List<PendingMember> pending = listPendingMembers(sf, md);
        // TODO: it'd be nice if there was an epoch for pending members to avoid this hashing...
        EntityTag etag = new EntityTag(aclEtag(caller) + BaseUtil.hexEncode(md.digest()), true);
        if (ifNoneMatch.isValid() && ifNoneMatch.matches(etag)) {
            return Response.notModified(etag).build();
        }

        return Response.ok()
                .entity(pending)
                .tag(etag)
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{id}/pending/{email}")
    public Response getPendingMember(@Auth AuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("email") User user)
            throws SQLException, ExNotFound
    {
        User caller = _factUser.create(token.user);
        throwIfNotAMember(sf, caller, "No such shared folder");

        Permissions p = sf.getPermissionsNullable(user);
        if (p == null  || sf.getStateNullable(user) != SharedFolderState.PENDING) {
            throw new ExNotFound("No such pending member");
        }

        return Response.ok()
                .entity(toPendingMember(user, p, sf.getSharerNullable(user)))
                .build();
    }

    @Since("1.1")
    @POST
    @Path("/{id}/pending")
    public Response inviteMember(@Auth AuthToken token,
            @PathParam("id") SharedFolder sf,
            @Context Version version,
            PendingMember invitee)
            throws Exception
    {
        User caller = _factUser.create(token.user);
        throwIfNotAMember(sf, caller, "No such shared folder");
        User user;
        try {
            user = _factUser.create(UserID.fromExternal(invitee.email));
        } catch (Exception e) {
            throw new ExBadArgs("Invalid invitee email");
        }

        // TODO: oauth scopes
        sf.throwIfNoPrivilegeToChangeACL(caller);

        String folderName = sf.getName(caller);
        ImmutableCollection<UserID> users = sf.getJoinedUserIDs();

        ISharingRules rules = _sharingRules.create(user);
        Permissions req = rules.onUpdatingACL(sf, user, Permissions.fromArray(invitee.permissions));

        InvitationEmailer em = _invitationHelper.createFolderInvitationAndEmailer(sf, caller, user,
                req, invitee.note, folderName);

        // NB: ignore sharing rules warnings for now
        // rules.throwIfAnyWarningTriggered();
        if (rules.shouldBumpEpoch()) _aclNotifier.publish_(users);

        _audit.event(AuditTopic.SHARING, "folder.invite")
                .add("folder", folderName)
                .add("sharer", caller.id())
                .add("target", user.id())
                .embed("role", req.toArray())
                .publish();

        // TODO: call emailer outside of transaction to mimic SPService?
        em.send();

        String location = Service.DUMMY_LOCATION
                + 'v' + version
                + "/shares/" + sf.id().toStringFormal()
                + "/pending/" + invitee.email;

        return Response.created(URI.create(location))
                .entity(toPendingMember(user, req, sf.getSharerNullable(user)))
                .tag(new EntityTag(aclEtag(user), true))
                .build();
    }

    @Since("1.1")
    @DELETE
    @Path("/{id}/pending/{email}")
    public Response revokeInvitation(@Auth AuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("email") User user)
            throws SQLException, ExNotFound, ExNoPerm, ExBadArgs
    {
        User caller = _factUser.create(token.user);
        throwIfNotAMember(sf, caller, "No such shared folder");
        sf.throwIfNoPrivilegeToChangeACL(caller);

        Permissions p = sf.getPermissionsNullable(user);
        if (p == null  || sf.getStateNullable(user) != SharedFolderState.PENDING) {
            throw new ExNotFound("No such pending member");
        }

        try {
            checkState(sf.removeUser(user).isEmpty());
        } catch (ExNoAdminOrOwner e) {
            throw new ExBadArgs(e.getMessage());
        }

        return Response.noContent()
                .build();
    }

    static String aclEtag(User user) throws SQLException
    {
        return String.format("%08x", user.getACLEpoch());
    }

    static Permissions throwIfNotAMember(SharedFolder sf, User caller, String message)
            throws SQLException, ExNotFound
    {
        Permissions p = sf.getPermissionsNullable(caller);
        if (p == null) throw new ExNotFound(message);
        return p;
    }

    static Member toMember(User u, Permissions p) throws ExNotFound, SQLException
    {
        FullName name = u.getFullName();
        return new Member(u.id().getString(), name._first, name._last, p.toArray());
    }

    static List<Member> listMembers(SharedFolder sf) throws ExNotFound, SQLException
    {
        ImmutableMap<User, Permissions> users = sf.getJoinedUsersAndRoles();
        List<Member> members = Lists.newArrayListWithExpectedSize(users.size());
        for (Entry<User, Permissions> e : users.entrySet()) {
            // filter out TS users
            if (e.getKey().id().isTeamServerID()) continue;
            members.add(toMember(e.getKey(), e.getValue()));
        }
        return members;
    }

    static PendingMember toPendingMember(User u, Permissions p, User sharer)
            throws ExNotFound, SQLException
    {
        FullName n = u.exists() ? u.getFullName() : null;
        return new PendingMember(u.id().getString(),
                n != null ? n._first : null,
                n != null ? n._last : null,
                sharer.id().getString(),
                p.toArray());
    }

    static List<PendingMember> listPendingMembers(SharedFolder sf, MessageDigest md)
            throws ExNotFound, SQLException
    {
        List<PendingMember> members = Lists.newArrayList();
        for (UserPermissionsAndState ups : sf.getAllUsersRolesAndStates()) {
            if (ups._state != SharedFolderState.PENDING) continue;
            // filter out TS users
            if (ups._user.id().isTeamServerID()) continue;
            PendingMember pm = toPendingMember(ups._user, ups._permissions,
                    sf.getSharerNullable(ups._user));
            members.add(pm);
            if (md != null) digest(pm, md);
        }
        return members;
    }

    private static void digest(PendingMember pm, MessageDigest md)
    {
        md.update(BaseUtil.string2utf(pm.email));
        for (String p : pm.permissions) md.update(BaseUtil.string2utf(p));
    }
}
