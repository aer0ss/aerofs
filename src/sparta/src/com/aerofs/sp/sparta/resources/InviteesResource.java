/*

 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import java.net.URI;
import java.sql.SQLException;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.UserID;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Invitee;
import com.aerofs.rest.auth.IUserAuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.Version;
import com.aerofs.sp.server.InvitationHelper;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.sparta.Transactional;
import com.google.inject.Inject;

@Path(Service.VERSION + "/invitees")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class InviteesResource extends AbstractSpartaResource {
    private static Logger l = LoggerFactory.getLogger(InviteesResource.class);
    private final OrganizationInvitation.Factory _factInvitation;
    private final User.Factory _factUser;
    private final InvitationHelper _invitationHelper;

    @Inject
    public InviteesResource(OrganizationInvitation.Factory factInvitation, User.Factory factUser,
            InvitationHelper invitationHelper) {
        _factInvitation = factInvitation;
        _factUser = factUser;
        _invitationHelper = invitationHelper;
    }

    private User validateAuth(IUserAuthToken token, Scope scope)
            throws SQLException, ExNotFound, ExNoPerm {
        if (scope != null) {
            requirePermission(scope, token);
        }
        return _factUser.create(token.user());
    }

    @Since("1.3")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Auth IUserAuthToken token, Invitee attrs, @Context Version version)
            throws Exception {
        User caller = validateAuth(token, null);
        User invitee = _factUser.create(UserID.fromExternal(attrs.emailTo));
        OrganizationInvitation invitation = _factInvitation.create(invitee,
                caller.getOrganization());

        if (invitation.exists()) {
            l.info("conflict creating invitation for {}", invitee.id().getString());
            return Response.status(Response.Status.CONFLICT)
                    .entity(new Error(Error.Type.CONFLICT, "User has already been invited"))
                    .build();
        }

        InvitationHelper.InviteToSignUpResult result = _invitationHelper.inviteToSignUp(caller,
                invitee);
        invitation = _factInvitation.save(caller, invitee, caller.getOrganization(),
                result._signUpCode);
        result._emailer.send();

        String location = Service.DUMMY_LOCATION + 'v' + version + "/invitees/" + attrs.emailTo;

        return Response.created(URI.create(location))
                .entity(new Invitee(invitation.getInvitee().id().getString(),
                        caller.id().getString(), result._signUpCode))
                .build();
    }

    @Since("1.3")
    @GET
    @Path("/{email}")
    public Response get(@Auth IUserAuthToken token, @Context Version version,
            @PathParam("email") User invitee) throws Exception {
        User caller = validateAuth(token, Scope.ORG_ADMIN);
        OrganizationInvitation invitation = _factInvitation.create(invitee,
                caller.getOrganization());
        return Response.ok().entity(new Invitee(invitation.getInvitee().id().getString(),
                caller.id().getString(), invitation.getCode())).build();
    }

    @Since("1.3")
    @DELETE
    @Path("/{email}")
    public Response delete(@Auth IUserAuthToken token, @Context Version version,
            @PathParam("email") User invitee) throws Exception {
        User caller = validateAuth(token, Scope.ORG_ADMIN);
        OrganizationInvitation invitation = _factInvitation.create(invitee,
                caller.getOrganization());
        invitation.delete();
        return Response.noContent().build();
    }
}
