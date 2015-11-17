/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.activity.ActivityLog;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.DeviceToUserMapper;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.admin.EIGetActivities;
import com.aerofs.daemon.lib.db.ActivityLogDatabase;
import com.aerofs.daemon.lib.db.IActivityLogDatabase;
import com.aerofs.daemon.lib.db.IActivityLogDatabase.ActivityRow;
import com.aerofs.daemon.lib.db.UserAndDeviceNameDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.FullName;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.InMemoryCoreDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Ritual.GetActivitiesReply.PBActivity;
import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.proto.Sp.GetDeviceInfoReply.Builder;
import com.aerofs.proto.Sp.GetDeviceInfoReply.PBDeviceInfo;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Injector;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.sql.SQLException;
import java.util.Set;

import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.CREATION_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.DELETION_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.MODIFICATION_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.MOVEMENT_VALUE;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestHdGetActivities extends AbstractTest
{
    @Mock IDBIterator<ActivityRow> dbiter;
    @Mock SPBlockingClient sp;
    @Mock InjectableSPBlockingClientFactory factSP;
    @Mock CfgLocalUser cfgLocalUser;
    @Mock CfgLocalDID cfgLocalDID;
    @Mock DirectoryService ds;
    @Mock DeviceToUserMapper d2u;
    @Mock TokenManager tokenManager;
    @Mock Token tk;
    @Mock TCB tcb;
    @Mock TransManager tm;
    @Mock Trans t;
    @Mock UserAndDeviceNameDatabase udndb;
    @Mock NativeVersionControl nvc;
    @Mock StoreHierarchy stores;
    @Mock IMapSIndex2SID sidx2sid;

    HdGetActivities hd;

    InMemoryCoreDBCW dbcw = new InMemoryCoreDBCW();
    IActivityLogDatabase aldb;
    ActivityLog al;

    EIGetActivities ev;

    DID did1 = new DID(UniqueID.generate());
    DID did2 = new DID(UniqueID.generate());
    DID did3 = new DID(UniqueID.generate());

    UserID me = UserID.fromInternal("hahaah");

    private void addActivity(int type, Path from, Path to, DID ... dids)
            throws SQLException
    {
        Set<DID> set = Sets.newTreeSet();
        for (DID did : dids) set.add(did);

        addActivity(new SOID(rootSidx, new OID(UniqueID.generate())), type, from, to, set);
    }

    private void addActivity(SOID soid, int type, Path from, Path to, Set<DID> dids)
            throws SQLException
    {
        aldb.insertActivity_(soid, type, from, to, dids, null);
    }

    private static SIndex rootSidx = new SIndex(1);
    private static SID rootSID = SID.rootSID(UserID.fromInternal("foo@bar.baz"));
    private Path mkpath(String path) { return Path.fromString(rootSID, path); }

    @SuppressWarnings("unchecked")
    @Before
    public void setup()
            throws Exception
    {
        when(stores.getPhysicalRoot_(any(SIndex.class))).thenReturn(rootSidx);
        when(sidx2sid.get_(rootSidx)).thenReturn(rootSID);
        when(sidx2sid.getNullable_(rootSidx)).thenReturn(rootSID);
        when(sidx2sid.getLocalOrAbsent_(rootSidx)).thenReturn(rootSID);
        aldb = new ActivityLogDatabase(dbcw, stores, sidx2sid);

        dbcw.init_();
        addActivity(CREATION_VALUE, mkpath("a"), null, did1, did2, did3);
        addActivity(MOVEMENT_VALUE, mkpath("a"), mkpath("b"), did1, did2, did3);

        Injector inj = mock(Injector.class);
        when(inj.getInstance(NativeVersionControl.class)).thenReturn(nvc);
        CfgUsePolaris usePolaris = mock(CfgUsePolaris.class);
        when(usePolaris.get()).thenReturn(false);
        al = new ActivityLog(ds, aldb, cfgLocalDID, usePolaris, inj);
        UserAndDeviceNames didinfo = new UserAndDeviceNames(cfgLocalUser, tokenManager,  tm, d2u,
                udndb, factSP, new ElapsedTimer.Factory());

        hd = new HdGetActivities(al, ds, d2u, didinfo, cfgLocalUser, cfgLocalDID, sidx2sid);

        when(cfgLocalUser.get()).thenReturn(me);

        when(tokenManager.acquire_(any(Cat.class), anyString())).thenReturn(tk);
        when(tk.pseudoPause_(anyString())).thenReturn(tcb);

        when(tm.begin_()).thenReturn(t);

        when(factSP.create()).thenReturn(sp);
        when(sp.signInRemote()).thenReturn(sp);

        when(sp.getDeviceInfo(anyCollectionOf(ByteString.class))).thenAnswer(invocation -> {
            Iterable<ByteString> dids = (Iterable<ByteString>) invocation.getArguments()[0];

            Builder bdReply = GetDeviceInfoReply.newBuilder();
            if (dids != null) {
                for (@SuppressWarnings("unused") ByteString did : dids) {
                    bdReply.addDeviceInfo(PBDeviceInfo.getDefaultInstance());
                }
            }
            return bdReply.build();
        });
    }

    @Test
    public void shouldHaveNoUnresolvedDevicesAfterContactingSPSucceeds()
            throws Exception
    {
        run(false);

        assertTrue(!ev._hasUnresolvedDevices);
    }

    @Test
    public void shouldHaveUnresolvedDevicesAfterContactingSPFails()
            throws Exception
    {
        when(sp.getDeviceInfo(anyCollectionOf(ByteString.class))).thenThrow(new Exception());

        run(false);

        assertTrue(ev._hasUnresolvedDevices);
    }

    @Test
    public void shouldReturnMultiplePagesWithSmallResultLimits()
            throws Exception
    {
        ev = new EIGetActivities(false, null, 1, null);
        hd.handleThrows_(ev);
        assertTrue(ev._activities.size() == 1);
        assertTrue(ev._replyPageToken != null);

        ev = new EIGetActivities(false, ev._replyPageToken, 1, null);
        hd.handleThrows_(ev);
        assertTrue(ev._activities.size() == 1);
        assertTrue(ev._replyPageToken != null);

        ev = new EIGetActivities(false, ev._replyPageToken, 1, null);
        hd.handleThrows_(ev);
        assertTrue(ev._activities.size() == 0);
        assertTrue(ev._replyPageToken == null);
    }

    @Test
    public void shouldReturnSinglePageWithLargeResultLimits()
            throws Exception
    {
        run(false);
        assertTrue(ev._activities.size() == 2);
        assertTrue(ev._replyPageToken == null);
    }

    ////////
    // Note: The following tests rely on string comparision and thus fragile. But this is the only
    // approach WW could think of.

    @Test
    public void shouldShowUnknownDevicesOnly()
            throws Exception
    {
        run(false);
        assertTrue(firstMsg().startsWith("3 unknown devices renamed"));
    }

    @Test
    public void shouldShowUnknownDevicesOnlyInBriefMode()
            throws Exception
    {
        run(true);
        assertTrue(firstMsg().startsWith("3 unknown devices renamed"));
    }

    @Test
    public void shouldShowYourUnknownDeviceAnd2UknonwDevicesOnly()
            throws Exception
    {
        when(d2u.getUserIDForDIDNullable_(did2)).thenReturn(me);
        run(false);
        assertTrue(firstMsg().startsWith("You (on 1 unknown device) and 2 unknown devices renamed"));
    }

    @Test
    public void shouldShowYourAnd2UknonwDevicesOnlyInBriefMode()
            throws Exception
    {
        when(d2u.getUserIDForDIDNullable_(did2)).thenReturn(me);
        run(true);
        assertTrue(firstMsg().startsWith("You and 2 unknown devices renamed"));
    }

    @Test
    public void shouldShowYourUnknownDeviceAndUserEmailAndUknonwDevicesOnly()
            throws Exception
    {
        when(d2u.getUserIDForDIDNullable_(did2)).thenReturn(me);
        when(d2u.getUserIDForDIDNullable_(did1)).thenReturn(UserID.fromInternal("user@gmail"));
        run(false);
        assertTrue(firstMsg().startsWith("You (on 1 unknown device) and user@gmail and 1 unknown device renamed"));
    }

    @Test
    public void shouldShowYourAndOthersOnlyInBriefMode()
            throws Exception
    {
        when(d2u.getUserIDForDIDNullable_(did2)).thenReturn(me);
        when(d2u.getUserIDForDIDNullable_(did1)).thenReturn(UserID.fromInternal("user@gmail"));
        run(true);
        assertTrue(firstMsg().startsWith("You and others renamed"));
    }

    @Test
    public void shouldShowFullNameOnly()
            throws Exception
    {
        addActivity(CREATION_VALUE, mkpath("a"), null, did1);
        when(d2u.getUserIDForDIDNullable_(did1)).thenReturn(UserID.fromInternal("user@gmail"));
        when(udndb.getUserNameNullable_(UserID.fromInternal("user@gmail"))).thenReturn(new FullName("A", "B"));
        run(false);
        printResult();
        assertTrue(firstMsg().startsWith("A B added"));
    }

    @Test
    public void shouldShowFirstNameOnlyInBriefMode()
            throws Exception
    {
        addActivity(CREATION_VALUE, mkpath("a"), null, did1);
        when(d2u.getUserIDForDIDNullable_(did1)).thenReturn(UserID.fromInternal("user@gmail"));
        when(udndb.getUserNameNullable_(UserID.fromInternal("user@gmail"))).thenReturn(new FullName("A", "B"));
        run(true);
        printResult();
        assertTrue(firstMsg().startsWith("A added"));
    }

    @Test
    public void shouldShowFullNameAndUnknownDevicesOnly()
            throws Exception
    {
        when(d2u.getUserIDForDIDNullable_(did1)).thenReturn(UserID.fromInternal("user@gmail"));
        when(udndb.getUserNameNullable_(UserID.fromInternal("user@gmail"))).thenReturn(new FullName("A", "B"));
        run(false);
        assertTrue(firstMsg().startsWith("A B and 2 unknown devices renamed"));
    }

    @Test
    public void shouldShowFirstNameAndUnknownDevicesOnlyInBriefMode()
            throws Exception
    {
        when(d2u.getUserIDForDIDNullable_(did1)).thenReturn(UserID.fromInternal("user@gmail"));
        when(udndb.getUserNameNullable_(UserID.fromInternal("user@gmail"))).thenReturn(new FullName("A", "B"));
        run(true);
        assertTrue(firstMsg().startsWith("A and 2 unknown devices"));
    }

    @Test
    public void shouldShowMyDeviceNames()
            throws Exception
    {
        when(d2u.getUserIDForDIDNullable_(did2)).thenReturn(me);
        when(udndb.getDeviceNameNullable_(did2)).thenReturn("DEV");
        run(false);
        assertTrue(firstMsg().startsWith("You (on DEV) and 2 unknown devices"));
    }

    @Test
    public void shouldNotShowMyDeviceNamesInBriefMode()
            throws Exception
    {
        when(d2u.getUserIDForDIDNullable_(did2)).thenReturn(me);
        when(udndb.getDeviceNameNullable_(did2)).thenReturn("DEV");
        run(true);
        assertTrue(firstMsg().startsWith("You and 2 unknown devices"));
    }

    @Test
    public void shouldNotShowOtherUsersDeviceNames()
            throws Exception
    {
        when(d2u.getUserIDForDIDNullable_(did2)).thenReturn(UserID.fromInternal("user@mail"));
        when(udndb.getDeviceNameNullable_(did2)).thenReturn("DEV");
        run(false);
        assertTrue(!firstMsg().contains("DEV"));
    }

    @Test
    public void shouldShowTargetAndDestinationOnRenaming()
            throws Exception
    {
        run(false);
        assertTrue(firstMsg().endsWith("renamed file or folder \"a\" to \"b\""));
    }

    @Test
    public void shouldShowTargetAndDestinationParentOnMovement()
            throws Exception
    {
        addActivity(MOVEMENT_VALUE, mkpath("a"), mkpath("b/c"), did1);

        run(false);
        assertTrue(firstMsg().endsWith("moved file or folder \"a\" to \"b/c\""));
    }

    @Test
    public void shouldNotShowTargetOnMovementInBriefMode()
            throws Exception
    {
        run(true);
        assertTrue(firstMsg().endsWith("renamed file or folder \"b\""));
    }

    // the modification event should be suppressed by creation
    @Test
    public void shouldNotShowModificationWithCreation()
            throws Exception
    {
        addActivity(CREATION_VALUE | MODIFICATION_VALUE, mkpath("a"), null, did1);

        run(true);
        assertTrue(firstMsg().endsWith("unknown device added file or folder \"a\""));
    }

    // the movemnet event should be suppressed by deletion
    @Test
    public void shouldNotShowMovementWithDeletion()
            throws Exception
    {
        addActivity(DELETION_VALUE | MOVEMENT_VALUE, mkpath("a"), mkpath("b"), did1);

        run(true);
        assertTrue(firstMsg().endsWith("unknown device deleted file or folder \"a\""));
    }

    @Test
    public void shouldIgnoreActivityInExpelledStore() throws Exception
    {
        addActivity(new SOID(new SIndex(42), OID.generate()), CREATION_VALUE,
                mkpath("expelled/foobar"), null, ImmutableSet.of(did1));

        run(true);
        assertTrue(firstMsg().endsWith("renamed file or folder \"b\""));
    }


    void printResult()
    {
        for (PBActivity a : ev._activities) {
            l.warn(a.getMessage());
        }
    }

    private void run(boolean brief)
            throws Exception
    {
        ev = new EIGetActivities(brief, null, 100, null);
        hd.handleThrows_(ev);
    }

    private String firstMsg()
    {
        return ev._activities.get(0).getMessage();
    }
}
