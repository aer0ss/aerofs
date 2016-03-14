/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.audit.client.AuditClient.AuditableEvent;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.RestObject;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.aerofs.rest.api.SFGroupMember;
import com.aerofs.rest.api.SFMember;
import com.aerofs.rest.api.SFPendingMember;
import com.aerofs.rest.auth.DelegatedUserDeviceToken;
import com.aerofs.rest.auth.IAuthToken;
import com.aerofs.rest.auth.IUserAuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.Version;
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
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.google.inject.Inject;

import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@Path(Service.VERSION + "/shares")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class SharedFolderResource extends AbstractSpartaResource
{
    public static final String X_REAL_IP = "X-Real-IP";

    private static final Logger l = Loggers.getLogger(SharedFolderResource.class);

    private final User.Factory _factUser;
    private final Group.Factory _factGroup;
    private final SharedFolder.Factory _factSF;
    private final UrlShare.Factory _factUrlShare;
    private final SharingRulesFactory _sharingRules;
    private final InvitationHelper _invitationHelper;
    private final SharedFolderNotificationEmailer _sfnEmailer;
    private final ACLNotificationPublisher _aclNotifier;
    private final AuditClient _audit;
    private final UrlShareResource _urlShare;

    @Inject
    public SharedFolderResource(User.Factory factUser, SharedFolder.Factory factSF, Group.Factory factGroup,
            UrlShare.Factory factUrlShare, SharingRulesFactory sharingRules,
            InvitationHelper invitationHelper, ACLNotificationPublisher aclNotifier,
            SharedFolderNotificationEmailer sfnEmailer, AuditClient audit,
            UrlShareResource urlShare)
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
        _urlShare = urlShare;
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
            if (!caller.isAdmin()) {
                throwIfNotAMember(sf, caller, "No such shared folder");
            }
            return caller;
        }
        return null;
    }

    @Since("1.1")
    @GET
    @Path("/{id}")
    public Response get(@Auth IAuthToken token, @Context Version version, @PathParam("id") SharedFolder sf,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
            throws SQLException, ExNotFound
    {
        User caller = validateAuth(token, Scope.READ_ACL, sf);
        List<SFMember> members = listMembers(sf);
        List<SFPendingMember> pending = listPendingMembers(sf);
        List<SFGroupMember> groups = listGroupMembers(sf);
        EntityTag etag = getEntityTag(members, pending, groups);
        if (ifNoneMatch.isValid() && etag != null && ifNoneMatch.matches(etag)) {
            return Response.notModified(etag).build();
        }

        Permissions callerPermissions = caller == null ? null : sf.getPermissionsNullable(caller);
        return Response.ok()
                .entity(new com.aerofs.rest.api.SharedFolder(sf.id().toStringFormal(),
                        sf.getName(caller), members,
                        version.compareTo(GroupResource.FIRST_GROUP_API_VERSION) >= 0 ? groups : null,
                        pending,
                        caller == null ? null : sf.isExternal(caller),
                        callerPermissions == null ? null : callerPermissions.toArray(), sf.isLocked()))
                .tag(etag)
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
        requirePermission(Scope.WRITE_ACL, token);

        User caller = _factUser.create(token.user());

        SharedFolder sf;
        // Use this if condition as a way to ensure only services(specifically Polaris)
        // can share an existing folder via the Public API. Block users from directly making
        // this call.
        if (token instanceof DelegatedUserDeviceToken && share.id != null) {
            // Check if folder already exists. If it does use the SID passed in.
            sf = _factSF.create(new SID(share.id));
            if (sf.exists()) {
                return Response.status(Status.NO_CONTENT).build();
            }
        } else {
            // in the unlikely event that we generate a UUID that's already taken,
            // retry up to 10 times before returning a 5xx
            int attempts = 10;
            do {
                sf = _factSF.create(SID.generate());
            } while (sf.exists() && --attempts > 0);
        }

        Set<UserID> affected = Sets.newHashSet();
        affected.addAll(sf.save(share.name, caller));
        if (share.members != null && share.members.size() > 0) {
            for (SFMember member : share.members) {
                if (member.email.equals(caller.id().getString())) {
                    continue;
                }
                User user = _factUser.create(member.email);
                if (!user.exists()) {
                    throw new ExNotFound("user not found: " + member.email);
                }
                sf.addUserWithGroup(user, null, Permissions.fromArray(member.permissions), caller);
                affected.add(UserID.fromExternal(member.email));
            }
        }
        if (share.isExternal != null) sf.setExternal(caller, share.isExternal);
        if (share.isLocked != null && share.isLocked) sf.setLocked();
        _aclNotifier.publish_(affected);

        audit(sf, caller, token, "folder.create")
                .publish();

        String location = Service.DUMMY_LOCATION
                + 'v' + version
                + "/shares/" + sf.id().toStringFormal();

        List<SFMember> members = listMembers(sf);
        List<SFPendingMember> pending = listPendingMembers(sf);
        List<SFGroupMember> groups = listGroupMembers(sf);
        EntityTag etag = getEntityTag(members, pending, groups);
        return Response.created(URI.create(location))
                .entity(new com.aerofs.rest.api.SharedFolder(sf.id().toStringFormal(), sf.getName(caller), members,
                        version.compareTo(GroupResource.FIRST_GROUP_API_VERSION) >= 0 ? groups : null,
                        pending, sf.isExternal(caller), Permissions.OWNER.toArray(), sf.isLocked()))
                .tag(etag)
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
        validateAuth(token, Scope.READ_ACL, sf);
        List<SFMember> members = listMembers(sf);
        EntityTag etag = getEntityTag(members, listPendingMembers(sf), listGroupMembers(sf));
        if (ifNoneMatch.isValid() && ifNoneMatch.matches(etag)) {
            return Response.notModified(etag).build();
        }

        return Response.ok()
                .entity(members)
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
        // FIXME (RD) this route uses the effective permissions, unlike all the other members routes
        Permissions p = throwIfNotAMember(sf, member, "No such member");
        EntityTag etag = getEntityTag(sf);
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
            @HeaderParam(Names.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            SFMember member)
            throws Exception
    {
        checkArgument(member.email != null, "Request body missing required field: email");
        requirePermission(Scope.WRITE_ACL, token);

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

        List<SFMember> members = listMembers(sf);
        List<SFPendingMember> pending = listPendingMembers(sf);
        if (ifMatch.isValid()) {
            List<SFGroupMember> groups = listGroupMembers(sf);
            EntityTag etag = getEntityTag(members, pending, groups);
            if (!ifMatch.matches(etag)) {
                return Response.status(Status.PRECONDITION_FAILED)
                        .tag(etag).build();
            }
        }

        SharedFolderState currentState = sf.getStateNullable(user);
        if (currentState == SharedFolderState.JOINED) {
            return Response.status(Status.CONFLICT)
                    .entity(new Error(Type.CONFLICT, "Member already exists"))
                    .build();
        }

        ISharingRules rules = _sharingRules.create(caller);
        Permissions req = rules.onUpdatingACL(sf, user, Permissions.fromArray(member.permissions));

        // POST when the user is already in the shared folder, but not joined, will join the user
        if (sf.getPermissionsNullable(user) != null) {
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
        checkArgument(member.permissions != null, "Request body missing required field: permissions");
        User caller = validateAuth(token, Scope.WRITE_ACL, sf);
        sf.throwIfNoPrivilegeToChangeACL(caller);
        Permissions oldPermissions = sf.getPermissions(user);

        List<SFPendingMember> pending = listPendingMembers(sf);
        List<SFGroupMember> groups = listGroupMembers(sf);
        EntityTag etag = getEntityTag(listMembers(sf), pending, groups);
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
                .tag(getEntityTag(listMembers(sf), pending, groups))
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
        User caller = validateAuth(token, Scope.WRITE_ACL, sf);
        Preconditions.checkState(caller != null, "no user from iuserauthtoken");
        if (!caller.equals(user)) sf.throwIfNoPrivilegeToChangeACL(caller);

        List<SFPendingMember> pending = listPendingMembers(sf);
        List<SFGroupMember> groups = listGroupMembers(sf);
        if (ifMatch.isValid()) {
            EntityTag etag = getEntityTag(listMembers(sf), pending, groups);
            if(!ifMatch.matches(etag)) {
                return Response.status(Status.PRECONDITION_FAILED)
                        .tag(etag).build();
            }
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
                .tag(getEntityTag(listMembers(sf), pending, groups))
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
        List<SFGroupMember> groups = listGroupMembers(sf);
        EntityTag etag = getEntityTag(listMembers(sf), listPendingMembers(sf), groups);
        if (ifNoneMatch.isValid() && ifNoneMatch.matches(etag)) {
            return Response.notModified(etag).build();
        }

        return Response.ok()
                .entity(groups)
                .tag(etag)
                .build();
    }

    @Since("1.3")
    @POST
    @Path("/{id}/groups")
    public Response addGroupMember(@Auth IUserAuthToken token,
            @PathParam("id") SharedFolder sf,
            @Context Version version,
            @HeaderParam(Names.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            SFGroupMember groupMember)
            throws Exception
    {
        User caller = validateAuth(token, Scope.WRITE_ACL, sf);
        checkArgument(groupMember.id != null, "Request body missing required field: id");
        requirePermission(Scope.WRITE_ACL, token);

        Group group = _factGroup.create(GroupID.fromExternal(groupMember.id));
        if (!group.exists()) throw new ExNotFound("No such group");

        sf.throwIfNoPrivilegeToChangeACL(caller);

        List<SFMember> members = listMembers(sf);
        List<SFPendingMember> pending = listPendingMembers(sf);
        if (ifMatch.isValid()) {
            EntityTag etag = getEntityTag(members, pending, listGroupMembers(sf));
            if (!ifMatch.matches(etag)) {
                return Response.status(Status.PRECONDITION_FAILED)
                        .tag(etag).build();
            }
        }

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

        return Response.created(URI.create(location))
                .entity(toGroupMember(group, req))
                .tag(getEntityTag(members, pending, listGroupMembers(sf)))
                .build();
    }

    @Since("1.3")
    @GET
    @Path("/{id}/groups/{groupid}")
    public Response getGroupMember(@Auth IAuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("groupid") Group group,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch)
            throws SQLException, ExNotFound
    {
        validateAuth(token, Scope.READ_ACL, sf);
        if (!group.inSharedFolder(sf)) {
            throw new ExNotFound("No such member");
        }

        EntityTag etag = getEntityTag(sf);
        if (ifNoneMatch.isValid() && ifNoneMatch.matches(etag)) {
            return Response.notModified(etag).build();
        }

        return Response.ok()
                .entity(toGroupMember(group, group.getRoleForSharedFolder(sf)))
                .tag(etag)
                .build();
    }

    @Since("1.3")
    @PUT
    @Path("/{id}/groups/{groupid}")
    public Response updateGroupMember(@Auth IUserAuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("groupid") Group group,
            @HeaderParam(Names.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            SFGroupMember groupMember)
            throws Exception
    {
        User caller = validateAuth(token, Scope.WRITE_ACL, sf);
        checkArgument(groupMember.permissions != null, "Request body missing required field: permissions");
        if (!group.inSharedFolder(sf)) {
            throw new ExNotFound("No such member");
        }

        sf.throwIfNoPrivilegeToChangeACL(caller);
        List<SFMember> members = listMembers(sf);
        List<SFPendingMember> pending = listPendingMembers(sf);
        if (ifMatch.isValid()) {
            EntityTag etag = getEntityTag(members, pending, listGroupMembers(sf));
            if (!ifMatch.matches(etag)) {
                return Response.status(Status.PRECONDITION_FAILED)
                        .tag(etag).build();
            }
        }

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
                .tag(getEntityTag(members, pending, listGroupMembers(sf)))
                .build();
    }

    @Since("1.3")
    @DELETE
    @Path("/{id}/groups/{groupid}")
    public Response deleteGroupMember(@Auth IUserAuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("groupid") Group group,
            @HeaderParam(Names.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch)
            throws Exception
    {
        User caller = validateAuth(token, Scope.WRITE_ACL, sf);
        sf.throwIfNoPrivilegeToChangeACL(caller);
        if (!group.inSharedFolder(sf)) {
            throw new ExNotFound("No such member");
        }

        List<SFMember> members = listMembers(sf);
        List<SFPendingMember> pending = listPendingMembers(sf);
        if (ifMatch.isValid()) {
            EntityTag etag = getEntityTag(members, pending, listGroupMembers(sf));
            if (!ifMatch.matches(etag)) {
                return Response.status(Status.PRECONDITION_FAILED)
                        .tag(etag).build();
            }
        }

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
                .tag(getEntityTag(members, pending, listGroupMembers(sf)))
                .build();
    }

    @Since("1.1")
    @GET
    @Path("/{id}/pending")
    public Response listPendingMembers(@Auth IAuthToken token,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch,
            @PathParam("id") SharedFolder sf)
            throws SQLException, ExNotFound
    {
        validateAuth(token, Scope.READ_ACL, sf);
        List<SFPendingMember> pending = listPendingMembers(sf);
        EntityTag etag = getEntityTag(listMembers(sf), pending, listGroupMembers(sf));
        if (ifNoneMatch.isValid() && etag != null && ifNoneMatch.matches(etag)) {
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
    public Response getPendingMember(@Auth IAuthToken token,
            @HeaderParam(Names.IF_NONE_MATCH) @DefaultValue("") EntityTagSet ifNoneMatch,
            @PathParam("id") SharedFolder sf,
            @PathParam("email") User user)
            throws SQLException, ExNotFound
    {
        validateAuth(token, Scope.READ_ACL, sf);

        EntityTag etag = getEntityTag(sf);
        if (ifNoneMatch.isValid() && etag != null && ifNoneMatch.matches(etag)) {
            return Response.notModified(etag).build();
        }

        Permissions p = sf.getPermissionsNullable(user);
        if (p == null  || sf.getStateNullable(user) != SharedFolderState.PENDING) {
            throw new ExNotFound("No such pending member");
        }

        return Response.ok()
                .entity(toPendingMember(user, p, sf.getSharerNullable(user)))
                .tag(etag)
                .build();
    }

    @Since("1.1")
    @POST
    @Path("/{id}/pending")
    public Response inviteMember(@Auth IUserAuthToken token,
            @PathParam("id") SharedFolder sf,
            @Context Version version,
            @HeaderParam(Names.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch,
            SFPendingMember invitee)
            throws Exception
    {
        checkArgument(invitee.email != null, "Request body missing required field: email");
        checkArgument(invitee.permissions != null, "Request body missing required field: permissions");

        User caller = validateAuth(token, Scope.WRITE_ACL, sf);
        User user;
        try {
            user = _factUser.create(UserID.fromExternal(invitee.email));
        } catch (Exception e) {
            throw new ExBadArgs("Invalid invitee email");
        }

        sf.throwIfNoPrivilegeToChangeACL(caller);

        List<SFMember> members = listMembers(sf);
        List<SFGroupMember> groups = listGroupMembers(sf);
        if (ifMatch.isValid()) {
            EntityTag etag = getEntityTag(members, listPendingMembers(sf), groups);
            if (!ifMatch.matches(etag)) {
                return Response.status(Status.PRECONDITION_FAILED)
                        .tag(etag).build();
            }
        }

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
                .tag(getEntityTag(members, listPendingMembers(sf), groups))
                .build();
    }

    @Since("1.1")
    @DELETE
    @Path("/{id}/pending/{email}")
    public Response revokeInvitation(@Auth IUserAuthToken token,
            @PathParam("id") SharedFolder sf,
            @PathParam("email") User user,
            @HeaderParam(Names.IF_MATCH) @DefaultValue("") EntityTagSet ifMatch)
            throws SQLException, ExNotFound, ExNoPerm, ExBadArgs
    {
        User caller = validateAuth(token, Scope.WRITE_ACL, sf);
        sf.throwIfNoPrivilegeToChangeACL(caller);

        Permissions p = sf.getPermissionsNullable(user);
        if (p == null  || sf.getStateNullable(user) != SharedFolderState.PENDING) {
            throw new ExNotFound("No such pending member");
        }

        List<SFMember> members = listMembers(sf);
        List<SFGroupMember> groups = listGroupMembers(sf);
        if (ifMatch.isValid()) {
            EntityTag etag = getEntityTag(members, listPendingMembers(sf), groups);
            if (!ifMatch.matches(etag)) {
                return Response.status(Status.PRECONDITION_FAILED)
                        .tag(etag).build();
            }
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
                .tag(getEntityTag(members, listPendingMembers(sf), groups))
                .build();
    }

    /**
     * NB: this route is currently undocumented
     *
     * This is intended to be temporary. Eventually we should figure out acceptable semantics and
     * document them in the public API docs.
     *
     * This route is used by Shelob
     *
     * see ENG-2315
     */
    @Since("1.3")
    @GET
    @Path("/{id}/urls")
    public Response listURLs(@Auth IAuthToken token,
            @PathParam("id") SharedFolder sf)
            throws Exception // ExNotFound, ExNoPerm
    {
        User caller = validateAuth(token, Scope.READ_ACL, sf);

        // FIXME(HB): that seems overly restrictive
        // surely it would be good for users to know which files have been shared via links
        // even if they can't create/remove/edit links in this shared folder
        if (caller != null) {
            sf.throwIfNoPrivilegeToChangeACL(caller);
        }

        List<com.aerofs.rest.api.UrlShare> urls = Lists.newArrayList();
        for (UrlShare url : _factUrlShare.getAllInStore(sf.id())) {
            urls.add(toUrlShareResponse(url));
        }

        return Response.ok()
                .entity(ImmutableMap.of("urls", urls))
                .build();
    }

    /**
     * N.B. this route is undocumented and deprecated
     *
     * It is only kept because AttachmentManager was released prior to its deprecation
     *
     * see ENG-2315
     */
    @Since("1.4")
    @POST
    @Path("/{id}/urls")
    public Response createURL(@Auth IUserAuthToken token,
            @HeaderParam(X_REAL_IP) String ip,
            @PathParam("id") SharedFolder sf,
            @Context Version version,
            com.aerofs.rest.api.UrlShare request)
            throws Exception // ExNotFound, ExNoPerm, ExBadArgs
    {
        RestObject restObject = RestObject.fromString(request.soid);
        // 400 if the target object is not in the store or is anchor (pointing to an object
        // in another store).
        checkArgument(sf.id().equals(restObject.getSID()));
        return _urlShare.createURL(token, ip, version, request);
    }

    static SFMember throwIfNotInList(List<SFMember> members, User user, String message) throws ExNotFound
    {
        SFMember member = members.stream().filter(sf -> sf.email.equals(user.id().getString())).findAny().orElse(null);
        if (member == null) {
            throw new ExNotFound(message);
        }
        return member;
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

    private com.aerofs.rest.api.UrlShare toUrlShareResponse(UrlShare url)
            throws ExNotFound, SQLException
    {
        Long expires = url.getExpiresNullable();

        return new com.aerofs.rest.api.UrlShare(
                url.getKey(),
                url.getRestObject().toStringFormal(),
                url.getToken(),
                url.getCreatedBy().getString(),
                url.getRequireLogin(),
                url.hasPassword(),
                // never reveal the link password
                null,
                expires != null ? new Date(expires) : null);
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
        Collections.sort(members, (member1, member2) -> member1.email.compareTo(member2.email));
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
        Collections.sort(members, (member1, member2) -> member1.email.compareTo(member2.email));
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
        Collections.sort(groupMembers, (group1, group2) -> group1.id.compareTo(group2.id));
        return groupMembers;
    }

    private EntityTag getEntityTag(SharedFolder sf)
            throws SQLException, ExNotFound
    {
        return getEntityTag(listMembers(sf), listPendingMembers(sf), listGroupMembers(sf));
    }

    private EntityTag getEntityTag(List<SFMember> members, List<SFPendingMember> pending, List<SFGroupMember> groups)
    {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        for (SFMember m : members) {
            md.update(BaseUtil.string2utf(m.email));
            for (String p : m.permissions) {
                md.update(p.getBytes());
            }
        }
        md.update("pending".getBytes());
        for (SFPendingMember pm : pending) {
            md.update(BaseUtil.string2utf(pm.email));
            for (String p : pm.permissions) {
                md.update(p.getBytes());
            }
        }
        md.update("groups".getBytes());
        for (SFGroupMember g : groups) {
            md.update(g.id.getBytes());
            for (String p : g.permissions) {
                md.update(p.getBytes());
            }
        }
        return new EntityTag(BaseUtil.hexEncode(md.digest()), true);
    }
}
