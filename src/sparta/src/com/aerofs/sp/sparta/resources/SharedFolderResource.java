/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.rest.api.Member;
import com.aerofs.rest.util.AuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.util.EntityTagSet;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.ACLNotificationPublisher;
import com.aerofs.sp.server.lib.SharedFolder;
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
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;

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
    private final ACLNotificationPublisher _aclNotifier;
    private final AuditClient _audit;

    @Inject
    public SharedFolderResource(SharingRulesFactory sharingRules, User.Factory factUser,
            ACLNotificationPublisher aclNotifier, AuditClient audit)
    {
        _factUser = factUser;
        _sharingRules = sharingRules;
        _aclNotifier = aclNotifier;
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

        // TODO: consider more robust etag
        EntityTag etag = new EntityTag(aclEtag(caller), true);
        if (ifNoneMatch.isValid() && ifNoneMatch.matches(etag)) {
            return Response.notModified(etag).build();
        }

        return Response.ok()
                .entity(new com.aerofs.rest.api.SharedFolder(sf.id().toStringFormal(), sf.getName(),
                        listMembers(sf)))
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
                .add("folder", sf.getName())
                .publish();

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
                .add("folder", sf.getName())
                .publish();

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
}
