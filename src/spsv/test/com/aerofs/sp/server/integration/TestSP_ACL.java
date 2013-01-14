package com.aerofs.sp.server.integration;

import com.aerofs.lib.FullName;
import com.aerofs.lib.Param;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePairs;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.sp.server.lib.organization.OrganizationID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;

/**
 * Test the functionality of our ACL system, including SP.shareFolder's ability to set ACLs, but
 * don't bother testing shareFolder more deeply here (see TestSP_ShareFolder)
 */
public class TestSP_ACL extends AbstractSPFolderPermissionTest
{
    // Another random person (in addition to the ones created by our parent).
    private static final UserID TEST_USER_4 = UserID.fromInternal("user_4");
    private static final byte[] TEST_USER_4_CRED = "CREDENTIALS".getBytes();

    private long getInitialServerACL()
    {
        //noinspection PointlessArithmeticExpression
        return Param.INITIAL_ACL_EPOCH + 1;
    }

    //
    // UTILITY
    //

    private List<PBSubjectRolePair> assertValidACLReplyAndGetPairs(GetACLReply getACLReply,
            long expectedEpoch, int numberOfACLEntries)
    {
        assertEquals(expectedEpoch, getACLReply.getEpoch());
        assertEquals(1, getACLReply.getStoreAclCount());
        assertEquals(numberOfACLEntries, getACLReply.getStoreAcl(0).getSubjectRoleCount());

        return getACLReply.getStoreAcl(0).getSubjectRoleList();
    }

    // FIXME: [sigh] think up a more efficient way
    private void assertACLContains(UserID subject, Role expectedRole, List<PBSubjectRolePair> pairs)
    {
        boolean found = false;

        for (PBSubjectRolePair pair : pairs) {
            try {
                UserID currentSubject = UserID.fromExternal(pair.getSubject());
                Role actualRole = Role.fromPB(pair.getRole());

                if (currentSubject.equals(subject) && actualRole.equals(expectedRole)) {
                    found = true;
                } else if (currentSubject.equals(subject)) {
                    fail("j:" + currentSubject + " has r:" + actualRole.getDescription());
                }
            } catch (ExBadArgs exBadArgs) {
                fail("no role for:" + pair.getRole().name());
            }
        }

        assertTrue("no entry for j:" + subject + " r:" + expectedRole.getDescription(), found);
    }

    @Before
    public void setupTestSPACL()
            throws Exception
    {
        // set up TEST_USER_4
        trans.begin();
        udb.insertUser(TEST_USER_4, new FullName(TEST_USER_4.toString(), TEST_USER_4.toString()),
                TEST_USER_4_CRED, OrganizationID.DEFAULT, AuthorizationLevel.USER);
        udb.setVerified(TEST_USER_4);
        trans.commit();
    }

    //
    // TESTS
    //

    @Test(expected = ExBadArgs.class)
    public void shareFolder_shouldThrowOnEmptyInviteeList()
            throws Exception
    {
        sessionUser.set(factUser.create(USER_1));
        service.shareFolder("folder", TEST_SID_1.toPB(), Collections.<PBSubjectRolePair>emptyList(),
                "").get();
    }

    @Test
    public void shareFolder_shouldAllowToShareIfNoACLExists()
            throws Exception
    {
        setupMockVerkehrToSuccessfullyPublish();

        shareFolder(USER_1, TEST_SID_1, USER_2, Role.OWNER);

        GetACLReply getAcl = service.getACL(0L).get();

        assertEquals(getInitialServerACL() + 1, getAcl.getEpoch());
        assertEquals(1, getAcl.getStoreAclCount());
        assertEquals(TEST_SID_1, new SID(getAcl.getStoreAcl(0).getStoreId()));
        assertEquals(1, getAcl.getStoreAcl(0).getSubjectRoleCount());
        assertEquals(USER_1,
                UserID.fromInternal(getAcl.getStoreAcl(0).getSubjectRole(0).getSubject()));
        assertEquals(Role.OWNER, Role.fromPB(getAcl.getStoreAcl(0).getSubjectRole(0).getRole()));

        verify(verkehrPublisher).publish_(eq(USER_1.toString()), any(byte[].class));
    }

    @Test
    public void shareFolder_shouldAllowOwnerToShareAndNotifyAllAffectedUsers()
            throws Exception
    {
        Set<String> published = mockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // create shared folder and invite a first user
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.OWNER);
        assertEquals(1, published.size());
        assertTrue(published.contains(USER_1.toString()));
        published.clear();

        // inviteee joins
        joinSharedFolder(USER_2, TEST_SID_1);
        assertEquals(2, published.size());
        assertTrue(published.contains(USER_1.toString()));
        assertTrue(published.contains(USER_2.toString()));
        published.clear();

        // now lets see if the other person can add a third person
        shareAndJoinFolder(USER_2, TEST_SID_1, TEST_USER_4, Role.EDITOR);
        assertEquals(3, published.size());
        assertTrue(published.contains(USER_1.toString()));
        assertTrue(published.contains(USER_2.toString()));
        assertTrue(published.contains(TEST_USER_4.toString()));
        published.clear();

        // now let's see what the acls are like
        setSessionUser(USER_1);
        GetACLReply reply = service.getACL(0L).get();

        List<PBSubjectRolePair> pairs = assertValidACLReplyAndGetPairs(reply,
                getInitialServerACL() + 3, 3);

        assertACLContains(USER_1, Role.OWNER, pairs);
        assertACLContains(USER_2, Role.OWNER, pairs);
        assertACLContains(TEST_USER_4, Role.EDITOR, pairs);
    }

    @Test
    public void shareFolder_shouldForbidNonOwnerToShare()
            throws Exception
    {
        setupMockVerkehrToSuccessfullyPublish();

        // share folder and invitea new editor
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);

        try {
            // get the editor to try and make some role changes
            shareAndJoinFolder(USER_2, TEST_SID_1, TEST_USER_4, Role.EDITOR);
            // must not reach here
            assertTrue(false);
        } catch (ExNoPerm e) { }
    }

    @Test
    public void deleteACL_shouldAllowOwnerToDeleteAndNotifyAllAffectedUsers()
            throws Exception
    {
        Set<String> published = mockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // share a folder and add a second person (as owner)
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_2, Role.OWNER);
        published.clear(); // don't care

        // add a third person (as editor)
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_3, Role.EDITOR);
        published.clear(); // don't care

        // now have the second guy delete the third

        setSessionUser(USER_2);
        service.deleteACL(TEST_SID_1.toPB(), Arrays.asList(USER_3.toString())).get();

        // expect first, second and third guy all to be notified

        assertEquals(3, published.size());
        assertTrue(published.contains(USER_1.toString()));
        assertTrue(published.contains(USER_2.toString()));
        assertTrue(published.contains(USER_3.toString()));

        // have the first guy get his acl

        List<PBSubjectRolePair> pairs;

        setSessionUser(USER_1);
        GetACLReply reply = service.getACL(0L).get();

        // this guy has seen _all_ the updates, so he should see an epoch of 4
        pairs = assertValidACLReplyAndGetPairs(reply, getInitialServerACL() + 4, 2);

        assertACLContains(USER_1, Role.OWNER, pairs);

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
        Set<String> published = mockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // share folder
        shareFolder(USER_1, TEST_SID_1, TEST_USER_4, Role.EDITOR);
        published.clear(); // don't care

        // now attempt to delete someone for whom the role doesn't exist

        setSessionUser(USER_1);

        try {
            service.deleteACL(TEST_SID_1.toPB(), Arrays.asList(USER_2.toString())).get();
            // must not reach here
            assertTrue(false);
        } catch (ExNotFound e) {
            trans.handleException();
        }

        setSessionUser(USER_1);
        GetACLReply reply = service.getACL(0L).get();

        // epoch shouldn't be bumped on a deletion of a person that doesn't exist
        List<PBSubjectRolePair> pairs = assertValidACLReplyAndGetPairs(reply,
                getInitialServerACL() + 1, 1);

        assertACLContains(USER_1, Role.OWNER, pairs);
    }

    @Test
    public void deleteACL_shouldForbitNonOwnerToDeleteACLs()
            throws Exception
    {
        setupMockVerkehrToSuccessfullyPublish();

        // share folder with an editor
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);

        // get the editor to try to delete the owner
        setSessionUser(USER_2);

        try {
            service.deleteACL(TEST_SID_1.toPB(), Arrays.asList(USER_1.toString())).get();
            // must not reach here
            assertTrue(false);
        } catch (ExNoPerm e) {}
    }

    @Test
    public void getACL_shouldAllowAnyUserWithAnyRoleToGetACL()
            throws Exception
    {
        setupMockVerkehrToSuccessfullyPublish();

        // share store # 1
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_3, Role.EDITOR);

        // share store # 2
        shareAndJoinFolder(USER_2, TEST_SID_2, USER_3, Role.EDITOR);

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
            if (aclSID.equals(TEST_SID_1)) {
                assertEquals(2, storeACL.getSubjectRoleCount());
                List<PBSubjectRolePair> pairs = storeACL.getSubjectRoleList();
                assertACLContains(USER_1, Role.OWNER, pairs);
                assertACLContains(USER_3, Role.EDITOR, pairs);
            } else if (aclSID.equals(TEST_SID_2)) {
                assertEquals(2, storeACL.getSubjectRoleCount());
                List<PBSubjectRolePair> pairs = storeACL.getSubjectRoleList();
                assertACLContains(USER_2, Role.OWNER, pairs);
                assertACLContains(USER_3, Role.EDITOR, pairs);
            } else {
                fail("unexpected store acl for s:" + aclSID);
            }
        }
    }

    @Test
    public void getACL_shouldNotIncludePendingMembers() throws Exception
    {
        setupMockVerkehrToSuccessfullyPublish();

        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);
        shareFolder(USER_1, TEST_SID_1, USER_3, Role.OWNER);

        checkACL(ImmutableMap.<SID, Map<UserID, Role>>of(
                TEST_SID_1,
                ImmutableMap.of(USER_1, Role.OWNER)));

        joinSharedFolder(USER_2, TEST_SID_1);

        checkACL(ImmutableMap.<SID, Map<UserID, Role>>of(
                TEST_SID_1,
                ImmutableMap.of(USER_1, Role.OWNER,
                        USER_2, Role.EDITOR)));

        joinSharedFolder(USER_3, TEST_SID_1);

        checkACL(ImmutableMap.<SID, Map<UserID, Role>>of(
                TEST_SID_1,
                ImmutableMap.of(USER_1, Role.OWNER,
                        USER_2, Role.EDITOR,
                        USER_3, Role.OWNER)));

        leaveSharedFolder(USER_2, TEST_SID_1);

        checkACL(ImmutableMap.<SID, Map<UserID, Role>>of(TEST_SID_1,
                ImmutableMap.of(USER_1, Role.OWNER, USER_3, Role.OWNER)));

        leaveSharedFolder(USER_1, TEST_SID_1);

        // USER_1 is the session user, hence the empty ACL reply
        checkACL(Collections.<SID, Map<UserID, Role>>emptyMap());

        setSessionUser(USER_3);
        checkACL(ImmutableMap.<SID, Map<UserID, Role>>of(
                TEST_SID_1,
                ImmutableMap.of(USER_3, Role.OWNER)));
    }

    private void checkACL(Map<SID, Map<UserID, Role>> acls) throws Exception
    {
        List<PBStoreACL> storeAcls = service.getACL(0L).get().getStoreAclList();
        assertEquals(acls.size(), storeAcls.size());
        for (PBStoreACL storeAcl : storeAcls) {
            Map<UserID, Role> expectedAcls = acls.get(new SID(storeAcl.getStoreId()));
            assertNotNull(expectedAcls);
            assertEquals(expectedAcls, SubjectRolePairs.mapFromPB(storeAcl.getSubjectRoleList()));
        }
    }

    @Test
    public void updateACL_shouldAllowToChangeExistingACLs()
            throws Exception
    {
        Set<String> published = mockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // add user 3 as editor for store # 1
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_3, Role.EDITOR);

        published.clear(); // clear out notifications from sharing

        // update ACL for user 3 as user 1
        setSessionUser(USER_1);
        service.updateACL(TEST_SID_1.toPB(), toPB(USER_3, Role.OWNER));

        // check that notifications were published on update
        assertEquals(2, published.size());
        assertTrue(published.contains(USER_1.toString()));
        assertTrue(published.contains(USER_3.toString()));

        // verify user 3 has updated ACL in place
        setSessionUser(USER_3);
        GetACLReply reply = service.getACL(0L).get();

        // epoch for this guy should be 2 (started at 0, added as editor then as owner)
        assertEquals(getInitialServerACL() + 2, reply.getEpoch());

        // he should have 1 store entry
        assertEquals(1, reply.getStoreAclCount());

        PBStoreACL acl = reply.getStoreAcl(0);
        assertEquals(2, acl.getSubjectRoleCount());
        List<PBSubjectRolePair> pairs = acl.getSubjectRoleList();
        assertACLContains(USER_1, Role.OWNER, pairs);
        assertACLContains(USER_3, Role.OWNER, pairs);
    }

    @Test
    public void updateACL_shouldThrowOnUpdatingNonexistingACLs()
            throws Exception
    {
        Set<String> published = mockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // add the owner for store # 1
        shareFolder(USER_1, TEST_SID_1, TEST_USER_4, Role.OWNER);
        published.clear(); // throw away this notification

        // update ACL for user 3 as user 1
        setSessionUser(USER_1);
        try {
            // should fail with ExNotFound
            service.updateACL(TEST_SID_1.toPB(), toPB(USER_3, Role.OWNER));
            // must not reach here
            assertTrue(false);
        } catch (Exception e) {
            // make sure we clean up after uncommitted transaction(s)
            trans.handleException();
        }

        // ensure that nothing was published
        assertEquals(0, published.size());

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
        Set<String> published = mockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // add user 3 as editor for store # 1
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_3, Role.EDITOR);
        published.clear(); // throw away these notifications

        // try to edit user 1's ACL entry for store 1 as user 3
        setSessionUser(USER_3);
        try {
            // should fail with ExNoPerm
            service.updateACL(TEST_SID_1.toPB(), toPB(USER_1, Role.EDITOR));
            // the code must not reach here
            assertTrue(false);
        } catch (ExNoPerm e) {
            // make sure we clean up after uncommitted transaction(s)
            trans.handleException();
        }

        // ensure that nothing was published
        assertEquals(0, published.size());

        // check that user 3 only has editor permissions
        setSessionUser(USER_3);
        GetACLReply reply = service.getACL(0L).get();
        assertEquals(getInitialServerACL() + 1, reply.getEpoch());
        assertEquals(1, reply.getStoreAclCount());
        List<PBSubjectRolePair> pairs = reply.getStoreAcl(0).getSubjectRoleList();
        assertACLContains(USER_1, Role.OWNER, pairs);
        assertACLContains(USER_3, Role.EDITOR, pairs);
    }
}
