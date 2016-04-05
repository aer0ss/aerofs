/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExWrongOrganization;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.api.GroupMember;
import com.aerofs.rest.auth.IAuthToken;
import com.aerofs.rest.auth.IUserAuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.Version;
import com.aerofs.servlets.lib.ThreadLocalSFNotifications;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.server.ACLNotificationPublisher;
import com.aerofs.sp.server.InvitationHelper;
import com.aerofs.sp.server.SFNotificationPublisher;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.group.Group.AffectedUserIDsAndInvitedFolders;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.sparta.Transactional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.sql.SQLException;

import static com.google.common.base.Preconditions.checkArgument;

@Path(Service.VERSION + "/groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class GroupResource extends AbstractSpartaResource
{
    public static Version FIRST_GROUP_API_VERSION = new Version(1, 3);

    static int MAX_RESULTS_RETURNED = 1000;

    private final User.Factory _factUser;
    private final Group.Factory _factGroup;
    private final Organization.Factory _factOrg;
    private final ACLNotificationPublisher _aclNotifier;
    private final Authenticator _authenticator;
    private final InvitationHelper _invitationHelper;
    private final ThreadLocalSFNotifications _sfNotif;
    private final SFNotificationPublisher _sfPublisher;

    @Inject
    public GroupResource(User.Factory factUser, Group.Factory factGroup, Organization.Factory factOrg,
            ACLNotificationPublisher aclNotifier, Authenticator authenticator, InvitationHelper invitationHelper,
            ThreadLocalSFNotifications sfNotif, SFNotificationPublisher sfPublisher)
    {
        _factUser = factUser;
        _factGroup = factGroup;
        _factOrg = factOrg;
        _aclNotifier = aclNotifier;
        _authenticator = authenticator;
        _invitationHelper = invitationHelper;
        _sfNotif = sfNotif;
        _sfPublisher = sfPublisher;
    }

    @Since("1.3")
    @POST
    public Response create(@Auth IAuthToken token,
            @Context Version version,
            com.aerofs.rest.api.Group group)
            throws Exception
    {
        requirePermission(Scope.ORG_ADMIN, token);
        checkArgument(group.name != null, "Request body missing required field: name");
        User caller = userFromAuthToken(token);

        Group g = _factGroup.save(group.name, caller != null ? caller.getOrganization().id() : OrganizationID.PRIVATE_ORGANIZATION, null);

        String location = Service.DUMMY_LOCATION
                + 'v' + version
                + "/groups/" + g.id().getString();

        return Response.created(URI.create(location))
                .entity(new com.aerofs.rest.api.Group(g.id().getString(), group.name, Lists.newArrayList()))
                .build();
    }

    @Since("1.3")
    @GET
    @Path("/{id}")
    public Response getGroup(@Auth IAuthToken token,
            @PathParam("id") Group group)
            throws SQLException, ExNotFound
    {
        requirePermission(Scope.READ_GROUPS, token);
        throwIfGroupNonexistentOrInDifferentOrg(userFromAuthToken(token), group);
        return Response.ok()
                .entity(new com.aerofs.rest.api.Group(
                        group.id().getString(), group.getCommonName(),
                         listMembersFor(group)))
                .build();
    }

    @Since("1.3")
    @DELETE
    @Path("/{id}")
    public Response deleteGroup(@Auth IAuthToken token,
            @PathParam("id") Group group)
            throws Exception
    {
        requirePermission(Scope.ORG_ADMIN, token);
        throwIfGroupNonexistentOrInDifferentOrg(userFromAuthToken(token), group);

        _sfNotif.begin();
        _aclNotifier.publish_(group.delete());

        _sfPublisher.sendNotifications(_sfNotif.get());
        _sfNotif.clear();
        //TODO etags
        return Response.noContent()
                .build();
    }

    @Since("1.3")
    @GET
    public Response listGroups(@Auth IAuthToken token,
                               @DefaultValue("0") @QueryParam("offset") int offset,
                               @DefaultValue("10") @QueryParam("results") int results)
            throws SQLException, ExNotFound
    {
        Preconditions.checkArgument(offset >= 0, "Offset cannot be negative");
        Preconditions.checkArgument(results >= 0, "Number of results returned cannot be negative");
        Preconditions.checkArgument(results <= MAX_RESULTS_RETURNED, "Number of results returned must be less than " + MAX_RESULTS_RETURNED);

        requirePermission(Scope.READ_GROUPS, token);
        User caller = userFromAuthToken(token);
        Organization org = caller != null ? caller.getOrganization() : _factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);

        ImmutableList.Builder<com.aerofs.rest.api.Group> groups = ImmutableList.builder();
        for (Group g : org.listGroups(results, offset)) {
            groups.add(new com.aerofs.rest.api.Group(g.id().getString(), g.getCommonName(), listMembersFor(g)));
        }

        return Response.ok()
                .entity(groups.build())
                .build();
    }

    @Since("1.3")
    @GET
    @Path("/{id}/shares")
    public Response getGroupShares(@Auth IAuthToken token, @PathParam("id") Group group)
            throws SQLException, ExNotFound
    {
        requirePermission(Scope.READ_GROUPS, token);
        User caller = userFromAuthToken(token);
        throwIfGroupNonexistentOrInDifferentOrg(caller, group);

        ImmutableList.Builder<com.aerofs.rest.api.SharedFolder> bd = ImmutableList.builder();
        for (SharedFolder sf : group.listSharedFolders()) {
            if (token.hasFolderPermission(Scope.READ_ACL, sf.id())) {
                Permissions p = caller != null ? sf.getPermissionsNullable(caller) : null;
                bd.add(new com.aerofs.rest.api.SharedFolder(
                        sf.id().toStringFormal(), sf.getName(caller),
                        SharedFolderResource.listMembers(sf),
                        SharedFolderResource.listGroupMembers(sf),
                        SharedFolderResource.listPendingMembers(sf),
                        caller != null ? sf.isExternal(caller) : null,
                        p != null ? p.toArray() : null, sf.isLocked()));
            }
        }

        return Response.ok()
                .entity(bd.build())
                .build();
    }

    @Since("1.3")
    @GET
    @Path("/{id}/members")
    public Response getGroupMembers(@Auth IAuthToken token, @PathParam("id") Group group)
            throws SQLException, ExNotFound
    {
        requirePermission(Scope.READ_GROUPS, token);
        throwIfGroupNonexistentOrInDifferentOrg(userFromAuthToken(token), group);

        return Response.ok()
                .entity(listMembersFor(group))
                .build();
    }

    @Since("1.3")
    @POST
    @Path("/{id}/members")
    public Response addGroupMember(@Auth IAuthToken token, @Context Version version,
            @PathParam("id") Group group, GroupMember member)
            throws Exception
    {
        requirePermission(Scope.ORG_ADMIN, token);
        checkArgument(member.email != null, "Request body missing required field: email");
        User caller = userFromAuthToken(token);
        throwIfGroupNonexistentOrInDifferentOrg(caller, group);

        User newMember = _factUser.create(member.email);
        if (!_authenticator.isInternalUser(newMember.id())) {
            throw new ExWrongOrganization(newMember + " is external to this organization, " +
                    "and not allowed to be in this organization's groups");
        }

        _sfNotif.begin();
        AffectedUserIDsAndInvitedFolders result = group.addMember(newMember);
        _aclNotifier.publish_(result._affected);
        _sfPublisher.sendNotifications(_sfNotif.get());
        _sfNotif.clear();

        _invitationHelper.createBatchFolderInvitationAndEmailer(group,
                // default to sending the emails from an org admin if there's no user associated with the request
                caller != null ? caller : _factUser.create(OrganizationID.PRIVATE_ORGANIZATION.toTeamServerUserID()),
                newMember, result._folders)
                .send();

        String location = Service.DUMMY_LOCATION
                + 'v' + version
                + "/groups/" + group.id().getString()
                + "/members/" + member.email;

        FullName fn = newMember.exists() ? newMember.getFullName() : new FullName("", "");
        return Response.created(URI.create(location))
                .entity(new com.aerofs.rest.api.GroupMember(member.email, fn._first, fn._last))
                .build();
    }

    @Since("1.3")
    @GET
    @Path("/{id}/members/{email}")
    public Response getGroupMember(@Auth IAuthToken token,
            @PathParam("id") Group group, @PathParam("email") User member)
            throws SQLException, ExNotFound
    {
        requirePermission(Scope.READ_GROUPS, token);
        throwIfGroupNonexistentOrInDifferentOrg(userFromAuthToken(token), group);

        if (!member.exists() || !group.hasMember(member)) {
            throw new ExNotFound("User is not a member of group");
        }

        //TODO etags

        FullName fn = member.getFullName();
        return Response.ok()
                .entity(new com.aerofs.rest.api.GroupMember(
                        member.id().getString(), fn._first, fn._last))
                .build();
    }

    @Since("1.3")
    @DELETE
    @Path("/{id}/members/{email}")
    public Response deleteGroupMember(@Auth IAuthToken token,
            @PathParam("id") Group group, @PathParam("email") User member)
            throws Exception
    {
        requirePermission(Scope.ORG_ADMIN, token);
        throwIfGroupNonexistentOrInDifferentOrg(userFromAuthToken(token), group);

        if (!member.exists() || !group.hasMember(member)) {
            throw new ExNotFound("User is not a member of group");
        }

        _sfNotif.begin();
        ImmutableCollection<UserID> affected = group.removeMember(member, null);
        _aclNotifier.publish_(affected);
        _sfPublisher.sendNotifications(_sfNotif.get());
        _sfNotif.clear();

        return Response.noContent()
                .build();
    }

    static ImmutableCollection<com.aerofs.rest.api.GroupMember> listMembersFor(Group group)
            throws SQLException, ExNotFound
    {
        ImmutableList.Builder<com.aerofs.rest.api.GroupMember> members = ImmutableList.builder();
        for (User u : group.listMembers()) {
            FullName fn = u.getFullName();
            members.add(new com.aerofs.rest.api.GroupMember(u.id().getString(), fn._first, fn._last));
        }
        return members.build();
    }

    private @Nullable User userFromAuthToken(IAuthToken token)
    {
        if (token instanceof IUserAuthToken) {
            return _factUser.create(((IUserAuthToken) token).user());
        } else {
            return null;
        }
    }

    private void throwIfGroupNonexistentOrInDifferentOrg(@Nullable User user, Group group)
            throws SQLException, ExNotFound
    {
        OrganizationID orgID = user != null ? user.getOrganization().id() : OrganizationID.PRIVATE_ORGANIZATION;
        if (!group.exists() || !orgID.equals(group.getOrganization().id())) {
            throw new ExNotFound("No such group");
        }
    }
}
