/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.ex.ExAlreadyInvited;
import com.aerofs.proto.Sp.GetAuthorizationLevelReply;
import com.aerofs.proto.Sp.GetOrganizationInvitationsReply;
import com.aerofs.proto.Sp.PBAuthorizationLevel;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestSP_OrganizationMovement extends AbstractSPTest
{
    @After
    public void resetParams() { PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = false; }

    private void sendInvitation(User user)
            throws Exception
    {
       service.inviteToOrganization(user.id().getString());
    }

    private void acceptOrganizationInvitation(Organization org, User user)
            throws Exception
    {
        clearVerkehrPublish();
        service.acceptOrganizationInvitation(org.id().getInt());
        // when the user changes the org, the ACL of its root store must be updated to inlucde
        // the team server user id.
        assertVerkehrPublishContains(user, org.getTeamServerUser());
    }

    /**
     * Accept the first inviation returned by the get organization invites call.
     * @return the ID of the organization that we have joined.
     */
    private Organization acceptFirstInvitation(User user)
            throws Exception
    {
        // Switch to the invited user.
        GetOrganizationInvitationsReply pending = service.getOrganizationInvitations().get();

        // Accept the invite.
        Organization org = factOrg.create(
                pending.getOrganizationInvitationsList().get(0).getOrganizationId());
        acceptOrganizationInvitation(org, user);

        return org;
    }

    /**
     * Ignore the first inviation returned by the get organization invites call.
     * @return the ID of the organization that we hvae joined.
     * @throws Exception
     */
    private Organization deleteFirstInvitation()
            throws Exception
    {
        // Switch to the invited user.
        GetOrganizationInvitationsReply pending = service.getOrganizationInvitations().get();

        // Ignore the invite.
        Organization org = factOrg.create(
                pending.getOrganizationInvitationsList().get(0).getOrganizationId());
        service.deleteOrganizationInvitation(org.id().getInt());

        return org;
    }

    @Test
    public void shouldMoveUserToNewOrganizationViaAcceptOrganizationInvitationCall()
            throws Exception
    {
        setSessionUser(USER_1);

        sendInvitation(USER_2);
        setSessionUser(USER_2);
        acceptFirstInvitation(USER_2);

        // Verify user 2 is indeed in the new organization.
        sqlTrans.begin();
        assertEquals(USER_1.getOrganization(), USER_2.getOrganization());
        sqlTrans.commit();

        // Verify get organization invitations call does not return any new invitations, since the
        // user has already been moved over.
        GetOrganizationInvitationsReply invites = service.getOrganizationInvitations().get();
        assertEquals(0, invites.getOrganizationInvitationsList().size());
    }

    @Test
    public void shouldAllowInviteToDefaultOrg() throws Exception
    {
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = true;

        sqlTrans.begin();
        User admin = saveUser();
        User accepter = saveUser();

        Organization adminOrg = admin.getOrganization();
        assertEquals(OrganizationID.PRIVATE_ORGANIZATION, adminOrg.id());
        sqlTrans.commit();

        setSessionUser(admin);

        sqlTrans.begin();
        accepter.setOrganization(USER_1.getOrganization(), AuthorizationLevel.ADMIN);
        sqlTrans.commit();

        sendInvitation(accepter);

        sqlTrans.begin();
        accepter.setOrganization(adminOrg, AuthorizationLevel.USER);
        sqlTrans.commit();

        setSessionUser(accepter);
        acceptOrganizationInvitation(adminOrg, accepter);

        sqlTrans.begin();
        // Verify accepter is indeed in the new organization.
        assertEquals(OrganizationID.PRIVATE_ORGANIZATION, accepter.getOrganization().id());
        sqlTrans.commit();

        // Verify get organization invitations call does not return any new invitations, since the
        // user has already been moved over.
        GetOrganizationInvitationsReply invites = service.getOrganizationInvitations().get();
        assertEquals(0, invites.getOrganizationInvitationsList().size());
    }

    @Test
    public void inviteToOrganization_shouldThrowExNoPermIfUserIsNotAdmin()
            throws Exception
    {
        setSessionUser(USER_1);

        // set USER_1 as non-admin
        sqlTrans.begin();
        USER_1.setOrganization(USER_2.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();

        try {
            service.inviteToOrganization(USER_2.id().getString());
            fail();
        } catch (ExNoPerm e) {}
    }

    @Test
    public void inviteToOrganization_shouldThrowExAlreadyInvitedIfUserHasAlreadyBeenInvited()
            throws Exception
    {
        setSessionUser(USER_1);
        sendInvitation(USER_2);
        try {
            sendInvitation(USER_2);
            fail();
        } catch (ExAlreadyInvited e) {}
    }

    @Test
    public void acceptOrganizationInvitation_shouldThrowExNotFoundIfUserIsAlreadyAccepted()
            throws Exception
    {
        setSessionUser(USER_1);
        sendInvitation(USER_2);
        setSessionUser(USER_2);
        Organization org = acceptFirstInvitation(USER_2);

        try {
            acceptOrganizationInvitation(org, USER_2);
            fail();
        } catch (ExNotFound e) {}
    }

    @Test
    public void acceptOrganizationInvitation_shouldThrowExNotFoundIfUserNotInvitedToTargetOrganization()
            throws Exception
    {
        setSessionUser(USER_1);

        sendInvitation(USER_2);
        setSessionUser(USER_2);
        Organization org = acceptFirstInvitation(USER_2);
        setSessionUser(USER_3);

        try {
            acceptOrganizationInvitation(org, USER_2);
            fail();
        } catch (ExNotFound e) {}
    }

    @Test
    public void acceptOrganizationInvitation_shouldDowngradeToNonAdminWhenMovingOrganization()
            throws Exception
    {
        // Make user 1 and admin of a new organization.
        setSessionUser(USER_1);

        // User 2 is also an admin, and invites user 1 to thier org.
        setSessionUser(USER_2);

        sendInvitation(USER_1);

        // User 1 accept invite to user 2's org.
        setSessionUser(USER_1);
        acceptFirstInvitation(USER_1);

        // Verify user 1 is not an admin in user 2's organization.
        GetAuthorizationLevelReply reply = service.getAuthorizationLevel().get();
        assertEquals(PBAuthorizationLevel.USER, reply.getLevel());
    }

    @Test
    public void acceptOrganizationInvitation_shouldThowExNotFoundWhenTryingToAcceptIgnoredInvitation()
            throws Exception
    {
        setSessionUser(USER_1);

        sendInvitation(USER_2);
        setSessionUser(USER_2);
        Organization org = deleteFirstInvitation();

        try {
            // Verify that the organization invite has indeed been deleted (ignored).
            acceptOrganizationInvitation(org, USER_2);
            fail();
        } catch (ExNotFound e) {}
    }
}
