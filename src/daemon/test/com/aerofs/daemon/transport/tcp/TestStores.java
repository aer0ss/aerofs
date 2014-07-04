/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.event.net.EIStoreAvailability;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import com.aerofs.lib.bf.BFSID;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Transport.PBTCPPong;
import com.aerofs.proto.Transport.PBTCPStoresFilter;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.LoggerSetup;
import com.google.common.collect.Maps;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.calls;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public final class TestStores extends AbstractTest
{
    static
    {
        LoggerSetup.init();
    }

    private final DID LOCAL_PEER = new DID(DID.generate());
    private final DID REMOTE_PEER_00 = new DID(DID.generate());
    private final DID REMOTE_PEER_01 = new DID(DID.generate());
    private final InetSocketAddress REMOTE_PEER_00_ADDRESS = new InetSocketAddress("localhost", 9999);
    private final InetSocketAddress REMOTE_PEER_01_ADDRESS = new InetSocketAddress("localhost", 9999);
    private final SID SID_00 = SID.generate();
    private final SID SID_01 = SID.generate();
    private final SID SID_02 = SID.generate();

    private final Multicast multicast = mock(Multicast.class);
    @SuppressWarnings("unchecked") private final IBlockingPrioritizedEventSink<IEvent> q = mock(IBlockingPrioritizedEventSink.class);
    private final TCP tcp = mock(TCP.class);
    private final ARP arp = new ARP(mock(IMulticastListener.class));
    private final Stores stores = new Stores(LOCAL_PEER, tcp, arp, multicast);

    private class PresenceInfo
    {
        private final boolean online;
        private final SID[] sids;

        private PresenceInfo(boolean online, SID... sids)
        {
            this.online = online;
            this.sids = sids;
        }

        public boolean isOnline()
        {
            return online;
        }

        public SID[] getSids()
        {
            return sids;
        }

        @Override
        public String toString()
        {
            return "PresenceInfo{o:" + online + " s:" + (sids == null ? null : Arrays.asList(sids)) + "}";
        }
    }

    @Before
    public void initMockTCP()
    {
        when(tcp.sink()).thenReturn(q);
        when(tcp.getListeningPort()).thenReturn(8888);
    }

    @Test
    public void shouldNotSendPresenceIfPeerWithUniterestingStoresComesOnline()
    {
        stores.updateStores(new SID[]{SID_02}, new SID[]{}); // only interested in SID_02

        arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS);

        updateFilterForRemotePeer(REMOTE_PEER_00, SID_00, SID_01);  // remote peer has SID_00 and _01

        verifyZeroInteractions(q);
    }

    @Test
    public void shouldNotSendPresenceIfPeerWithUninterestingStoresGoesOffline()
    {

        arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS);

        stores.updateStores(new SID[]{SID_02}, new SID[]{}); // only interested in SID_02

        updateFilterForRemotePeer(REMOTE_PEER_00, SID_00, SID_01);  // remote peer has SID_00 and _01

        arp.remove(REMOTE_PEER_00);

        verifyZeroInteractions(q);
    }

    @Test
    public void shouldSendPresenceWhenAMatchingFilterIsReceivedFromPeer()
            throws Exception
    {
        arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS);
        stores.updateStores(new SID[]{SID_00}, new SID[]{}); // only interested in SID_00

        updateFilterForRemotePeer(REMOTE_PEER_00, SID_00, SID_01);  // remote peer has SID_00 and _01

        // expecting only one presence (online, SID_00)

        ArgumentCaptor<IEvent> evcaptor = ArgumentCaptor.forClass(IEvent.class);
        verify(q).enqueueBlocking(evcaptor.capture(), any(Prio.class));

        EIStoreAvailability presenceEvent = (EIStoreAvailability) evcaptor.getValue();
        verifyPresenceEvent(presenceEvent, REMOTE_PEER_00, true, SID_00);
    }

    @Test
    public void shouldNotNotifyCoreWhenARPEntryIsDeletedAndPeerHasInterestingStores()
    {
        arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS);

        stores.updateStores(new SID[]{SID_00, SID_01}, new SID[]{});

        updateFilterForRemotePeer(REMOTE_PEER_00, SID_00, SID_01);

        ArgumentCaptor<IEvent> addEventCaptor = ArgumentCaptor.forClass(IEvent.class);
        verify(q, times(1)).enqueueBlocking(addEventCaptor.capture(), any(Prio.class));
        verifyNoMoreInteractions(q);

        EIStoreAvailability presenceEvent = (EIStoreAvailability) addEventCaptor.getValue();
        verifyPresenceEvent(presenceEvent, REMOTE_PEER_00, true, SID_00, SID_01);

        arp.remove(REMOTE_PEER_00);
    }

    @Test
    public void shouldNotifyCoreWhenDevicePresenceNotifiesThatAPeerIsUnavailableAndPeerHasInterestingStores()
    {
        arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS);

        stores.updateStores(new SID[]{SID_00, SID_01}, new SID[]{});

        updateFilterForRemotePeer(REMOTE_PEER_00, SID_00, SID_01);

        stores.onDevicePresenceChanged(REMOTE_PEER_00, false);

        ArgumentCaptor<IEvent> delEventCaptor = ArgumentCaptor.forClass(IEvent.class); // only captures arg for last call (first one was for device going online)
        verify(q, times(2)).enqueueBlocking(delEventCaptor.capture(), any(Prio.class));

        EIStoreAvailability presenceEvent = (EIStoreAvailability) delEventCaptor.getValue();
        verifyPresenceEvent(presenceEvent, REMOTE_PEER_00, false, SID_00, SID_01);
    }

    @Test
    public void shouldAddEntryToARPAndNotifyCoreWhenPongIsReceived() throws ExDeviceUnavailable
    {
        stores.updateStores(new SID[]{SID_00, SID_01}, new SID[]{}); // only interested in two stores

        PBTCPPong pbpong = newPBTCPPong(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS.getPort(), SID_00, SID_01, SID_02); // told of interest in 3
        stores.processPong(REMOTE_PEER_00_ADDRESS.getAddress(), REMOTE_PEER_00, pbpong);

        ArgumentCaptor<IEvent> addEventCaptor = ArgumentCaptor.forClass(IEvent.class);
        verify(q).enqueueBlocking(addEventCaptor.capture(), any(Prio.class));
        verifyNoMoreInteractions(q);

        EIStoreAvailability presenceEvent = (EIStoreAvailability) addEventCaptor.getValue();
        verifyPresenceEvent(presenceEvent, REMOTE_PEER_00, true, SID_00, SID_01);

        assertThat(checkNotNull(arp.getThrows(REMOTE_PEER_00)).remoteAddress, equalTo(REMOTE_PEER_00_ADDRESS));
    }

    @Test
    public void shouldUpdatePresenceCorrectlyIfUpdateStoresIsCalledAfterFiltersReceivedFromRemotePeers()
    {
        // core is initially interested only in SID_00

        stores.updateStores(new SID[]{SID_00}, new SID[]{});

        // remote peers only have SID 01 and 02

        arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS);
        updateFilterForRemotePeer(REMOTE_PEER_00, SID_01, SID_02);

        arp.put(REMOTE_PEER_01, REMOTE_PEER_01_ADDRESS);
        updateFilterForRemotePeer(REMOTE_PEER_01, SID_01, SID_02);

        // suddenly core is interested in 01 and 02
        // this will trigger the following presence notifications: (A) <==
        // REMOTE_PEER_00: +(SID_01, SID_02)
        // REMOTE_PEER_01: +(SID_01, SID_02)

        stores.updateStores(new SID[]{SID_01, SID_02}, new SID[]{});

        // at some point REMOTE_PEER_01 loses SID_02 and notifies us via a filter update
        // this will trigger the following presence notifications: (B) <==
        // REMOTE_PEER_01: -SID_02

        updateFilterForRemotePeer(REMOTE_PEER_01, 2, SID_01);

        // now we're going to verify everything
        // XXX: use Mockito.calls(int) to get non-greedy verification. This allows me to verify
        // one invocation at a time

        InOrder presenceOrder = inOrder(q);

        // (A) (I don't know which presence update comes first (due to ordering)...either one is fine)

        Map<DID, PresenceInfo> expectedPresences = Maps.newHashMap();
        expectedPresences.put(REMOTE_PEER_00, new PresenceInfo(true, SID_01, SID_02));
        expectedPresences.put(REMOTE_PEER_01, new PresenceInfo(true, SID_01, SID_02));
        verifyUnorderedPresenceNotification(presenceOrder, expectedPresences);

        // (B)

        verifyPresenceNotification(presenceOrder, REMOTE_PEER_01, false, SID_02);
    }

    @Test
    public void shouldNotSendPresenceIfTheCoreRemovesAStore()
    {
        // we're interested in SID_00, SID_01, SID_02

        stores.updateStores(new SID[]{SID_00, SID_01, SID_02}, new SID[]{});

        // remote peers come online
        // this will trigger the following notifications (A) <==
        // REMOTE_PEER_00: +(SID_00, SID_01)
        // REMOTE_PEER_01: +(SID_00, SID_02)

        arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS);
        updateFilterForRemotePeer(REMOTE_PEER_00, SID_00, SID_01);

        arp.put(REMOTE_PEER_01, REMOTE_PEER_01_ADDRESS);
        updateFilterForRemotePeer(REMOTE_PEER_01, SID_00, SID_02);

        // now we're no longer interested in SID_01
        // this will update internal state, but we will not send presence to the core because
        // presumably the core wanted this to happen

        stores.updateStores(new SID[]{}, new SID[]{SID_01});

        // now, start the verification

        InOrder presenceOrder = inOrder(q);

        // (A)

        Map<DID, PresenceInfo> expectedPresences = Maps.newHashMap();
        expectedPresences.put(REMOTE_PEER_00, new PresenceInfo(true, SID_00, SID_01));
        expectedPresences.put(REMOTE_PEER_01, new PresenceInfo(true, SID_00, SID_02));
        verifyUnorderedPresenceNotification(presenceOrder, expectedPresences);

        // (B)

        presenceOrder.verifyNoMoreInteractions();
    }

    private PBTCPPong newPBTCPPong(DID remote, int remoteListeningPort, SID... sids)
    {
        return PBTCPPong.newBuilder()
                .setUnicastListeningPort(remoteListeningPort)
                .setFilter(newPBTCPStoresFilter(1, sids))
                .build();
    }

    private void updateFilterForRemotePeer(DID remote, SID... sids)
    {
        updateFilterForRemotePeer(remote, 1, sids);
    }

    private void updateFilterForRemotePeer(DID remote, int filterSeqnum, SID... sids)
    {
        PBTCPStoresFilter pbstores = newPBTCPStoresFilter(filterSeqnum, sids);
        stores.storesFilterReceived(remote, pbstores);
    }

    private PBTCPStoresFilter newPBTCPStoresFilter(int filterSeqnum, SID[] sids)
    {
        BFSID filter = new BFSID();
        for (SID sid : sids) {
            filter.add_(sid);

        }
        filter.finalize_();

        return PBTCPStoresFilter.newBuilder()
                .setFilter(filter.toPB())
                .setSequence(filterSeqnum)
                .build();
    }

    private void verifyUnorderedPresenceNotification(InOrder presenceOrder, Map<DID, PresenceInfo> expectedPresences)
    {
        int numupdates = expectedPresences.size();

        for (int i = 0; i < numupdates; i++) {
            ArgumentCaptor<IEvent> evcaptor = ArgumentCaptor.forClass(IEvent.class);
            presenceOrder.verify(q, calls(1)).enqueueBlocking(evcaptor.capture(), eq(Prio.LO));

            EIStoreAvailability presenceEvent = (EIStoreAvailability) evcaptor.getValue();
            assertEquals(1, presenceEvent._did2sids.size());

            boolean found = false;
            Map.Entry<DID, PresenceInfo> entry = null;
            Iterator<Map.Entry<DID, PresenceInfo>> it = expectedPresences.entrySet().iterator();
            while (it.hasNext() && !found) {
                entry = it.next();
                if (presenceEvent._did2sids.containsKey(entry.getKey())) {
                    it.remove();
                    found = true;
                }
            }

            assertTrue("cannot find one of:" + expectedPresences, found);

            verifyPresenceEvent(presenceEvent, entry.getKey(), entry.getValue().isOnline(), entry.getValue().getSids());
        }
    }

    private void verifyPresenceNotification(InOrder presenceOrder, DID did, boolean online, SID... sids)
    {
        ArgumentCaptor<IEvent> presenceCaptor = ArgumentCaptor.forClass(IEvent.class);
        presenceOrder.verify(q, calls(1)).enqueueBlocking(presenceCaptor.capture(), eq(Prio.LO));
        verifyPresenceEvent((EIStoreAvailability)presenceCaptor.getValue(), did, online, sids);
    }

    private void verifyPresenceEvent(EIStoreAvailability presenceEvent, DID did, boolean online, SID... sids)
    {
        assertEquals(tcp, presenceEvent._tp);
        assertEquals(online, presenceEvent._online);
        assertTrue(presenceEvent._did2sids.containsKey(did));

        Collection<SID> presenceSids = presenceEvent._did2sids.get(did);
        assertEquals(sids.length, presenceSids.size());

        for (SID sid : sids) {
            assertTrue(presenceSids.contains(sid));
        }
    }
}
