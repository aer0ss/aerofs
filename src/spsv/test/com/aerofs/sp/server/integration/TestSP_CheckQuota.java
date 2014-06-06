/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Sp.CheckQuotaCall.PBStoreUsage;
import com.aerofs.proto.Sp.CheckQuotaReply;
import com.aerofs.proto.Sp.CheckQuotaReply.PBStoreShouldCollect;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class TestSP_CheckQuota extends AbstractSPFolderTest
{
    private User admin, user1, user2;
    private SID sidAll = SID.generate();
    private SID sidA1 = SID.generate();
    private SID sid12 = SID.generate();
    private SID sidA,sid1, sid2;

    @Before
    public void setUp() throws Exception
    {
        // set up users
        sqlTrans.begin();
        admin = saveUser();
        user1 = saveUser();
        user2 = saveUser();
        admin.setLevel(AuthorizationLevel.ADMIN);
        sqlTrans.commit();

        // set up share between all three users
        shareFolder(admin, sidAll, user1, Permissions.allOf(Permission.WRITE));
        shareFolder(admin, sidAll, user2, Permissions.allOf(Permission.WRITE));
        joinSharedFolder(user1, sidAll);
        joinSharedFolder(user2, sidAll);

        // set up share between admin and user1
        shareFolder(admin, sidA1, user1, Permissions.allOf(Permission.WRITE));
        joinSharedFolder(user1, sidA1);

        // set up share between user1 and user2
        shareFolder(user1, sid12, user2, Permissions.allOf(Permission.WRITE));
        joinSharedFolder(user2, sid12);

        // get SIDs of user root stores
        sidA = SID.rootSID(admin.id());
        sid1 = SID.rootSID(user1.id());
        sid2 = SID.rootSID(user2.id());

        SharedFolder storeA = factSharedFolder.create(sidA);
        SharedFolder store1 = factSharedFolder.create(sid1);
        SharedFolder store2 = factSharedFolder.create(sid2);
        SharedFolder storeAll = factSharedFolder.create(sidAll);
        SharedFolder storeA1 = factSharedFolder.create(sidA1);
        SharedFolder store12 = factSharedFolder.create(sid12);

        // sanity check store membership
        sqlTrans.begin();

        assertTrue(Sets.newHashSet(admin.getSharedFolders())
                .equals(Sets.newHashSet(storeA, storeAll, storeA1)));
        assertTrue(Sets.newHashSet(user1.getSharedFolders())
                .equals(Sets.newHashSet(store1, storeAll, storeA1, store12)));
        assertTrue(Sets.newHashSet(user2.getSharedFolders())
                .equals(Sets.newHashSet(store2, storeAll, store12)));

        assertTrue(Sets.newHashSet(storeA.getAllUsersExceptTeamServers())
                .equals(Sets.newHashSet(admin)));
        assertTrue(Sets.newHashSet(store1.getAllUsersExceptTeamServers())
                .equals(Sets.newHashSet(user1)));
        assertTrue(Sets.newHashSet(store2.getAllUsersExceptTeamServers())
                .equals(Sets.newHashSet(user2)));
        assertTrue(Sets.newHashSet(storeAll.getAllUsersExceptTeamServers())
                .equals(Sets.newHashSet(admin, user1, user2)));
        assertTrue(Sets.newHashSet(storeA1.getAllUsersExceptTeamServers())
                .equals(Sets.newHashSet(admin, user1)));
        assertTrue(Sets.newHashSet(store12.getAllUsersExceptTeamServers())
                .equals(Sets.newHashSet(user1, user2)));

        // set org quota
        admin.getOrganization().setQuotaPerUser(100L);

        setSession(admin.getOrganization().getTeamServerUser());

        sqlTrans.commit();
    }

    private Map<SID, Boolean> getResponse(List<PBStoreUsage> request) throws Exception
    {
        CheckQuotaReply response = service.checkQuota(request).get();
        assertEquals(request.size(), response.getStoreCount());

        Map<SID, Boolean> shouldSync = new HashMap<SID, Boolean>(6);
        for (PBStoreShouldCollect store: response.getStoreList()) {
            shouldSync.put(new SID(store.getSid()), store.getCollectContent());
        }
        return shouldSync;
    }

    private void checkThatUsersWereEmailed(Set<User> users) throws Exception
    {
        // verify that notifications were sent to "users"
        for (User user : users) {
            verify(asyncEmailSender).sendPublicEmail(anyString(), anyString(),
                    eq(user.id().getString()), anyString(), anyString(), anyString(), anyString());
        }

        // verify that no notifications were sent to the other users
        for (User user : Sets.difference(Sets.newHashSet(admin, user1, user2), users)) {
            verify(asyncEmailSender, never()).sendPublicEmail(anyString(), anyString(),
                    eq(user.id().getString()), anyString(), anyString(), anyString(), anyString());
        }
    }

    @Test
    public void responseShouldContainSameStoresAsRequest() throws Exception
    {
        List<PBStoreUsage> request = new ArrayList<PBStoreUsage>(4);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(10L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(100L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(1000L).build());

        Map<SID, Boolean> response = getResponse(request);
        assertEquals(response.keySet(), Sets.newHashSet(sidA, sid1, sid2, sidAll));
    }

    @Test
    public void shouldContinueSyncAllIfNoQuotaSet() throws Exception
    {
        // Usages
        // A: 300
        // 1: 400
        // 2: 300
        List<PBStoreUsage> request = new ArrayList<PBStoreUsage>(6);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(100L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(100L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(100L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(100L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidA1).setBytesUsed(100L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid12).setBytesUsed(100L).build());

        sqlTrans.begin();
        admin.getOrganization().setQuotaPerUser(null);
        sqlTrans.commit();

        Map<SID, Boolean> shouldSync = getResponse(request);
        for (boolean should : shouldSync.values()) assertTrue(should);

        checkThatUsersWereEmailed(Sets.<User>newHashSet());
    }

    @Test
    public void shouldContinueSyncAllIfAllStoresAreEmpty() throws Exception
    {
        // Usages
        // A: 0
        // 1: 0
        // 2: 0
        List<PBStoreUsage> request = new ArrayList<PBStoreUsage>(6);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidA1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid12).setBytesUsed(0L).build());

        Map<SID, Boolean> shouldSync = getResponse(request);
        for (Boolean should : shouldSync.values()) assertTrue(should);

        checkThatUsersWereEmailed(Sets.<User>newHashSet());
    }

    @Test
    public void shouldNotSyncUser1RootStoreIfRootOverQuota() throws Exception
    {
        // Usages
        // A: 0
        // 1: 100
        // 2: 0
        List<PBStoreUsage> request = new ArrayList<PBStoreUsage>(6);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(100L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidA1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid12).setBytesUsed(0L).build());

        Map<SID, Boolean> shouldSync = getResponse(request);

        // only user 1's root should stop syncing
        assertEquals(true, shouldSync.get(sidA));
        assertEquals(false, shouldSync.get(sid1));
        assertEquals(true, shouldSync.get(sid2));
        assertEquals(true, shouldSync.get(sidAll));
        assertEquals(true, shouldSync.get(sidA1));
        assertEquals(true, shouldSync.get(sid12));

        checkThatUsersWereEmailed(Sets.<User>newHashSet(user1));
    }

    @Test
    public void shouldNotSyncUser1And2StoresIfTheirShareOverQuota() throws Exception
    {
        // Usages
        // A: 0
        // 1: 100
        // 2: 100
        List<PBStoreUsage> request = new ArrayList<PBStoreUsage>(6);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidA1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid12).setBytesUsed(100L).build());

        Map<SID, Boolean> shouldSync = getResponse(request);

        // shares with user 1 and user 2 should not be synced
        assertEquals(true, shouldSync.get(sidA));
        assertEquals(false, shouldSync.get(sid1));
        assertEquals(false, shouldSync.get(sid2));
        assertEquals(true, shouldSync.get(sidAll));
        assertEquals(true, shouldSync.get(sidA1));
        assertEquals(false, shouldSync.get(sid12));

        checkThatUsersWereEmailed(Sets.<User>newHashSet(user1, user2));
    }

    @Test
    public void shouldNotSyncAnyStoresIfMutualShareOverQuota() throws Exception
    {
        // Usages
        // A: 100
        // 1: 100
        // 2: 100
        List<PBStoreUsage> request = new ArrayList<PBStoreUsage>(6);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(100L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidA1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid12).setBytesUsed(0L).build());

        Map<SID, Boolean> shouldSync = getResponse(request);

        for (Boolean should : shouldSync.values()) assertFalse(should);

        checkThatUsersWereEmailed(Sets.<User>newHashSet(admin, user1, user2));
    }

    @Test
    public void shouldNotSyncUser1RootStoreIfSharesAndRootAddUpToQuota() throws Exception
    {
        // Usages
        // A: 30
        // 1: 100
        // 2: 30
        List<PBStoreUsage> request = new ArrayList<PBStoreUsage>(6);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(40L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidA1).setBytesUsed(30L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid12).setBytesUsed(30L).build());

        Map<SID, Boolean> shouldSync = getResponse(request);

        // stores with user 1 should not be synced
        assertEquals(true, shouldSync.get(sidA));
        assertEquals(false, shouldSync.get(sid1));
        assertEquals(true, shouldSync.get(sid2));
        assertEquals(true, shouldSync.get(sidAll));
        assertEquals(true, shouldSync.get(sidA1));
        assertEquals(true, shouldSync.get(sid12));

        checkThatUsersWereEmailed(Sets.<User>newHashSet(user1));
    }

    @Test
    public void shouldSyncToUser2StoresIfUnderQuota_1() throws Exception
    {
        // Usages
        // A: 100
        // 1: 100
        // 2: 70
        List<PBStoreUsage> request = new ArrayList<PBStoreUsage>(6);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(50L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidA1).setBytesUsed(50L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid12).setBytesUsed(0L).build());

        Map<SID, Boolean> shouldSync = getResponse(request);

        assertEquals(false, shouldSync.get(sidA));
        assertEquals(false, shouldSync.get(sid1));
        assertEquals(true, shouldSync.get(sid2));
        assertEquals(true, shouldSync.get(sidAll));
        assertEquals(false, shouldSync.get(sidA1));
        assertEquals(true, shouldSync.get(sid12));

        checkThatUsersWereEmailed(Sets.<User>newHashSet(admin, user1));
    }

    @Test
    public void shouldSyncToUser2StoresIfUnderQuota_2() throws Exception
    {
        // Usages
        // A: 100
        // 1: 100
        // 2: 70
        List<PBStoreUsage> request = new ArrayList<PBStoreUsage>(6);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(20L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(20L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(20L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(50L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidA1).setBytesUsed(30L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid12).setBytesUsed(0L).build());

        Map<SID, Boolean> shouldCollect = getResponse(request);

        assertEquals(false, shouldCollect.get(sidA));
        assertEquals(false, shouldCollect.get(sid1));
        assertEquals(true, shouldCollect.get(sid2));
        assertEquals(true, shouldCollect.get(sidAll));
        assertEquals(false, shouldCollect.get(sidA1));
        assertEquals(true, shouldCollect.get(sid12));

        checkThatUsersWereEmailed(Sets.<User>newHashSet(admin, user1));
    }

    @Test
    public void shouldEmailUsersIfOver80PercentOfQuota_1() throws Exception
    {
        // Usages
        // A: 85
        // 1: 85
        // 2: 85
        List<PBStoreUsage> request = new ArrayList<PBStoreUsage>(6);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(85L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidA1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid12).setBytesUsed(0L).build());

        Map<SID, Boolean> shouldCollect = getResponse(request);

        assertEquals(true, shouldCollect.get(sidA));
        assertEquals(true, shouldCollect.get(sid1));
        assertEquals(true, shouldCollect.get(sid2));
        assertEquals(true, shouldCollect.get(sidAll));
        assertEquals(true, shouldCollect.get(sidA1));
        assertEquals(true, shouldCollect.get(sid12));

        checkThatUsersWereEmailed(Sets.<User>newHashSet(admin, user1, user2));
    }

    @Test
    public void shouldEmailUsersIfOver80PercentOfQuota_2() throws Exception
    {
        // Usages
        // A: 85
        // 1: 85
        // 2: 85
        List<PBStoreUsage> request = new ArrayList<PBStoreUsage>(6);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(85L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(85L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(85L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidA1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid12).setBytesUsed(0L).build());

        Map<SID, Boolean> shouldCollect = getResponse(request);

        assertEquals(true, shouldCollect.get(sidA));
        assertEquals(true, shouldCollect.get(sid1));
        assertEquals(true, shouldCollect.get(sid2));
        assertEquals(true, shouldCollect.get(sidAll));
        assertEquals(true, shouldCollect.get(sidA1));
        assertEquals(true, shouldCollect.get(sid12));

        checkThatUsersWereEmailed(Sets.<User>newHashSet(admin, user1, user2));
    }

    @Test
    public void shouldEmailUsersIfOver80PercentOfQuota_3() throws Exception
    {
        // Usages
        // A: 60
        // 1: 90
        // 2: 90
        List<PBStoreUsage> request = new ArrayList<PBStoreUsage>(6);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(20L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(20L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(20L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(40L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidA1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid12).setBytesUsed(30L).build());

        Map<SID, Boolean> shouldCollect = getResponse(request);

        assertEquals(true, shouldCollect.get(sidA));
        assertEquals(true, shouldCollect.get(sid1));
        assertEquals(true, shouldCollect.get(sid2));
        assertEquals(true, shouldCollect.get(sidAll));
        assertEquals(true, shouldCollect.get(sidA1));
        assertEquals(true, shouldCollect.get(sid12));

        checkThatUsersWereEmailed(Sets.<User>newHashSet(user1, user2));
    }

    @Test
    public void shouldNotEmailUsersTwiceWhoHaveGoneOver80PercentOfQuota() throws Exception
    {
        // Usages
        // A: 60
        // 1: 90
        // 2: 90
        List<PBStoreUsage> request = new ArrayList<PBStoreUsage>(6);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(20L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(20L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(20L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(40L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidA1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid12).setBytesUsed(30L).build());

        Map<SID, Boolean> shouldCollect = getResponse(request);

        assertEquals(true, shouldCollect.get(sidA));
        assertEquals(true, shouldCollect.get(sid1));
        assertEquals(true, shouldCollect.get(sid2));
        assertEquals(true, shouldCollect.get(sidAll));
        assertEquals(true, shouldCollect.get(sidA1));
        assertEquals(true, shouldCollect.get(sid12));

        checkThatUsersWereEmailed(Sets.<User>newHashSet(user1, user2));

        reset(asyncEmailSender);

        // Usages
        // A: 60
        // 1: 90
        // 2: 90
        request = new ArrayList<PBStoreUsage>(6);
        request.add(PBStoreUsage.newBuilder().setSid(sidA).setBytesUsed(20L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid1).setBytesUsed(20L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid2).setBytesUsed(20L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidAll).setBytesUsed(40L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sidA1).setBytesUsed(0L).build());
        request.add(PBStoreUsage.newBuilder().setSid(sid12).setBytesUsed(30L).build());

        shouldCollect = getResponse(request);

        assertEquals(true, shouldCollect.get(sidA));
        assertEquals(true, shouldCollect.get(sid1));
        assertEquals(true, shouldCollect.get(sid2));
        assertEquals(true, shouldCollect.get(sidAll));
        assertEquals(true, shouldCollect.get(sidA1));
        assertEquals(true, shouldCollect.get(sid12));

        checkThatUsersWereEmailed(Sets.<User>newHashSet());
    }
}
