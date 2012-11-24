package com.aerofs.sp.server;

import com.aerofs.lib.C;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.aerofs.lib.id.SID.rootSID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;

/**
 * Test the functionality of our ACL system, including SP.shareFolder's ability to set ACLs, but
 * don't bother testing shareFolder more deeply here (see TestSPShareFolder)
 */
public class TestSPACL extends AbstractSPFolderPermissionTest
{
    // Another random person (in addition to the ones created by our parent).
    private static final UserID TEST_USER_4 = UserID.fromInternal("user_4");
    private static final byte[] TEST_USER_4_CRED = "CREDENTIALS".getBytes();

    private long getInitialServerACL()
    {
        return C.INITIAL_ACL_EPOCH + 1;
    }

    //
    // UTILITY
    //

    private Set<String> setupMockVerkehrToSuccessfullyPublishAndStoreSubscribers()
    {
        final Set<String> published = new HashSet<String>();
        when(verkehrPublisher.publish_(any(String.class), any(byte[].class))).then(new
        Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                published.add((String)invocation.getArguments()[0]);
                return UncancellableFuture.createSucceeded(null);
            }
        });
        return published;
    }

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
        _transaction.begin();
        db.addUser(new User(TEST_USER_4, TEST_USER_4.toString(), TEST_USER_4.toString(),
                TEST_USER_4_CRED, false, OrgID.DEFAULT, AuthorizationLevel.USER));
        db.markUserVerified(TEST_USER_4);
        _transaction.commit();
    }

    //
    // TESTS
    //

    @Test
    public void shouldAlwaysAllowAnACLEntryToBeAddedIfNoACLEntryExistsForThatStore()
            throws Exception
    {
        setupMockVerkehrToSuccessfullyPublish();

        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_1, Role.OWNER);

        GetACLReply getAcl = service.getACL(0L).get();

        assertEquals(getInitialServerACL() + 1, getAcl.getEpoch());
        assertEquals(1, getAcl.getStoreAclCount());
        assertEquals(TEST_SID_1, new SID(getAcl.getStoreAcl(0).getStoreId()));
        assertEquals(1, getAcl.getStoreAcl(0).getSubjectRoleCount());
        assertEquals(TEST_USER_1, UserID.fromInternal(getAcl.getStoreAcl(0).getSubjectRole(0).getSubject()));
        assertEquals(Role.OWNER, Role.fromPB(getAcl.getStoreAcl(0).getSubjectRole(0).getRole()));

        verify(verkehrPublisher).publish_(eq(TEST_USER_1.toString()), any(byte[].class));
    }

    // should get acl regardless of who you are
    // should override acl if store is wrong

    @Test
    public void shouldDeleteExistingACLEntriesForStoreIfThisIsRequestersRootStoreAndRequesterDoesNotHaveAnExistingACLEntryWithOwnerRole()
            throws Exception
    {
        Set<String> published = setupMockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // malicious user creates an acl for someone's root store
        // note that they don't put themselves in the list!
        shareFolderThroughSP(TEST_USER_1, rootSID(TEST_USER_2), TEST_USER_3, Role.OWNER);

        // clear this set out, because I don't care about what happens prior to the last call
        published.clear();

        // now the actual owner comes along and tries to do the same
        shareFolderThroughSP(TEST_USER_2, rootSID(TEST_USER_2), TEST_USER_2,
                Role.OWNER);

        // we still expect both epoch of 1) the owner and 2) the person who was flushed to be
        // updated and a notification published to them

        // FIXME: we do not properly notify the flushed users!!!
        // assertEquals(2, published.size());
        // assertTrue(published.contains(TEST_USER_3));
        assertTrue(published.contains(TEST_USER_2.toString()));

        // we should see only one entry for the second user

        sessionUser.set(TEST_USER_2);
        GetACLReply reply = service.getACL(0L).get();

        List<PBSubjectRolePair> pairs = assertValidACLReplyAndGetPairs(reply,
                getInitialServerACL() + 1, 1);

        assertACLContains(TEST_USER_2, Role.OWNER, pairs);
    }

    @Test
    public void shouldAllowAnyRequesterWithOwnerRoleToSetARoleForTheStoreAndNotifyRequesterAsWellAsAllAffectedUsersOfACLChange()
            throws Exception
    {
        Set<String> published = setupMockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // add yourself
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_1, Role.OWNER);
        assertEquals(1, published.size());
        assertTrue(published.contains(TEST_USER_1.toString()));
        published.clear();

        // add the second person
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_2, Role.OWNER);
        assertEquals(2, published.size());
        assertTrue(published.contains(TEST_USER_1.toString()));
        assertTrue(published.contains(TEST_USER_2.toString()));
        published.clear();

        // now lets see if the other person can add a third person
        shareFolderThroughSP(TEST_USER_2, TEST_SID_1, TEST_USER_4, Role.EDITOR);
        assertEquals(3, published.size());
        assertTrue(published.contains(TEST_USER_1.toString()));
        assertTrue(published.contains(TEST_USER_2.toString()));
        assertTrue(published.contains(TEST_USER_4.toString()));
        published.clear();

        // now let's see what the acls are like
        sessionUser.set(TEST_USER_1);
        GetACLReply reply = service.getACL(0L).get();

        List<PBSubjectRolePair> pairs = assertValidACLReplyAndGetPairs(reply,
                getInitialServerACL() + 3, 3);

        assertACLContains(TEST_USER_1, Role.OWNER, pairs);
        assertACLContains(TEST_USER_2, Role.OWNER, pairs);
        assertACLContains(TEST_USER_4, Role.EDITOR, pairs);
    }

    @Test(expected = ExNoPerm.class) // thrown when the editor attempts to add a user
    public void shouldReturnExNoPermIfNonOwnerAttemptsToAddACLEntry()
            throws Exception
    {
        setupMockVerkehrToSuccessfullyPublish();

        // add the owner
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_1, Role.OWNER);

        // add an editor
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_2, Role.EDITOR);

        // get the editor to try and make some role changes
        try {
            shareFolderThroughSP(TEST_USER_2, TEST_SID_1, TEST_USER_4, Role.EDITOR);
        } catch (Exception e) {
            // make sure we clean up after uncommitted transaction(s)
            _transaction.handleException();
            throw e;
        }
    }

    @Test
    public void shouldAllowAnyRequesterWithOwnerRoleToDeleteARoleForTheStoreAndNotifyRequesterAsWellAsAllAffectedUsersOfACLChange()
            throws Exception
    {
        Set<String> published = setupMockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // add the owner
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_1, Role.OWNER);
        published.clear(); // don't care

        // add a second person (as owner)
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_2, Role.OWNER);
        published.clear(); // don't care

        // add a third person (as editor)
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_3, Role.EDITOR);
        published.clear(); // don't care

        // now have the second guy delete the third

        sessionUser.set(TEST_USER_2);
        service.deleteACL(TEST_SID_1.toPB(), Arrays.asList(TEST_USER_3.toString())).get();

        // expect first, second and third guy all to be notified

        assertEquals(3, published.size());
        assertTrue(published.contains(TEST_USER_1.toString()));
        assertTrue(published.contains(TEST_USER_2.toString()));
        assertTrue(published.contains(TEST_USER_3.toString()));

        // have the first guy get his acl

        List<PBSubjectRolePair> pairs;

        sessionUser.set(TEST_USER_1);
        GetACLReply reply = service.getACL(0L).get();

        // this guy has seen _all_ the updates, so he should see an epoch of 4
        pairs = assertValidACLReplyAndGetPairs(reply, getInitialServerACL() + 4, 2);

        assertACLContains(TEST_USER_1, Role.OWNER, pairs);

        // now have the deleted guy get his acl

        sessionUser.set(TEST_USER_3);
        reply = service.getACL(0L).get();

        // only two updates have affected him, so he should have an epoch of 2
        assertEquals(getInitialServerACL() + 2, reply.getEpoch());
        // since all entries have been deleted for this user, there should be no entries for him
        assertEquals(0, reply.getStoreAclCount());
    }

    @Test
    @Ignore("known bug - waiting until db refactor")
    // FIXME: we notify a user on 'deleting' a role for them, even though this deletion is invalid
    public void shouldAlwaysReturnSuccessfullyEvenIfDeleteACLCallIsMadeForAUserThatDoesntHaveAnACLEntry()
            throws Exception
    {
        Set<String> published = setupMockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // add the owner
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_1, Role.OWNER);
        published.clear(); // don't care

        // now attempt to delete someone for whom the role doesn't exist

        sessionUser.set(TEST_USER_1);
        service.deleteACL(TEST_SID_1.toPB(), Arrays.asList(TEST_USER_2.toString())).get();

        assertEquals(1, published.size());
        assertTrue(published.contains(TEST_USER_1.toString()));

        sessionUser.set(TEST_USER_1);
        GetACLReply reply = service.getACL(0L).get();

        // epoch shouldn't be bumped on a deletion of a person that doesn't exist
        List<PBSubjectRolePair> pairs = assertValidACLReplyAndGetPairs(reply,
                getInitialServerACL() + 1, 1);

        assertACLContains(TEST_USER_1, Role.OWNER, pairs);
    }

    @Test(expected = ExNoPerm.class)
    public void shouldReturnExNoPermIfNonOwnerAttemptsToDeleteACLEntry()
            throws Exception
    {
        setupMockVerkehrToSuccessfullyPublish();

        // add the owner
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_1, Role.OWNER);

        // add an editor
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_2, Role.EDITOR);

        // get the editor to try to delete the owner
        try {
            sessionUser.set(TEST_USER_2);
            service.deleteACL(TEST_SID_1.toPB(), Arrays.asList(TEST_USER_1.toString())).get();
        } catch (Exception e) {
            _transaction.handleException();
            throw e;
        }
    }

    @Test
    public void shouldAllowAnyUserWithAnyRoleToGetACL()
            throws Exception
    {
        setupMockVerkehrToSuccessfullyPublish();

        // add the owner for store # 1
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_1, Role.OWNER);

        // add the owner for store # 2
        shareFolderThroughSP(TEST_USER_2, TEST_SID_2, TEST_USER_2, Role.OWNER);

        // add an editor for store # 1
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_3, Role.EDITOR);

        // add an editor for store # 2
        shareFolderThroughSP(TEST_USER_2, TEST_SID_2, TEST_USER_3, Role.EDITOR);

        // now have the editor do a getacl call

        sessionUser.set(TEST_USER_3);
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
                assertACLContains(TEST_USER_1, Role.OWNER, pairs);
                assertACLContains(TEST_USER_3, Role.EDITOR, pairs);
            } else if (aclSID.equals(TEST_SID_2)) {
                assertEquals(2, storeACL.getSubjectRoleCount());
                List<PBSubjectRolePair> pairs = storeACL.getSubjectRoleList();
                assertACLContains(TEST_USER_2, Role.OWNER, pairs);
                assertACLContains(TEST_USER_3, Role.EDITOR, pairs);
            } else {
                fail("unexpected store acl for s:" + aclSID);
            }
        }
    }

    @Test
    public void shouldAllowUpdateACLToChangeACLsAlreadyInDatabase()
            throws Exception
    {
        Set<String> published = setupMockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // add the owner for store # 1
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_1, Role.OWNER);

        // add user 3 as editor for store # 1
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_3, Role.EDITOR);

        published.clear(); // clear out notifications from sharing

        // update ACL for user 3 as user 1
        sessionUser.set(TEST_USER_1);
        service.updateACL(TEST_SID_1.toPB(), makePair(TEST_USER_3, Role.OWNER));

        // check that notifications were published on update
        assertEquals(2, published.size());
        assertTrue(published.contains(TEST_USER_1.toString()));
        assertTrue(published.contains(TEST_USER_3.toString()));

        // verify user 3 has updated ACL in place
        sessionUser.set(TEST_USER_3);
        GetACLReply reply = service.getACL(0L).get();

        // epoch for this guy should be 2 (started at 0, added as editor then as owner)
        assertEquals(getInitialServerACL() + 2, reply.getEpoch());

        // he should have 1 store entry
        assertEquals(1, reply.getStoreAclCount());

        PBStoreACL acl = reply.getStoreAcl(0);
        assertEquals(2, acl.getSubjectRoleCount());
        List<PBSubjectRolePair> pairs = acl.getSubjectRoleList();
        assertACLContains(TEST_USER_1, Role.OWNER, pairs);
        assertACLContains(TEST_USER_3, Role.OWNER, pairs);
    }

    @Test (expected = ExNoPerm.class) // thrown when owner tries to update nonexistent ACL entry
    public void shouldForbidUpdateACLFromChangingACLsNotInDatabase()
            throws Exception
    {
        Set<String> published = setupMockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // add the owner for store # 1
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_1, Role.OWNER);
        published.clear(); // throw away this notification

        // update ACL for user 3 as user 1
        sessionUser.set(TEST_USER_1);
        Exception ex = null;
        try {
            // should fail with ExNoPerm
            service.updateACL(TEST_SID_1.toPB(), makePair(TEST_USER_3, Role.OWNER));
        } catch (Exception e) {
            _transaction.handleException();
            ex = e;
        }

        // ensure that nothing was published
        assertEquals(0, published.size());

        // check that user 3 still has no ACLs set in the db
        sessionUser.set(TEST_USER_3);
        GetACLReply reply = service.getACL(0L).get();
        assertEquals(getInitialServerACL(), reply.getEpoch());
        assertEquals(0, reply.getStoreAclCount());

        // rethrow exception caught above
        if (ex != null) throw ex;
    }

    @Test (expected = ExNoPerm.class) // thrown when editor tries to update ACL entry
    public void shouldForbidUpdateACLFromUserWithNonOwnerPermissions()
            throws Exception
    {
        Set<String> published = setupMockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // add the owner for store # 1
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_1, Role.OWNER);

        // add user 3 as editor for store # 1
        shareFolderThroughSP(TEST_USER_1, TEST_SID_1, TEST_USER_3, Role.EDITOR);

        published.clear(); // throw away these notifications

        // try to edit user 1's ACL entry for store 1 as user 3
        sessionUser.set(TEST_USER_3);
        Exception ex = null;
        try {
            // should fail with ExNoPerm
            service.updateACL(TEST_SID_1.toPB(), makePair(TEST_USER_1, Role.EDITOR));
        } catch (Exception e) {
            _transaction.handleException();
            ex = e;
        }

        // ensure that nothing was published
        assertEquals(0, published.size());

        // check that user 3 only has editor permissions
        sessionUser.set(TEST_USER_3);
        GetACLReply reply = service.getACL(0L).get();
        assertEquals(getInitialServerACL() + 1, reply.getEpoch());
        assertEquals(1, reply.getStoreAclCount());
        List<PBSubjectRolePair> pairs = reply.getStoreAcl(0).getSubjectRoleList();
        assertACLContains(TEST_USER_1, Role.OWNER, pairs);
        assertACLContains(TEST_USER_3, Role.EDITOR, pairs);

        // rethrow exception caught above
        if (ex != null) throw ex;
    }
}
