/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.audit.client.AuditClient.AuditableEvent;
import com.aerofs.base.C;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.rest.auth.IAuthToken;
import com.aerofs.base.id.GroupID;
import com.aerofs.restless.Version;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.rest.api.*;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.aerofs.rest.auth.IUserAuthToken;
import com.aerofs.oauth.Scope;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.util.EntityTagSet;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.ACLNotificationPublisher;
import com.aerofs.sp.server.InvitationHelper;
import com.aerofs.sp.server.UserManagement;
import com.aerofs.sp.server.audit.AuditCaller;
import com.aerofs.sp.server.audit.AuditFolder;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.email.SharedFolderNotificationEmailer;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.group.Group.AffectedUserIDsAndInvitedUsers;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.sf.SharedFolder.AffectedAndNeedsEmail;
import com.aerofs.sp.server.lib.sf.SharedFolder.GroupPermissions;
import com.aerofs.sp.server.lib.sf.SharedFolder.UserPermissionsAndState;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.sharing_rules.ISharingRules;
import com.aerofs.sp.server.sharing_rules.SharingRulesFactory;
import com.aerofs.sp.server.url_sharing.UrlShare;
import com.aerofs.sp.sparta.Transactional;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;

import javax.annotation.Nullable;
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
import java.sql.SQLException;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@Path(Service.VERSION + "/shares")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class SharedFolderResource extends AbstractSpartaResource
{
    private final User.Factory _factUser;
    private final Group.Factory _factGroup;
    private final SharedFolder.Factory _factSF;
    private final UrlShare.Factory _factUrlShare;
    private final SharingRulesFactory _sharingRules;
    private final InvitationHelper _invitationHelper;
    private final SharedFolderNotificationEmailer _sfnEmailer;
    private final ACLNotificationPublisher _aclNotifier;
    private final AuditClient _audit;

    @Inject
    public SharedFolderResource(User.Factory factUser, SharedFolder.Factory factSF, Group.Factory factGroup,
            UrlShare.Factory factUrlShare, SharingRulesFactory sharingRules,
            InvitationHelper invitationHelper, ACLNotificationPublisher aclNotifier,
            SharedFolderNotificationEmailer sfnEmailer, AuditClient audit)
    {
        _factUser = factUser;
        _factGroup = factGroup;
        _factSF = factSF;
        _factUrlShare = factUrlShare;
        _sharingRules = sharingRules;
        _invitationHelper = invitationHelper;
        _aclNotifier = aclNotifier;
        _sfnEmailer = sfnEmailer;
        _audit = audit;
    }

    private AuditableEvent audit(SharedFolder sf, User caller, IUserAuthToken token, String event)
            throws SQLException, ExNotFound
    {
        return _audit.event(AuditTopic.SHARING, event)
                .embed("folder", new AuditFolder(sf.id(), sf.getName(caller)))
                .embed("caller", new AuditCaller(caller.id(), token.issuer(), token.uniqueId()));
    }

    private @Nullable User validateAuth(IAuthToken token, Scope scope, SharedFolder sf)
            throws SQLException, ExNotFound
    {
        requirePermissionOnFolder(scope, token, sf.id());
        if (token instanceof IUserAuthToken) {
            User caller = _factUser.create(((IUserAuthToken)token).user());
            throwIfNotAMember(sf, caller, "No such shared folder");
            return caller;
        }
        return null;
    }

    @Since("1.1")
    @GET
    @Path("/{id}")
    public Response get(@Auth IAuthToken token, @Context Version version, @PathParam("id") SharedFolder sf)
            throws SQLException, ExNotFound
    {
        User caller = validateAuth(token, Scope.READ_ACL, sf);
        Permissions callerPermissions = caller == null ? null : sf.getPermissionsNullable(caller);

        return Response.ok()
                .entity(new com.aerofs.rest.api.SharedFolder(sf.id().toStringFormal(),
                        sf.getName(caller), listMembers(sf),
                        version.compareTo(GroupResource.FIRST_GROUP_API_VERSION) >= 0 ? listGroupMembers(sf) : null,
                        listPendingMembers(sf),
                        caller == null ? null : sf.isExternal(caller),
                        callerPermissions == null ? null : callerPermissions.toArray()))
                .build();
    }

    @Since("1.1")
    @POST
    public Response create(@Auth IUserAuthToken token,
            @Context Version version,
            com.aerofs.rest.api.SharedFolder share)
            throws Exception
    {
        checkArgument(share.name != null, "Request body missing required field: name");
        User caller = _factUser.create(token.user());

        // in the unlikely event that we generate a UUID that's already taken,
        // retry up to 10 times before returning a 5xx
        int attempts = 10;
        SharedFolder sf;
        do {
            sf = _factSF.create(SID.generate());
        } while (sf.exists() && --attempts > 0);

        ImmutableCollection<UserID> affected = sf.save(share.name, caller);
        if (share.isExternal != null) sf.setExternal(caller, share.isExternal);
        _aclNotifier.publish_(affected);

        audit(sf, caller, token, "folder.create")
                .publish();

        String location = Service.DUMMY_LOCATION
                + 'v' + version
                + "/shares/" + sf.id().toStringFormal();

        return Response.created(URI.create(location))
                .entity(new com.aerofs.rest.api.SharedFolder(sf.id().toStringFormal(), sf.getName(caller), listMembers(sf),
                        version.compareTo(GroupResource.FIRST_GROUP_API_VERSION) >= 0 ? listGroupMembers(sf) : null,
                        listPendingMembers(sf), sf.isExternal(caller), Permissions.OWNER.toArray()))
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{id}/members")
    public Response listMembers(@Auth IAuthToken token,
            @PathParam("id") SharedFolder sf,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
            throws SQLException, ExNotFound
    {
        User caller = validateAuth(token, Scope.READ_ACL, sf);

        EntityTag etag = null;
        if (caller != null) {
            throwIfNotAMember(sf, caller, "No such shared folder");

            // TODO: consider more robust etag
            etag = new EntityTag(aclEtag(caller), true);
            if (ifNoneMatch.isValid() && ifNoneMatch.matches(etag)) {
                return Response.notModified(etag).build();
            }
        }

        return Response.ok()
                .entity(listMembers(sf))
                .tag(etag)
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{id}/members/{email}")
    public Response getMember(@Auth IAuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("email") User member,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
            throws ExBadArgs, ExNotFound, SQLException
    {
        validateAuth(token, Scope.READ_ACL, sf);
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
    public Response addMember(@Auth IUserAuthToken token,
            @PathParam("id") SharedFolder sf,
            @Context Version version,
            SFMember member)
            throws Exception
    {
        User caller = validateAuth(token, Scope.WRITE_ACL, sf);
        User user;
        try {
            user = _factUser.create(UserID.fromExternal(member.email));
        } catch (ExInvalidID e) {
            throw new ExBadArgs("Invalid member email");
        }

        if (!user.exists()) throw new ExNotFound("No such user");

        sf.throwIfNoPrivilegeToChangeACL(caller);
        if (!UserManagement.isSelfOrTSOf(caller, user)) {
            throw new ExNoPerm("Not allowed to bypass invitation process");
        }

        boolean exists = false;
        if (sf.getPermissionsNullable(user) != null) {
            if (sf.getStateNullable(user) == SharedFolderState.JOINED) {
                return Response.status(Status.CONFLICT)
                        .entity(new Error(Type.CONFLICT, "Member already exists"))
                        .build();
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

        audit(sf, caller, token, "folder.add")
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
    public Response updateMember(@Auth IUserAuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("email") User user,
            @HeaderParam(Names.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            SFMember member)
            throws Exception
    {
        requirePermissionOnFolder(Scope.WRITE_ACL, token, sf.id());
        User caller = _factUser.create(token.user());
        throwIfNotAMember(sf, caller, "No such shared folder");
        Permissions oldPermissions = throwIfNotAMember(sf, user, "No such member");

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

        audit(sf, caller, token, "folder.permission.update")
                .add("target", user.id())
                .embed("new_role", req.toArray())
                .embed("old_role", oldPermissions != null ? oldPermissions.toArray() : "")
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
    public Response removeMember(@Auth IUserAuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("email") User user,
            @HeaderParam(Names.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch)
            throws Exception
    {
        requirePermissionOnFolder(Scope.WRITE_ACL, token, sf.id());
        User caller = _factUser.create(token.user());
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
                    : sf.removeIndividualUser(user);
        } catch (ExNoAdminOrOwner e) {
            throw new ExBadArgs(e.getMessage());
        }

        _aclNotifier.publish_(affected);
        audit(sf, caller, token, caller.equals(user) ? "folder.leave" : "folder.permission.delete")
                .add("target", user.id())
                .publish();

        return Response.noContent()
                .build();
    }

    @Since("1.3")
    @GET
    @Path("/{id}/groups")
    public Response getGroupMembers(@Auth IAuthToken token,
            @PathParam("id") SharedFolder sf,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
            throws Exception
    {
        validateAuth(token, Scope.READ_ACL, sf);

        return Response.ok()
                .entity(listGroupMembers(sf))
                .build();
    }

    @Since("1.3")
    @POST
    @Path("/{id}/groups")
    public Response addGroupMember(@Auth IUserAuthToken token,
            @PathParam("id") SharedFolder sf,
            @Context Version version,
            SFGroupMember groupMember)
            throws Exception
    {
        User caller = validateAuth(token, Scope.WRITE_ACL, sf);

        Group group = _factGroup.create(GroupID.fromExternal(groupMember.id));
        if (!group.exists()) throw new ExNotFound("No such group");

        sf.throwIfNoPrivilegeToChangeACL(caller);

        if (group.inSharedFolder(sf)) {
            return Response.status(Status.CONFLICT)
                    .entity(new Error(Type.CONFLICT, "Member already exists"))
                    .build();
        }

        ISharingRules rules = _sharingRules.create(caller);
        Permissions req = rules.onUpdatingACL(sf, group, Permissions.fromArray(groupMember.permissions));


        AffectedUserIDsAndInvitedUsers result = group.joinSharedFolder(sf,
                Permissions.fromArray(groupMember.permissions), caller);

        _aclNotifier.publish_(result._affected);

        for (User invitee : group.listMembers()) {
            audit(sf, caller, token, "folder.add")
                    .add("target", invitee.id())
                    .embed("role", req.toArray())
                    .publish();
        }

        // TODO: outside transaction
        _sfnEmailer.sendRoleChangedNotificationEmail(sf, caller, group, Permissions.allOf(), req);

        String location = Service.DUMMY_LOCATION
                + 'v' + version
                + "/shares/" + sf.id().toStringFormal()
                + "/groups/" + groupMember.id;

        // TODO: etags
        return Response.created(URI.create(location))
                .entity(toGroupMember(group, req))
                .build();
    }

    @Since("1.3")
    @GET
    @Path("/{id}/groups/{groupid}")
    public Response getGroupMember(@Auth IAuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("groupid") Group group)
            throws SQLException, ExNotFound
    {
        validateAuth(token, Scope.READ_ACL, sf);
        if (!group.inSharedFolder(sf)) {
            throw new ExNotFound("No such member");
        }

        // TODO: etags
        return Response.ok()
                .entity(toGroupMember(group, group.getRoleForSharedFolder(sf)))
                .build();
    }

    @Since("1.3")
    @PUT
    @Path("/{id}/groups/{groupid}")
    public Response updateGroupMember(@Auth IUserAuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("groupid") Group group,
            SFGroupMember groupMember)
            throws Exception
    {
        User caller = validateAuth(token, Scope.WRITE_ACL, sf);
        if (!group.inSharedFolder(sf)) {
            throw new ExNotFound("No such member");
        }

        sf.throwIfNoPrivilegeToChangeACL(caller);

        // TODO: etags
        ISharingRules rules = _sharingRules.create(caller);
        Permissions req = rules.onUpdatingACL(sf, group, Permissions.fromArray(groupMember.permissions));
        Permissions oldPermissions = group.getRoleForSharedFolder(sf);

        ImmutableCollection<UserID> affected;
        try {
            affected = group.changeRoleInSharedFolder(sf, req);
        } catch (ExNoAdminOrOwner e) {
            throw new ExBadArgs(e.getMessage());
        }

        // NB: ignore sharing rules warnings for now
        // rules.throwIfAnyWarningTriggered();

        _aclNotifier.publish_(affected);

        audit(sf, caller, token, "folder.permission.update")
                .add("target", group.id())
                .embed("new_role", req.toArray())
                .embed("old_role", oldPermissions.toArray())
                .publish();

        // TODO: outside transaction
        _sfnEmailer.sendRoleChangedNotificationEmail(sf, caller, group, oldPermissions, req);

        return Response.ok()
                .entity(toGroupMember(group, req))
                .build();
    }

    @Since("1.3")
    @DELETE
    @Path("/{id}/groups/{groupid}")
    public Response deleteGroupMember(@Auth IUserAuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("groupid") Group group)
            throws Exception
    {
        requirePermissionOnFolder(Scope.WRITE_ACL, token, sf.id());
        User caller = _factUser.create(token.user());
        throwIfNotAMember(sf, caller, "No such shared folder");
        if (!group.inSharedFolder(sf)) {
            throw new ExNotFound("No such member");
        }

        sf.throwIfNoPrivilegeToChangeACL(caller);

        // TODO: etags?
        ImmutableCollection<UserID> affected;
        try {
            affected = group.deleteSharedFolder(sf);
        } catch (ExNoAdminOrOwner e) {
            throw new ExBadArgs(e.getMessage());
        }

        _aclNotifier.publish_(affected);
        audit(sf, caller, token, "folder.permission.delete")
                .add("target", group.id())
                .publish();

        return Response.noContent()
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{id}/pending")
    public Response listPendingMembers(@Auth IAuthToken token,
            @PathParam("id") SharedFolder sf)
            throws SQLException, ExNotFound
    {
        validateAuth(token, Scope.READ_ACL, sf);

        return Response.ok()
                .entity(listPendingMembers(sf))
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{id}/pending/{email}")
    public Response getPendingMember(@Auth IAuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("email") User user)
            throws SQLException, ExNotFound
    {
        validateAuth(token, Scope.READ_ACL, sf);

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
    public Response inviteMember(@Auth IUserAuthToken token,
            @PathParam("id") SharedFolder sf,
            @Context Version version,
            SFPendingMember invitee)
            throws Exception
    {
        requirePermissionOnFolder(Scope.WRITE_ACL, token, sf.id());
        User caller = _factUser.create(token.user());
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
        ImmutableCollection.Builder<UserID> affected = ImmutableSet.builder();

        ISharingRules rules = _sharingRules.create(user);
        Permissions req = rules.onUpdatingACL(sf, user, Permissions.fromArray(invitee.permissions));

        InvitationEmailer em = _invitationHelper.doesNothing();
        AffectedAndNeedsEmail updates = sf.addUserWithGroup(user, null, req,
                caller);
        affected.addAll(updates._affected);
        if (updates._needsEmail) {
            em = _invitationHelper.createFolderInvitationAndEmailer(sf, caller,
                    user, req, invitee.note, folderName);
        }

        // NB: ignore sharing rules warnings for now
        // rules.throwIfAnyWarningTriggered();
        if (rules.shouldBumpEpoch()) {
            affected.addAll(sf.getJoinedUserIDs());
        }
        _aclNotifier.publish_(affected.build());

        audit(sf, caller, token, "folder.invite")
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
    public Response revokeInvitation(@Auth IUserAuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("email") User user)
            throws SQLException, ExNotFound, ExNoPerm, ExBadArgs
    {
        requirePermissionOnFolder(Scope.WRITE_ACL, token, sf.id());
        User caller = _factUser.create(token.user());
        throwIfNotAMember(sf, caller, "No such shared folder");
        sf.throwIfNoPrivilegeToChangeACL(caller);

        Permissions p = sf.getPermissionsNullable(user);
        if (p == null  || sf.getStateNullable(user) != SharedFolderState.PENDING) {
            throw new ExNotFound("No such pending member");
        }

        audit(sf, caller, token, "folder.delete_invitation")
                .add("target", user.id())
                .publish();

        try {
            checkState(sf.removeIndividualUser(user).isEmpty());
        } catch (ExNoAdminOrOwner e) {
            throw new ExBadArgs(e.getMessage());
        }

        return Response.noContent()
                .build();
    }

    /**
     * NB: this route is currently undocumented
     *
     * This is intended to be temporary. Eventually we should figure out acceptable semantics and
     * document them in the public API docs.
     *
     * see ENG-2315
     */
    @Since("1.3")
    @GET
    @Path("/{id}/urls")
    public Response listURLs(@Auth IAuthToken token,
            @PathParam("id") SharedFolder sf)
            throws ExNotFound, ExNoPerm, SQLException
    {
        User caller = validateAuth(token, Scope.READ_ACL, sf);

        // FIXME: that seems overly restrictive
        // surely it would be good for users to know which files have been shared via links
        // even if they can't create/remove/edit links in this shared folder
        if (caller != null) {
            sf.throwIfNoPrivilegeToChangeACL(caller);
        }

        // TODO: etag support
        List<com.aerofs.rest.api.UrlShare> urls = Lists.newArrayList();
        for (UrlShare url : _factUrlShare.getAllInStore(sf.id())) {
            // NB: expiry logic should probably be moved to the frontend
            // For now this needs to be a drop-in replacement for the json endpoint in the web code
            Long expires = url.getExpiresNullable();
            if (expires == null) {
                expires = 0L;
            } else {
                // FIXME: if the link expires NOW it will be shown as never expiring
                // this is pretty unlikely but should still be fixed in the fullness of time
                expires = (expires - System.currentTimeMillis()) / C.SEC;
            }
            urls.add(new com.aerofs.rest.api.UrlShare(url.getKey(),
                    url.getRestObject().toStringFormal(),
                    url.getToken(),
                    url.getCreatedBy().getString(),
                    url.hasPassword(),
                    expires));
        }
        return Response.ok()
                .entity(ImmutableMap.of("urls", urls))
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

    static SFMember toMember(User u, Permissions p) throws ExNotFound, SQLException
    {
        FullName name = u.getFullName();
        return new SFMember(u.id().getString(), name._first, name._last, p.toArray());
    }

    static SFGroupMember toGroupMember(Group g, Permissions p)
            throws SQLException, ExNotFound
    {
        return new SFGroupMember(g.id().getString(), g.getCommonName(), p.toArray());
    }

    static SFPendingMember toPendingMember(User u, Permissions p, @Nullable User sharer)
            throws ExNotFound, SQLException
    {
        FullName n = u.exists() ? u.getFullName() : null;
        return new SFPendingMember(u.id().getString(),
                n != null ? n._first : null,
                n != null ? n._last : null,
                sharer != null ? sharer.id().getString() : null,
                p.toArray());
    }

    /**
     * @return a list of SFMembers that are in the SharedFolder not taking groups into account
     */
    private static List<SFMember> listMembers(Iterable<UserPermissionsAndState> userPermStates)
            throws ExNotFound, SQLException
    {
        List<SFMember> members = Lists.newArrayList();
        for (UserPermissionsAndState ups : userPermStates) {
            if (ups._state != SharedFolderState.JOINED) continue;
            // filter out TS users
            if (ups._user.id().isTeamServerID()) continue;
            members.add(toMember(ups._user, ups._permissions));
        }
        return members;
    }

    static List<SFMember> listMembers(SharedFolder sf)
            throws ExNotFound, SQLException
    {
        return listMembers(sf.getUserRolesAndStatesForGroup(null));
    }

    /**
     * @return a list of SFPendingMembers that are invited to the SharedFolder not taking groups into account
     */
    private static List<SFPendingMember> listPendingMembers(SharedFolder sf, Iterable<UserPermissionsAndState> userPermStates)
            throws ExNotFound, SQLException
    {
        List<SFPendingMember> members = Lists.newArrayList();
        for (UserPermissionsAndState ups : userPermStates) {
            if (ups._state != SharedFolderState.PENDING) continue;
            // filter out TS users
            if (ups._user.id().isTeamServerID()) continue;
            SFPendingMember pm = toPendingMember(ups._user, ups._permissions,
                    sf.getSharerNullable(ups._user));
            members.add(pm);
        }
        return members;
    }

    static List<SFPendingMember> listPendingMembers(SharedFolder sf)
            throws SQLException, ExNotFound
    {
        return listPendingMembers(sf, sf.getUserRolesAndStatesForGroup(null));
    }

    static List<SFGroupMember> listGroupMembers(SharedFolder sf)
            throws SQLException, ExNotFound
    {
        List<SFGroupMember> groupMembers = Lists.newArrayList();
        for (GroupPermissions gp : sf.getAllGroupsAndRoles()) {
            groupMembers.add(toGroupMember(gp._group, gp._permissions));
        }
        return groupMembers;
    }
}
