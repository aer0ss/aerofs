package com.aerofs.sp.server.integration;

import com.aerofs.lib.Param;
import com.aerofs.lib.acl.Role;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test the functionality of our ACL system, including SP.shareFolder's ability to set ACLs, but
 * don't bother testing shareFolder more deeply here (see TestSP_ShareFolder)
 */
public class TestSP_ACL extends AbstractSPFolderPermissionTest
{
    // Another random person (in addition to the ones created by our parent).
    private final User USER_4 = factUser.create(UserID.fromInternal("user_4"));

    private long getInitialServerACL()
    {
        //noinspection PointlessArithmeticExpression
        return Param.INITIAL_ACL_EPOCH + 1;
    }

    @Before
    public void setup()
            throws Exception
    {
        // set up USER_4
        sqlTrans.begin();

        saveUser(USER_4);

        // remove all root stores to simplify test verifications.
        sfdb.delete(SID.rootSID(USER_1.id()));
        sfdb.delete(SID.rootSID(USER_2.id()));
        sfdb.delete(SID.rootSID(USER_3.id()));

        sqlTrans.commit();
    }

    @Test(expected = ExBadArgs.class)
    public void shareFolder_shouldThrowOnEmptyInviteeList()
            throws Exception
    {
        sessionUser.set(USER_1);
        service.shareFolder("folder", SID_1.toPB(), Collections.<PBSubjectRolePair>emptyList(),
                "").get();
    }

    private static class UserAndRole {
        User u;
        Role r;

        @Override
        public String toString()
        {
            return u + ": " + r;
        }

        UserAndRole(User u, Role r)
        {
            this.u = u;
            this.r = r;
        }
    }
    @Test
    public void shareFolder_shouldAllowToShareIfNoACLExists()
            throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.OWNER);

        GetACLReply reply = service.getACL(0L).get();

        assertGetACLReplyIncrementsEpochBy(reply, 1);
        assertACLOnlyContains(getSingleACL(SID_1, reply), USER_1, Role.OWNER);
        assertVerkehrPublishOnlyContains(USER_1);
    }

    private void assertGetACLReplyIncrementsEpochBy(GetACLReply reply, int delta)
    {
        assertEquals(getInitialServerACL() + delta, reply.getEpoch());
    }

    @Test
    public void shareFolder_shouldAllowOwnerToShareAndNotifyAllAffectedUsers()
            throws Exception
    {
        // create shared folder and invite a first user
        shareFolder(USER_1, SID_1, USER_2, Role.OWNER);
        assertVerkehrPublishOnlyContains(USER_1);
        clearVerkehrPublish();

        // inviteee joins
        joinSharedFolder(USER_2, SID_1);
        assertVerkehrPublishOnlyContains(USER_1, USER_2);
        clearVerkehrPublish();

        // now lets see if the other person can add a third person
        shareAndJoinFolder(USER_2, SID_1, USER_4, Role.EDITOR);
        assertVerkehrPublishOnlyContains(USER_1, USER_2, USER_4);
        clearVerkehrPublish();

        // now let's see what the acls are like
        setSessionUser(USER_1);
        GetACLReply reply = service.getACL(0L).get();

        assertGetACLReplyIncrementsEpochBy(reply, 3);

        assertACLOnlyContains(getSingleACL(SID_1, reply),
                new UserAndRole(USER_1, Role.OWNER),
                new UserAndRole(USER_2, Role.OWNER),
                new UserAndRole(USER_4, Role.EDITOR));
    }

    @Test
    public void shareFolder_shouldForbidNonOwnerToShare()
            throws Exception
    {
        // share folder and invite a new editor
        shareAndJoinFolder(USER_1, SID_1, USER_2, Role.EDITOR);

        sqlTrans.begin();
        // set USER_2 as non-admin
        USER_2.setOrganization(USER_1.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();

        try {
            // get the editor to try and make some role changes
            shareAndJoinFolder(USER_2, SID_1, USER_4, Role.EDITOR);
            // must not reach here
            fail();
        } catch (ExNoPerm e) {}
    }

    @Test
    public void deleteACL_shouldAllowOwnerToDeleteAndNotifyAllAffectedUsers()
            throws Exception
    {
        // share a folder and add a second person (as owner)
        shareAndJoinFolder(USER_1, SID_1, USER_2, Role.OWNER);
        clearVerkehrPublish();

        // add a third person (as editor)
        shareAndJoinFolder(USER_1, SID_1, USER_3, Role.EDITOR);
        clearVerkehrPublish();

        // now have the second guy delete the third

        setSessionUser(USER_2);
        service.deleteACL(SID_1.toPB(), USER_3.id().getString()).get();

        // expect first, second and third guy all to be notified

        assertVerkehrPublishOnlyContains(USER_1, USER_2, USER_3);

        // have the first guy get his acl

        setSessionUser(USER_1);
        GetACLReply reply = service.getACL(0L).get();

        // this guy has seen _all_ the updates, so he should see an epoch of 4
        assertGetACLReplyIncrementsEpochBy(reply, 4);

        assertACLOnlyContains(getSingleACL(SID_1, reply),
                new UserAndRole(USER_1, Role.OWNER),
                new UserAndRole(USER_2, Role.OWNER));

        // now have the deleted guy get his acl

        setSessionUser(USER_3);
        reply = service.getACL(0L).get();

        // only two updates have affected him, so he should have an epoch of 2
        assertEquals(getInitialServerACL() + 2, reply.getEpoch());
        // since all entries have been deleted for this user, there should be no entries for him
        assertEquals(0, reply.getStoreAclCount());
    }

    @Test
    public void deleteACL_shouldReturnSuccessfullyEvenIfACLDoesntContainSubject()
            throws Exception
    {
        // share folder
        shareFolder(USER_1, SID_1, USER_4, Role.EDITOR);
        clearVerkehrPublish(); // don't care

        // now attempt to delete someone for whom the role doesn't exist

        setSessionUser(USER_1);

        try {
            service.deleteACL(SID_1.toPB(), USER_2.id().getString()).get();
            // must not reach here
            fail();
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }

        setSessionUser(USER_1);
        GetACLReply reply = service.getACL(0L).get();

        // epoch shouldn't be bumped on a deletion of a person that doesn't exist
        assertGetACLReplyIncrementsEpochBy(reply, 1);

        assertACLOnlyContains(getSingleACL(SID_1, reply), USER_1, Role.OWNER);
    }

    @Test
    public void deleteACL_shouldForbitNonOwnerToDeleteACLs()
            throws Exception
    {
        // share folder with an editor
        shareAndJoinFolder(USER_1, SID_1, USER_2, Role.EDITOR);

        // get the editor to try to delete the owner
        setSessionUser(USER_2);

        try {
            service.deleteACL(SID_1.toPB(), USER_1.id().getString()).get();
            // must not reach here
            fail();
        } catch (ExNoPerm e) {}
    }

    @Test
    public void getACL_shouldAllowAnyUserWithAnyRoleToGetACL()
            throws Exception
    {
        // share store # 1
        shareAndJoinFolder(USER_1, SID_1, USER_3, Role.EDITOR);

        // share store # 2
        shareAndJoinFolder(USER_2, SID_2, USER_3, Role.EDITOR);

        // now have the editor do a getacl call

        setSessionUser(USER_3);
        GetACLReply reply = service.getACL(0L).get();

        // ok:

        // epoch for this guy should be 2 (started at 0, added twice as editor)
        assertEquals(getInitialServerACL() + 2, reply.getEpoch());

        // he should have 2 store entries (since he's the editor of 2 stores)
        assertEquals(2, reply.getStoreAclCount());

        for (PBStoreACL storeACL : reply.getStoreAclList()) {
            SID aclSID = new SID(storeACL.getStoreId());
            if (aclSID.equals(SID_1)) {
                assertACLOnlyContains(storeACL.getSubjectRoleList(),
                        new UserAndRole(USER_1, Role.OWNER), new UserAndRole(USER_3, Role.EDITOR));
            } else if (aclSID.equals(SID_2)) {
                assertACLOnlyContains(storeACL.getSubjectRoleList(),
                        new UserAndRole(USER_2, Role.OWNER), new UserAndRole(USER_3, Role.EDITOR));
            } else {
                fail("unexpected store acl for s:" + aclSID);
            }
        }
    }

    @Test
    public void getACL_shouldNotIncludePendingMembers() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);
        shareFolder(USER_1, SID_1, USER_3, Role.OWNER);

        checkACL(USER_1, Role.OWNER);

        joinSharedFolder(USER_2, SID_1);

        checkACL(
                new UserAndRole(USER_1, Role.OWNER),
                new UserAndRole(USER_2, Role.EDITOR));

        joinSharedFolder(USER_3, SID_1);

        checkACL(
                new UserAndRole(USER_1, Role.OWNER),
                new UserAndRole(USER_2, Role.EDITOR),
                new UserAndRole(USER_3, Role.OWNER));

        leaveSharedFolder(USER_2, SID_1);

        checkACL(
                new UserAndRole(USER_1, Role.OWNER),
                new UserAndRole(USER_3, Role.OWNER));

        leaveSharedFolder(USER_1, SID_1);

        // USER_1 is the session user, hence the empty ACL reply
        assertEquals(service.getACL(0L).get().getStoreAclCount(), 0);

        setSessionUser(USER_3);
        checkACL(USER_3, Role.OWNER);
    }

    private void checkACL(User user, Role role) throws Exception
    {
        checkACL(new UserAndRole(user, role));
    }

    private void checkACL(UserAndRole ... urs) throws Exception
    {
        assertACLOnlyContains(getSingleACL(SID_1, service.getACL(0L).get()), urs);
    }

    @Test
    public void updateACL_shouldAllowToChangeExistingACLs()
            throws Exception
    {
        // add user 3 as editor for store # 1
        shareAndJoinFolder(USER_1, SID_1, USER_3, Role.EDITOR);

        clearVerkehrPublish(); // clear out notifications from sharing

        // update ACL for user 3 as user 1
        setSessionUser(USER_1);
        service.updateACL(SID_1.toPB(), USER_3.id().getString(), Role.OWNER.toPB());

        // check that notifications were published on update
        assertVerkehrPublishOnlyContains(USER_1, USER_3);

        // verify user 3 has updated ACL in place
        setSessionUser(USER_3);
        GetACLReply reply = service.getACL(0L).get();

        // epoch for this guy should be 2 (started at 0, added as editor then as owner)
        assertGetACLReplyIncrementsEpochBy(reply, 2);
        assertACLOnlyContains(getSingleACL(SID_1, reply),
                new UserAndRole(USER_1, Role.OWNER),
                new UserAndRole(USER_3, Role.OWNER));
    }

    @Test
    public void updateACL_shouldThrowOnUpdatingNonexistingACLs()
            throws Exception
    {
        // add the owner for store # 1
        shareFolder(USER_1, SID_1, USER_4, Role.OWNER);
        clearVerkehrPublish(); // throw away this notification

        // update ACL for user 3 as user 1
        setSessionUser(USER_1);
        try {
            // should fail with ExNotFound
            service.updateACL(SID_1.toPB(), USER_3.id().getString(), Role.OWNER.toPB());
            // must not reach here
            fail();
        } catch (Exception e) {
            // make sure we clean up after uncommitted transaction(s)
            sqlTrans.handleException();
        }

        assertVerkehrPublishIsEmpty();

        // check that user 3 still has no ACLs set in the db
        setSessionUser(USER_3);
        GetACLReply reply = service.getACL(0L).get();
        assertEquals(getInitialServerACL(), reply.getEpoch());
        assertEquals(0, reply.getStoreAclCount());
    }

    @Test
    public void updateACL_shouldForbidNonOwnerToUpdateACLs()
            throws Exception
    {
        // add user 3 as editor for store # 1
        shareAndJoinFolder(USER_1, SID_1, USER_3, Role.EDITOR);
        clearVerkehrPublish(); // throw away these notifications

        // try to edit user 1's ACL entry for store 1 as user 3
        setSessionUser(USER_3);
        try {
            // should fail with ExNoPerm
            service.updateACL(SID_1.toPB(), USER_1.id().getString(), Role.EDITOR.toPB());
            // the code must not reach here
            fail();
        } catch (ExNoPerm e) {
            // make sure we clean up after uncommitted transaction(s)
            sqlTrans.handleException();
        }

        assertVerkehrPublishIsEmpty();

        // check that user 3 only has editor permissions
        setSessionUser(USER_3);
        GetACLReply reply = service.getACL(0L).get();

        assertGetACLReplyIncrementsEpochBy(reply, 1);
        assertACLOnlyContains(getSingleACL(SID_1, reply),
                new UserAndRole(USER_1, Role.OWNER),
                new UserAndRole(USER_3, Role.EDITOR));
    }

    private List<PBSubjectRolePair> getSingleACL(SID sid, GetACLReply getACLReply)
    {
        assertEquals(1, getACLReply.getStoreAclCount());
        assertEquals(sid.toPB(), getACLReply.getStoreAcl(0).getStoreId());
        return getACLReply.getStoreAcl(0).getSubjectRoleList();
    }

    private void assertACLOnlyContains(List<PBSubjectRolePair> pairs, User user, Role role)
            throws Exception
    {
        assertACLOnlyContains(pairs, new UserAndRole(user, role));
    }

    private void assertACLOnlyContains(List<PBSubjectRolePair> pairs, UserAndRole ... urs)
            throws Exception
    {
        Set<User> tsUsers = Sets.newHashSet();

        sqlTrans.begin();
        for (UserAndRole ur : urs) {
            assertACLContains(pairs, ur.u, ur.r);
            tsUsers.add(ur.u.getOrganization().getTeamServerUser());
        }
        sqlTrans.commit();

        // verify the team server of all the users exist in the ACL
        for (User tsUser : tsUsers) {
            assertACLContains(pairs, tsUser, Role.EDITOR);
        }

        if (pairs.size() != urs.length + tsUsers.size()) {
            StringBuilder sb = new StringBuilder("[");
            for (PBSubjectRolePair pair : pairs) {
                sb.append(pair.getSubject())
                        .append(": ")
                        .append(Role.fromPB(pair.getRole()))
                        .append(", ");
            }
            sb.append("]");

            fail("getACL() returns mismatch expected: " + Arrays.toString(urs) + " + team servers," +
                    " actual: " + sb.toString());
        }
    }

    // FIXME: [sigh] think up a more efficient way
    private void assertACLContains(List<PBSubjectRolePair> pairs, User subject, Role role)
    {
        boolean found = false;

        for (PBSubjectRolePair pair : pairs) {
            try {
                UserID currentSubject = UserID.fromExternal(pair.getSubject());
                Role actualRole = Role.fromPB(pair.getRole());

                if (currentSubject.equals(subject.id()) && actualRole.equals(role)) {
                    found = true;
                } else if (currentSubject.equals(subject.id())) {
                    fail(currentSubject + " expect role: " + role + " actual: " + actualRole);
                }
            } catch (ExBadArgs exBadArgs) {
                fail("no role for " + pair.getRole().name());
            }
        }

        assertTrue("no entry for " + subject + ": " + role, found);
    }
}
