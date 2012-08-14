/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.admin;

import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.CREATION_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.DELETION_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.MODIFICATION_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.MOVEMENT_VALUE;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.sql.SQLException;
import java.util.Set;

import com.aerofs.daemon.lib.db.IActivityLogDatabase;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.aerofs.daemon.core.ActivityLog;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.DID2User;
import com.aerofs.daemon.core.syncstatus.SyncStatusSynchronizer;
import com.aerofs.daemon.event.admin.EIGetActivities;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.ActivityLogDatabase;
import com.aerofs.daemon.lib.db.IActivityLogDatabase.ActivityRow;
import com.aerofs.daemon.lib.db.UserAndDeviceNameDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.FullName;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.spsv.SPBlockingClient;
import com.aerofs.proto.Ritual.GetActivitiesReply.PBActivity;
import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.proto.Sp.GetDeviceInfoReply.Builder;
import com.aerofs.proto.Sp.GetDeviceInfoReply.PBDeviceInfo;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;

public class TestHdGetActivities extends AbstractTest
{
    @Mock IDBIterator<ActivityRow> dbiter;
    @Mock SPBlockingClient sp;
    @Mock SPBlockingClient.Factory factSP;
    @Mock CfgLocalUser cfgLocalUser;
    @Mock CfgLocalDID cfgLocalDID;
    @Mock DirectoryService ds;
    @Mock DID2User d2u;
    @Mock TransManager tm;
    @Mock Trans t;
    @Mock UserAndDeviceNameDatabase udndb;
    @Mock SyncStatusSynchronizer sync;

    HdGetActivities hd;

    InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
    IActivityLogDatabase aldb = new ActivityLogDatabase(dbcw.mockCoreDBCW());
    ActivityLog al;

    EIGetActivities ev;

    DID did1 = new DID(UniqueID.generate());
    DID did2 = new DID(UniqueID.generate());
    DID did3 = new DID(UniqueID.generate());

    String me = "hahaah";

    int idx;
    private void addActivity(int type, Path from, Path to, DID ... dids)
            throws SQLException
    {
        Set<DID> set = Sets.newTreeSet();
        for (DID did : dids) set.add(did);

        idx++;
        aldb.addActivity_(new SOID(new SIndex(idx), new OID(UniqueID.generate())), type, from, to,
                set, null);
    }

    @SuppressWarnings("unchecked")
    @Before
    public void setup()
            throws Exception
    {
        dbcw.init_();
        addActivity(CREATION_VALUE, new Path("a"), null, did1, did2, did3);
        addActivity(MOVEMENT_VALUE, new Path("a"), new Path("b"), did1, did2, did3);

        al = new ActivityLog(ds, aldb);
        hd = new HdGetActivities(al, ds, d2u, udndb, tm, cfgLocalUser, cfgLocalDID, factSP);

        when(cfgLocalUser.get()).thenReturn(me);

        when(tm.begin_()).thenReturn(t);

        when(factSP.create_(any(URL.class), any(String.class))).thenReturn(sp);

        when(sp.getDeviceInfo((Iterable<ByteString>) any())).thenAnswer(
                new Answer<GetDeviceInfoReply>()
                {
                    @Override
                    public GetDeviceInfoReply answer(InvocationOnMock invocation)
                            throws Throwable
                    {
                        Iterable<ByteString> dids = (Iterable<ByteString>) invocation.getArguments()[0];

                        Builder bdReply = GetDeviceInfoReply.newBuilder();
                        if (dids != null) {
                            for (@SuppressWarnings("unused") ByteString did : dids) {
                                bdReply.addDeviceInfo(PBDeviceInfo.getDefaultInstance());
                            }
                        }
                        return bdReply.build();
                    }
                });
    }

    @Test
    public void shouldHaveNoUnresolvedDevicesAfterContactingSPSucceeds()
            throws Exception
    {
        run(false);

        assertTrue(!ev._hasUnresolvedDevices);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldHaveUnresolvedDevicesAfterContactingSPFails()
            throws Exception
    {
        when(sp.getDeviceInfo((Iterable<ByteString>) any())).thenThrow(new Exception());

        run(false);

        assertTrue(ev._hasUnresolvedDevices);
    }

    @Test
    public void shouldReturnMultiplePagesWithSmallResultLimits()
            throws Exception
    {
        ev = new EIGetActivities(false, null, 1, null);
        hd.handleThrows_(ev, Prio.LO);
        assertTrue(ev._activities.size() == 1);
        assertTrue(ev._replyPageToken != null);

        ev = new EIGetActivities(false, ev._replyPageToken, 1, null);
        hd.handleThrows_(ev, Prio.LO);
        assertTrue(ev._activities.size() == 1);
        assertTrue(ev._replyPageToken != null);

        ev = new EIGetActivities(false, ev._replyPageToken, 1, null);
        hd.handleThrows_(ev, Prio.LO);
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
        when(d2u.getFromLocalNullable_(did2)).thenReturn(me);
        run(false);
        assertTrue(firstMsg().startsWith("You (on 1 unknown device) and 2 unknown devices renamed"));
    }

    @Test
    public void shouldShowYourAnd2UknonwDevicesOnlyInBriefMode()
            throws Exception
    {
        when(d2u.getFromLocalNullable_(did2)).thenReturn(me);
        run(true);
        assertTrue(firstMsg().startsWith("You and 2 unknown devices renamed"));
    }

    @Test
    public void shouldShowYourUnknownDeviceAndUserEmailAndUknonwDevicesOnly()
            throws Exception
    {
        when(d2u.getFromLocalNullable_(did2)).thenReturn(me);
        when(d2u.getFromLocalNullable_(did1)).thenReturn("user@gmail");
        run(false);
        assertTrue(firstMsg().startsWith("You (on 1 unknown device) and user@gmail and 1 unknown device renamed"));
    }

    @Test
    public void shouldShowYourAndOthersOnlyInBriefMode()
            throws Exception
    {
        when(d2u.getFromLocalNullable_(did2)).thenReturn(me);
        when(d2u.getFromLocalNullable_(did1)).thenReturn("user@gmail");
        run(true);
        assertTrue(firstMsg().startsWith("You and others renamed"));
    }

    @Test
    public void shouldShowFullNameOnly()
            throws Exception
    {
        addActivity(CREATION_VALUE, new Path("a"), null, did1);
        when(d2u.getFromLocalNullable_(did1)).thenReturn("user@gmail");
        when(udndb.getUserNameNullable_("user@gmail")).thenReturn(new FullName("A", "B"));
        run(false);
        printResult();
        assertTrue(firstMsg().startsWith("A B added"));
    }

    @Test
    public void shouldShowFirstNameOnlyInBriefMode()
            throws Exception
    {
        addActivity(CREATION_VALUE, new Path("a"), null, did1);
        when(d2u.getFromLocalNullable_(did1)).thenReturn("user@gmail");
        when(udndb.getUserNameNullable_("user@gmail")).thenReturn(new FullName("A", "B"));
        run(true);
        printResult();
        assertTrue(firstMsg().startsWith("A added"));
    }

    @Test
    public void shouldShowFullNameAndUnknownDevicesOnly()
            throws Exception
    {
        when(d2u.getFromLocalNullable_(did1)).thenReturn("user@gmail");
        when(udndb.getUserNameNullable_("user@gmail")).thenReturn(new FullName("A", "B"));
        run(false);
        assertTrue(firstMsg().startsWith("A B and 2 unknown devices renamed"));
    }

    @Test
    public void shouldShowFirstNameAndUnknownDevicesOnlyInBriefMode()
            throws Exception
    {
        when(d2u.getFromLocalNullable_(did1)).thenReturn("user@gmail");
        when(udndb.getUserNameNullable_("user@gmail")).thenReturn(new FullName("A", "B"));
        run(true);
        assertTrue(firstMsg().startsWith("A and 2 unknown devices"));
    }

    @Test
    public void shouldShowMyDeviceNames()
            throws Exception
    {
        when(d2u.getFromLocalNullable_(did2)).thenReturn(me);
        when(udndb.getDeviceNameNullable_(did2)).thenReturn("DEV");
        run(false);
        assertTrue(firstMsg().startsWith("You (on DEV) and 2 unknown devices"));
    }

    @Test
    public void shouldNotShowMyDeviceNamesInBriefMode()
            throws Exception
    {
        when(d2u.getFromLocalNullable_(did2)).thenReturn(me);
        when(udndb.getDeviceNameNullable_(did2)).thenReturn("DEV");
        run(true);
        assertTrue(firstMsg().startsWith("You and 2 unknown devices"));
    }

    @Test
    public void shouldNotShowOtherUsersDeviceNames()
            throws Exception
    {
        when(d2u.getFromLocalNullable_(did2)).thenReturn("user@mail");
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
        addActivity(MOVEMENT_VALUE, new Path("a"), new Path("b", "c"), did1);

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
        addActivity(CREATION_VALUE | MODIFICATION_VALUE, new Path("a"), null, did1);

        run(true);
        assertTrue(firstMsg().endsWith("unknown device added file or folder \"a\""));
    }

    // the movemnet event should be suppressed by deletion
    @Test
    public void shouldNotShowMovementWithDeletion()
            throws Exception
    {
        addActivity(DELETION_VALUE | MOVEMENT_VALUE, new Path("a"), new Path("b"), did1);

        run(true);
        assertTrue(firstMsg().endsWith("unknown device deleted file or folder \"a\""));
    }


    void printResult()
    {
        for (PBActivity a : ev._activities) {
            Log.warn(a.getMessage());
        }
    }

    private void run(boolean brief)
            throws Exception
    {
        ev = new EIGetActivities(brief, null, 100, null);
        hd.handleThrows_(ev, Prio.LO);
    }

    private String firstMsg()
    {
        return ev._activities.get(0).getMessage();
    }
}
