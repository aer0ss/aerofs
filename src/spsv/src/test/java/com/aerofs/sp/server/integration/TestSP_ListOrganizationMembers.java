/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.proto.Sp.ListOrganizationMembersReply;
import com.aerofs.sp.server.SPService;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestSP_ListOrganizationMembers extends AbstractSPTest
{
    private Organization _org;

    private User _orgAdmin;
    private User _orgUser1;
    private User _orgUser2;

    @Before
    public void setupUsers()
            throws Exception
    {
        sqlTrans.begin();

        _org = saveOrganization();

        // Note that internally, users are saved with first name and last name equal to id
        _orgAdmin = saveUserWithOrgAndAuthLevel(_org, AuthorizationLevel.ADMIN);
        _orgUser1 = saveUserWithOrgAndAuthLevel(_org, AuthorizationLevel.USER);
        _orgUser2 = saveUserWithOrgAndAuthLevel(_org, AuthorizationLevel.USER);

        sqlTrans.commit();
    }

    @Test
    public void shouldAllowOrgMembersToListOrgMembers()
            throws Exception
    {
        for (User user : newArrayList(_orgAdmin, _orgUser1, _orgUser2)) {
            setSession(user);

            ListOrganizationMembersReply reply = service.listOrganizationMembers(10, 0, "").get();
            assertEquals(reply.getUserAndLevelCount(), 3);
            assertEquals(reply.getTotalCount(), 3);
        }
    }

    @Test
    public void shouldListOrgMembersMatchingString()
            throws Exception
    {
        for (User user : newArrayList(_orgAdmin, _orgUser1, _orgUser2)) {
            setSession(user);

            ListOrganizationMembersReply reply = service.listOrganizationMembers(10, 0,
                    user.id().getString()).get();
            assertEquals(reply.getUserAndLevelCount(), 1);
            assertEquals(reply.getTotalCount(), 1);
        }
    }

    @Test
    public void shouldListOrgMembersMatchingStringWithLimitAndOffset()
            throws Exception
    {
        for (User user : newArrayList(_orgAdmin, _orgUser1, _orgUser2)) {
            setSession(user);

            // All generated test ids start with 'u'
            // And incremented by one with each user created. Our order is admin, user1, user2
            // See AbstractSPTest.newUser

            // Page 1
            ListOrganizationMembersReply reply = service.listOrganizationMembers(1, 0, "u").get();
            String userEmail = reply.getUserAndLevel(0).getUser().getUserEmail();

            assertTrue(userEmail.contains(_orgAdmin.id().getString()));
            assertEquals(reply.getUserAndLevelCount(), 1);
            assertEquals(reply.getTotalCount(), 3);

            // Page 2
            reply = service.listOrganizationMembers(1, 1, "u").get();
            userEmail = reply.getUserAndLevel(0).getUser().getUserEmail();

            assertTrue(userEmail.contains(_orgUser1.id().getString()));
            assertEquals(reply.getUserAndLevelCount(), 1);
            assertEquals(reply.getTotalCount(), 3);

            // Page 3
            reply = service.listOrganizationMembers(1, 2, "u").get();
            userEmail = reply.getUserAndLevel(0).getUser().getUserEmail();

            assertTrue(userEmail.contains(_orgUser2.id().getString()));
            assertEquals(reply.getUserAndLevelCount(), 1);
            assertEquals(reply.getTotalCount(), 3);
        }
    }

    @Test
    public void shouldThrowIfNoSessionUser()
            throws Exception
    {
        try {
            service.listOrganizationMembers(10, 0, "");
        } catch (ExNotAuthenticated e) {
            return;
        }

        fail();
    }

    @Test
    public void shouldThrowIfSessionUserNotFound()
            throws Exception
    {
        setSession(newUser());

        try {
            service.listOrganizationMembers(10, 0, "");
        } catch (ExNotFound e) {
            return;
        }

        fail();
    }

    @Test
    public void shouldLimitResultsBasedOnMaxResults()
            throws Exception
    {
        setSession(_orgAdmin);

        ListOrganizationMembersReply reply = service.listOrganizationMembers(1, 0, "").get();
        assertEquals(reply.getUserAndLevelCount(), 1);
        assertEquals(reply.getTotalCount(), 3);
    }

    @Test
    public void shouldOffsetResultsBasedOnOffset()
            throws Exception
    {
        setSession(_orgAdmin);

        String userEmail1 = service.listOrganizationMembers(10, 0, "").get().getUserAndLevel(1)
                .getUser().getUserEmail();
        String userEmail2 = service.listOrganizationMembers(10, 1, "").get().getUserAndLevel(0)
                .getUser().getUserEmail();

        assertEquals(userEmail1, userEmail2);
    }

    @Test
    public void shouldAcceptOffsetsGreaterThanResultCount()
            throws Exception
    {
        setSession(_orgAdmin);

        ListOrganizationMembersReply reply = service.listOrganizationMembers(10, 10, "").get();
        assertEquals(reply.getUserAndLevelCount(), 0);
        assertEquals(reply.getTotalCount(), 3);
    }

    @Test
    public void shouldThrowOnInvalidOffset()
            throws Exception
    {
        setSession(_orgAdmin);

        try {
            service.listOrganizationMembers(10, -1, "");
        } catch (ExBadArgs e) {
            return;
        }

        fail();
    }

    @Test
    public void shouldThrowOnInvalidMaxResults()
            throws Exception
    {
        setSession(_orgAdmin);

        for (int maxResult : newArrayList(-1, SPService.ABSOLUTE_MAX_RESULTS + 1)) {
            try {
                service.listOrganizationMembers(maxResult, 0, "");
            } catch (ExBadArgs e) {
                continue;
            }

            fail();
        }
    }

    private User saveUserWithOrgAndAuthLevel(Organization org, AuthorizationLevel level)
            throws Exception
    {
        User user = saveUser();
        user.setOrganization(org, level);
        return user;
    }
}
