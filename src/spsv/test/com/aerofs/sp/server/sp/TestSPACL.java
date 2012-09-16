package com.aerofs.sp.server.sp;

import com.aerofs.lib.C;
import com.aerofs.lib.Role;
import com.aerofs.lib.SubjectRolePair;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.aerofs.lib.Util.getRootSID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;

public class TestSPACL extends AbstractSPUserBasedTest
{
    // Another random person (in addition to the ones created by our parent).
    private static final String TEST_USER_4_NAME = "USER_4";

    private static final SID TEST_SID_1 = new SID(UniqueID.generate());
    private static final SID TEST_SID_2 = new SID(UniqueID.generate());

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
    private void assertACLContains(String subject, Role expectedRole, List<PBSubjectRolePair> pairs)
    {
        boolean found = false;

        for (PBSubjectRolePair pair : pairs) {
            try {
                String currentSubject = pair.getSubject();
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

    //
    // TESTS
    //

    @Test
    public void shouldAlwaysAllowAnACLEntryToBeAddedIfNoACLEntryExistsForThatStore()
            throws Exception
    {
        setupMockVerkehrToSuccessfullyPublish();

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_1_NAME, Role.OWNER)).get();

        GetACLReply getAcl = service.getACL(0L).get();

        assertEquals(getInitialServerACL() + 1, getAcl.getEpoch());
        assertEquals(1, getAcl.getStoreAclCount());
        assertEquals(TEST_SID_1, new SID(getAcl.getStoreAcl(0).getStoreId()));
        assertEquals(1, getAcl.getStoreAcl(0).getSubjectRoleCount());
        assertEquals(TEST_USER_1_NAME, getAcl.getStoreAcl(0).getSubjectRole(0).getSubject());
        assertEquals(Role.OWNER, Role.fromPB(getAcl.getStoreAcl(0).getSubjectRole(0).getRole()));

        verify(verkehrPublisher).publish_(eq(TEST_USER_1_NAME), any(byte[].class));
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

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(getRootSID(TEST_USER_2_NAME).toPB(), makePair(TEST_USER_3_NAME,
                Role.OWNER)).get();

        // clear this set out, because I don't care about what happens prior to the last call

        published.clear();

        // now the actual owner comes along and tries to do the same

        sessionUser.setUser(TEST_USER_2_NAME);
        service.setACL(getRootSID(TEST_USER_2_NAME).toPB(), makePair(TEST_USER_2_NAME,
                Role.OWNER)).get();

        // we still expect both epoch of 1) the owner and 2) the person who was flushed to be
        // updated and a notification published to them

        // FIXME: we do not properly notify the flushed users!!!
        // assertEquals(2, published.size());
        // assertTrue(published.contains(TEST_USER_3_NAME));
        assertTrue(published.contains(TEST_USER_2_NAME));

        // we should see only one entry for the second user

        sessionUser.setUser(TEST_USER_2_NAME);
        GetACLReply reply = service.getACL(0L).get();

        List<PBSubjectRolePair> pairs = assertValidACLReplyAndGetPairs(reply,
                getInitialServerACL() + 1, 1);

        assertACLContains(TEST_USER_2_NAME, Role.OWNER, pairs);
    }

    @Test
    public void shouldAllowAnyRequesterWithOwnerRoleToSetARoleForTheStoreAndNotifyRequesterAsWellAsAllAffectedUsersOfACLChange()
            throws Exception
    {
        Set<String> published = setupMockVerkehrToSuccessfullyPublishAndStoreSubscribers();

        // add yourself

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_1_NAME, Role.OWNER)).get();

        assertEquals(1, published.size());
        assertTrue(published.contains(TEST_USER_1_NAME));
        published.clear();

        // add the second person

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_2_NAME, Role.OWNER)).get();

        assertEquals(2, published.size());
        assertTrue(published.contains(TEST_USER_1_NAME));
        assertTrue(published.contains(TEST_USER_2_NAME));
        published.clear();

        // now lets see if the other person can add a third person

        sessionUser.setUser(TEST_USER_2_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_4_NAME, Role.EDITOR)).get();

        assertEquals(2, published.size());
        assertTrue(published.contains(TEST_USER_1_NAME));
        assertTrue(published.contains(TEST_USER_2_NAME));
        // IMPORTANT: user 3 doesn't exist in the user table, so no notification can be published
        // to him!
        published.clear();

        // now let's see what the acls are like
        sessionUser.setUser(TEST_USER_1_NAME);
        GetACLReply reply = service.getACL(0L).get();

        List<PBSubjectRolePair> pairs = assertValidACLReplyAndGetPairs(reply,
                getInitialServerACL() + 3, 3);

        assertACLContains(TEST_USER_1_NAME, Role.OWNER, pairs);
        assertACLContains(TEST_USER_2_NAME, Role.OWNER, pairs);
        assertACLContains(TEST_USER_4_NAME, Role.EDITOR, pairs);
    }

    @Test(expected = ExNoPerm.class) // thrown when the editor attempts to add a user
    public void shouldReturnExNoPermIfNonOwnerAttemptsToAddACLEntry()
            throws Exception
    {
        setupMockVerkehrToSuccessfullyPublish();

        // add the owner

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_1_NAME, Role.OWNER)).get();

        // add an editor

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_2_NAME, Role.EDITOR)).get();

        // get the editor to try and make some role changes
        try {
            sessionUser.setUser(TEST_USER_2_NAME);
            service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_4_NAME, Role.EDITOR)).get();
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

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_1_NAME, Role.OWNER)).get();

        published.clear(); // don't care

        // add a second person (as owner)

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_2_NAME, Role.OWNER)).get();

        published.clear(); // don't care

        // add a third person (as editor)

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_3_NAME, Role.EDITOR)).get();

        published.clear(); // don't care

        // now have the second guy delete the third

        sessionUser.setUser(TEST_USER_2_NAME);
        service.deleteACL(TEST_SID_1.toPB(), Arrays.asList(TEST_USER_3_NAME)).get();

        // expect first, second and third guy all to be notified

        assertEquals(3, published.size());
        assertTrue(published.contains(TEST_USER_1_NAME));
        assertTrue(published.contains(TEST_USER_2_NAME));
        assertTrue(published.contains(TEST_USER_3_NAME));

        // have the first guy get his acl

        List<PBSubjectRolePair> pairs;

        sessionUser.setUser(TEST_USER_1_NAME);
        GetACLReply reply = service.getACL(0L).get();

        // this guy has seen _all_ the updates, so he should see an epoch of 4
        pairs = assertValidACLReplyAndGetPairs(reply, getInitialServerACL() + 4, 2);

        assertACLContains(TEST_USER_1_NAME, Role.OWNER, pairs);

        // now have the deleted guy get his acl

        sessionUser.setUser(TEST_USER_3_NAME);
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

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_1_NAME, Role.OWNER)).get();

        published.clear(); // don't care

        // now attempt to delete someone for whom the role doesn't exist

        sessionUser.setUser(TEST_USER_1_NAME);
        service.deleteACL(TEST_SID_1.toPB(), Arrays.asList(TEST_USER_2_NAME)).get();

        assertEquals(1, published.size());
        assertTrue(published.contains(TEST_USER_1_NAME));

        sessionUser.setUser(TEST_USER_1_NAME);
        GetACLReply reply = service.getACL(0L).get();

        // epoch shouldn't be bumped on a deletion of a person that doesn't exist
        List<PBSubjectRolePair> pairs = assertValidACLReplyAndGetPairs(reply,
                getInitialServerACL() + 1, 1);

        assertACLContains(TEST_USER_1_NAME, Role.OWNER, pairs);
    }

    @Test(expected = ExNoPerm.class)
    public void shouldReturnExNoPermIfNonOwnerAttemptsToDeleteACLEntry()
            throws Exception
    {
        setupMockVerkehrToSuccessfullyPublish();

        // add the owner

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_1_NAME, Role.OWNER)).get();

        // add an editor

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_2_NAME, Role.EDITOR)).get();

        // get the editor to try to delete the owner
        try {
            sessionUser.setUser(TEST_USER_2_NAME);
            service.deleteACL(TEST_SID_1.toPB(), Arrays.asList(TEST_USER_1_NAME)).get();
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

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_1_NAME, Role.OWNER)).get();

        // add the owner for store # 2

        sessionUser.setUser(TEST_USER_2_NAME);
        service.setACL(TEST_SID_2.toPB(), makePair(TEST_USER_2_NAME, Role.OWNER)).get();

        // add an editor for store # 1

        sessionUser.setUser(TEST_USER_1_NAME);
        service.setACL(TEST_SID_1.toPB(), makePair(TEST_USER_3_NAME, Role.EDITOR)).get();

        // add an editor for store # 2

        sessionUser.setUser(TEST_USER_2_NAME);
        service.setACL(TEST_SID_2.toPB(), makePair(TEST_USER_3_NAME, Role.EDITOR)).get();

        // now have the editor do a getacl call

        sessionUser.setUser(TEST_USER_3_NAME);
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
                assertACLContains(TEST_USER_1_NAME, Role.OWNER, pairs);
                assertACLContains(TEST_USER_3_NAME, Role.EDITOR, pairs);
            } else if (aclSID.equals(TEST_SID_2)) {
                assertEquals(2, storeACL.getSubjectRoleCount());
                List<PBSubjectRolePair> pairs = storeACL.getSubjectRoleList();
                assertACLContains(TEST_USER_2_NAME, Role.OWNER, pairs);
                assertACLContains(TEST_USER_3_NAME, Role.EDITOR, pairs);
            } else {
                fail("unexpected store acl for s:" + aclSID);
            }
        }
    }

    private List<PBSubjectRolePair> makePair(String subject, Role role)
    {
        ArrayList<PBSubjectRolePair> pair = new ArrayList<PBSubjectRolePair>();
        pair.add(new SubjectRolePair(subject, role).toPB());
        return pair;
    }
}
