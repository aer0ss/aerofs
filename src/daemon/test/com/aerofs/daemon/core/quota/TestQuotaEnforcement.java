/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.quota;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Sp.CheckQuotaCall.PBStoreUsage;
import com.aerofs.proto.Sp.CheckQuotaReply;
import com.aerofs.proto.Sp.CheckQuotaReply.PBStoreShouldCollect;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestQuotaEnforcement extends AbstractTest
{
    @Mock IMapSID2SIndex sid2sidx;
    @Mock IMapSIndex2SID sidx2sid;
    @Mock MapSIndex2Store sidx2s;
    @Mock CoreScheduler sched;
    @Mock IStores stores;
    @Mock StoreUsageCache usage;
    @Mock TokenManager tokenManager;
    @Mock TransManager tm;
    @Mock InjectableSPBlockingClientFactory factSP;
    @Mock SPBlockingClient sp;

    @InjectMocks QuotaEnforcement quota;

    @Captor ArgumentCaptor<List<PBStoreUsage>> captorSPParam;

    SIndex[] sidxs;
    SID[] sids;
    Store[] ss;

    @Before
    public void setUp()
            throws Exception
    {
        // Mock a few stores
        int STORES = 3;
        sidxs = new SIndex[STORES];
        sids = new SID[STORES];
        ss = new Store[STORES];
        for (int i = 0; i < STORES; i++) {
            sidxs[i] = new SIndex(i);
            sids[i] = SID.generate();
            ss[i] = mock(Store.class);
        }

        // Mock store management
        when(stores.getAll_()).thenReturn(Sets.newHashSet(sidxs));
        for (int i = 0; i < sidxs.length; i++) {
            when(sid2sidx.getNullable_(sids[i])).thenReturn(sidxs[i]);
            when(sidx2sid.get_(sidxs[i])).thenReturn(sids[i]);
            when(sidx2s.get_(sidxs[i])).thenReturn(ss[i]);
        }

        // Mock out boring stuff
        when(tokenManager.acquireThrows_(any(Cat.class), anyString())).then(RETURNS_MOCKS);
        when(factSP.create()).thenReturn(sp);
        when(sp.signInRemote()).thenReturn(sp);

        // By default, SP replies with an empty list
        buildSPReply();

        // Run the first event only (which is scheduled with 0 delay)
        doAnswer(new Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                AbstractEBSelfHandling ev = (AbstractEBSelfHandling)invocation.getArguments()[0];
                ev.handle_();
                return null;
            }
        }).when(sched).schedule(any(IEvent.class), eq(0L));
    }

    private void buildSPReply(PBStoreShouldCollect... list)
            throws Exception
    {
        CheckQuotaReply reply = CheckQuotaReply.newBuilder()
                .addAllStore(Lists.newArrayList(list))
                .build();
        when(sp.checkQuota(anyListOf(PBStoreUsage.class))).thenReturn(reply);
    }

    @Test
    public void shouldScheduleNextEvent()
            throws Exception
    {
        quota.start_();
        verify(sched).schedule(any(AbstractEBSelfHandling.class),
                eq(DaemonParam.CHECK_QUOTA_INTERVAL));
    }

    @Test
    public void shouldReportExpectedStoreUsage()
            throws Exception
    {
        for (SIndex sidx : sidxs) {
            when(usage.getBytesUsed_(sidx)).thenReturn(mockBytesUsed(sidx));
        }
        quota.start_();

        verify(sp).checkQuota(captorSPParam.capture());

        // import the SP parameter to a map
        Map<SID, Long> params = Maps.newHashMap();
        for (PBStoreUsage su : captorSPParam.getValue()) {
            params.put(new SID(su.getSid()), su.getBytesUsed());
        }

        // Verify the values in the parameter are expected
        assertEquals(params.size(), sids.length);
        for (SID sid : sids) {
            long bytesUsed = mockBytesUsed(sid2sidx.getNullable_(sid));
            assertEquals(params.get(sid), (Long)bytesUsed);
        }
    }

    private long mockBytesUsed(SIndex sidx)
    {
        return sidx.getInt() * 1000;
    }

    @Test
    public void shouldToggleContentCollection()
            throws Exception
    {
        buildSPReply(
                PBStoreShouldCollect.newBuilder().setSid(sids[0].toPB()).setCollectContent(true)
                        .build());
        quota.start_();

        verify(ss[0]).startCollectingContent_(any(Trans.class));

        buildSPReply(PBStoreShouldCollect.newBuilder()
                        .setSid(sids[0].toPB())
                        .setCollectContent(false)
                        .build()
        );
        quota.start_();

        verify(ss[0]).stopCollectingContent_(any(Trans.class));
    }

    @Test
    public void shouldSkipAbsentStores()
            throws Exception
    {
        // Inject an arbitrary SID into the SP reply. The test passes as long as the system doesn't
        // crash.
        SID sid = SID.generate();
        buildSPReply(PBStoreShouldCollect.newBuilder().setSid(sid.toPB()).setCollectContent(false)
                .build());

        quota.start_();

        // Verify that the system has attempted to resovle the SID
        verify(sid2sidx).getNullable_(sid);
        // Verfiy that sid2sidx does return null for the SID.
        assertNull(sid2sidx.getNullable_(sid));
    }

    @Test
    public void shouldSkipUnmentionedStores()
            throws Exception
    {
        quota.start_();

        // Verify that stores not mentioned in the reply weren't looked after.
        verify(ss[0], never()).stopCollectingContent_(any(Trans.class));
        verify(ss[0], never()).startCollectingContent_(any(Trans.class));
    }
}