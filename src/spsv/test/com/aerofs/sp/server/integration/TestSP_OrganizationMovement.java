/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.UserID;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExAlreadyInvited;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.proto.Sp.PBAuthorizationLevel;
import com.aerofs.proto.Sp.GetAuthorizationLevelReply;
import com.aerofs.proto.Sp.GetOrganizationInvitationsReply;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestSP_OrganizationMovement extends AbstractSPTest
{
    private static final String _organizationName = "Some Awesome Organization";

    private void addOrganization()
            throws Exception
    {
        // Create a new organization (name doesn't matter). The session user will now be an admin
        // of this new organization.
        service.addOrganization(_organizationName);
    }

    private void sendInvitation(UserID userID)
            throws Exception
    {
       service.inviteToOrganization(userID.toString());
    }

    /**
     * Accept the first inviation returned by the get organization invites call.
     * @return the ID of the organization that we hvae joined.
     * @throws Exception
     */
    private int acceptFirstInvitation()
            throws Exception
    {
        // Switch to the invited user.
        GetOrganizationInvitationsReply pending = service.getOrganizationInvitations().get();

        // Accept the invite.
        int orgID = pending.getOrganizationInvitationsList().get(0).getOrganizationId();
        service.acceptOrganizationInvitation(orgID);

        return orgID;
    }

    /**
     * Ignore the first inviation returned by the get organization invites call.
     * @return the ID of the organization that we hvae joined.
     * @throws Exception
     */
    private int ignoreFirstInvitation()
            throws Exception
    {
        // Switch to the invited user.
        GetOrganizationInvitationsReply pending = service.getOrganizationInvitations().get();

        // Ignore the invite.
        int orgID = pending.getOrganizationInvitationsList().get(0).getOrganizationId();
        service.ignoreOrganizationInvitation(orgID);

        return orgID;
    }

    private void acceptSpecificInvitation(int orgID)
            throws Exception
    {
        service.acceptOrganizationInvitation(orgID);
    }

    @Test
    public void shouldMoveUserToNewOrganizationViaAcceptOrganizationInvitationCall()
            throws Exception
    {
        setSessionUser(USER_1);
        addOrganization();
        sendInvitation(USER_2);
        setSessionUser(USER_2);
        acceptFirstInvitation();

        // Verify user 2 is indeed in the new organization.
        assertEquals(service.getOrgPreferences().get().getOrganizationName(), _organizationName);

        // Verify get organization invitations call does not return any new invitations, since the
        // user has already been moved over.
        GetOrganizationInvitationsReply invites = service.getOrganizationInvitations().get();
        assertEquals(0, invites.getOrganizationInvitationsList().size());
    }

    @Test (expected = ExNoPerm.class)
    public void shouldThrowExNoPermIfUserIsNotAdmin()
            throws Exception
    {
        setSessionUser(USER_1);
        service.inviteToOrganization(USER_2.toString());
    }

    @Test (expected = ExAlreadyInvited.class)
    public void shouldThrowExAlreadyInvitedIfUserHasAlreadyBeenInvited()
            throws Exception
    {
        try {
            setSessionUser(USER_1);
            addOrganization();
            sendInvitation(USER_2);
        } catch (Exception e) {
            assertTrue(false);
        }

        sendInvitation(USER_2);
    }

    @Test (expected = ExNotFound.class)
    public void shouldThrowExNotFoundIfUserIsAlreadyAccepted()
            throws Exception
    {
        int orgID = 0;

        try {
            setSessionUser(USER_1);
            addOrganization();
            sendInvitation(USER_2);
            setSessionUser(USER_2);
            orgID = acceptFirstInvitation();
        } catch (Exception e) {
            assertTrue(false);
        }

        acceptSpecificInvitation(orgID);
    }

    @Test (expected = ExNotFound.class)
    public void shouldThrowExNotFoundIfUserNotInvitedToTargetOrganization()
            throws Exception
    {
        int orgID = 0;

        try {
            setSessionUser(USER_1);
            addOrganization();
            sendInvitation(USER_2);
            setSessionUser(USER_2);
            orgID = acceptFirstInvitation();
            setSessionUser(USER_3);
        } catch (Exception e) {
            assertTrue(false);
        }

        acceptSpecificInvitation(orgID);
    }

    @Test
    public void shouldDowngradeToNonAdminWhenMovingOrganization()
            throws Exception
    {
        // Make user 1 and admin of a new organization.
        setSessionUser(USER_1);
        addOrganization();

        // User 2 is also an admin, and invites user 1 to thier org.
        setSessionUser(USER_2);
        addOrganization();
        sendInvitation(USER_1);

        // User 1 accept invite to user 2's org.
        setSessionUser(USER_1);
        acceptFirstInvitation();

        // Verify user 1 is not an admin in user 2's organization.
        GetAuthorizationLevelReply reply = service.getAuthorizationLevel().get();
        assertEquals(PBAuthorizationLevel.USER, reply.getLevel());
    }

    @Test (expected = ExNotFound.class)
    public void shouldThowExNotFoundWhenTryingToAcceptIgnoredInvitation()
            throws Exception
    {
        int orgID = 0;

        try {
            setSessionUser(USER_1);
            addOrganization();
            sendInvitation(USER_2);
            setSessionUser(USER_2);
            orgID = ignoreFirstInvitation();
        } catch (Exception e) {
            assertTrue(false);
        }

        // Verify that the organization invite has indeed been deleted (ignored).
        acceptSpecificInvitation(orgID);
    }
}
