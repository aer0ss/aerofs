/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.lib.IBlockingPrioritizedEventSink;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.Scheduler;
import com.aerofs.lib.bf.BFSID;
import com.aerofs.proto.Transport.PBTCPFilterAndSeq;
import com.aerofs.proto.Transport.PBTCPStores;
import com.aerofs.testlib.AbstractTest;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.net.InetSocketAddress;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.calls;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestStores extends AbstractTest
{
    private final DID LOCAL_PEER = new DID(DID.generate());
    private final DID REMOTE_PEER_00 = new DID(DID.generate());
    private final DID REMOTE_PEER_01 = new DID(DID.generate());
    private final InetSocketAddress REMOTE_PEER_00_ADDRESS = new InetSocketAddress("localhost", 9999);
    private final InetSocketAddress REMOTE_PEER_01_ADDRESS = new InetSocketAddress("localhost", 9999);
    private final SID SID_00 = SID.generate();
    private final SID SID_01 = SID.generate();
    private final SID SID_02 = SID.generate();

    private final Unicast _unicast = mock(Unicast.class);
    private final Multicast _multicast = mock(Multicast.class);
    private final Scheduler _scheduler = mock(Scheduler.class);
    @SuppressWarnings("unchecked") private final IBlockingPrioritizedEventSink<IEvent> _q =
            mock(IBlockingPrioritizedEventSink.class);
    private final TCP _tcp = mock(TCP.class);
    private final ARP _arp = new ARP();
    private final Stores _stores = new Stores(LOCAL_PEER, _tcp, _arp, false);

    @Before
    public void initMockTCP()
    {
        when(_tcp.sink()).thenReturn(_q);
        when(_tcp.sched()).thenReturn(_scheduler);
        when(_tcp.ucast()).thenReturn(_unicast);
        when(_tcp.mcast()).thenReturn(_multicast);

        when(_unicast.getListeningPort()).thenReturn(8888);
    }

    @Test
    public void shouldNotSendPresenceIfPeerWithUniterestingStoresComesOnline()
    {
        _stores.updateStores(new SID[]{SID_02}, new SID[]{}); // only interested in SID_02

        _arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS, false);

        updateFilterForRemotePeer_(REMOTE_PEER_00, SID_00, SID_01);  // remote peer has SID_00 and _01

        verifyZeroInteractions(_q);
    }

    @Test
    public void shouldNotSendPresenceIfPeerWithUninterestingStoresGoesOffline()
    {

        _arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS, false);

        _stores.updateStores(new SID[]{SID_02}, new SID[]{}); // only interested in SID_02

        updateFilterForRemotePeer_(REMOTE_PEER_00, SID_00, SID_01);  // remote peer has SID_00 and _01

        _arp.remove(REMOTE_PEER_00);

        verifyZeroInteractions(_q);
    }

    @Test
    public void shouldSendPresenceWhenAMatchingFilterIsReceivedFromPeer()
            throws Exception
    {
        _arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS, false);
        _stores.updateStores(new SID[]{SID_00}, new SID[]{}); // only interested in SID_00

        updateFilterForRemotePeer_(REMOTE_PEER_00, SID_00, SID_01);  // remote peer has SID_00 and _01

        // expecting only one presence (online, SID_00)

        ArgumentCaptor<IEvent> evcaptor = ArgumentCaptor.forClass(IEvent.class);
        verify(_q).enqueueBlocking(evcaptor.capture(), any(Prio.class));

        EIPresence presenceEvent = (EIPresence) evcaptor.getValue();
        verifyPresenceEvent(presenceEvent, REMOTE_PEER_00, true, SID_00);
    }

    @Test
    public void shouldNotifyCoreWhenARPEntryIsDeletedAndPeerHasInterestingStores()
    {
        _arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS, true);

        _stores.updateStores(new SID[]{SID_00, SID_01}, new SID[]{});

        updateFilterForRemotePeer_(REMOTE_PEER_00, SID_00, SID_01);

        _arp.remove(REMOTE_PEER_00);

        ArgumentCaptor<IEvent> delEventCaptor = ArgumentCaptor.forClass(IEvent.class); // only captures arg for last call
        verify(_q, times(2)).enqueueBlocking(delEventCaptor.capture(), any(Prio.class));

        EIPresence presenceEvent = (EIPresence) delEventCaptor.getValue();
        verifyPresenceEvent(presenceEvent, REMOTE_PEER_00, false, SID_00, SID_01);
    }

    @Ignore
    @Test
    public void shouldUpdatePresenceCorrectlyIfUpdateStoresIsCalledAfterFiltersReceivedFromRemotePeers()
    {
        // core is initially interested only in SID_00

        _stores.updateStores(new SID[]{SID_00}, new SID[]{});

        // remote peers only have SID 01 and 02

        _arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS, true);
        updateFilterForRemotePeer_(REMOTE_PEER_00, SID_01, SID_02);

        _arp.put(REMOTE_PEER_01, REMOTE_PEER_01_ADDRESS, false);
        updateFilterForRemotePeer_(REMOTE_PEER_01, SID_01, SID_02);

        // suddenly core is interested in 01 and 02
        // this will trigger the following presence notifications: (A) <==
        // REMOTE_PEER_00: +(SID_01, SID_02)
        // REMOTE_PEER_01: +(SID_01, SID_02)

        _stores.updateStores(new SID[]{SID_01, SID_02}, new SID[]{});

        // at some point REMOTE_PEER_01 loses SID_02 and notifies us via a filter update
        // this will trigger the following presence notifications: (B) <==
        // REMOTE_PEER_01: -SID_02

        updateFilterForRemotePeer_(REMOTE_PEER_01, 2, SID_01);

        // now we're going to verify everything
        // XXX: use Mockito.calls(int) to get non-greedy verification. This allows me to verify
        // one invocation at a time

        InOrder presenceOrder = inOrder(_q);

        // (A)

        verifyPresenceNotification_(presenceOrder, REMOTE_PEER_00, true, SID_01, SID_02);
        verifyPresenceNotification_(presenceOrder, REMOTE_PEER_01, true, SID_01, SID_02);

        // (B)

        verifyPresenceNotification_(presenceOrder, REMOTE_PEER_01, false, SID_02);
    }

    @Ignore
    @Test
    public void shouldNotSendPresenceIfTheCoreRemovesAStore()
    {
        // we're interested in SID_00, SID_01, SID_02

        _stores.updateStores(new SID[]{SID_00, SID_01, SID_02}, new SID[]{});

        // remote peers come online
        // this will trigger the following notifications (A) <==
        // REMOTE_PEER_00: +(SID_00, SID_01)
        // REMOTE_PEER_01: +(SID_00, SID_02)

        _arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS, true);
        updateFilterForRemotePeer_(REMOTE_PEER_00, SID_00, SID_01);

        _arp.put(REMOTE_PEER_01, REMOTE_PEER_01_ADDRESS, true);
        updateFilterForRemotePeer_(REMOTE_PEER_01, SID_00, SID_02);

        // now we're no longer interested in SID_01
        // this will update internal state, but we will not send presence to the core because
        // presumably the core wanted this to happen

        _stores.updateStores(new SID[]{}, new SID[]{SID_01});

        // now, start the verification

        InOrder presenceOrder = inOrder(_q);

        // (A)

        verifyPresenceNotification_(presenceOrder, REMOTE_PEER_00, true, SID_00, SID_01);
        verifyPresenceNotification_(presenceOrder, REMOTE_PEER_01, true, SID_00, SID_02);

        // (B)

        presenceOrder.verifyNoMoreInteractions();
    }

    @Test
    public void shouldSendPresenceWhenAMatchingPrefixesAreReceivedFromPeer()
    {
        _arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS, false);
        _stores.updateStores(new SID[]{SID_00}, new SID[]{}); // only interested in SID_00

        updatePrefixesForRemotePeer_(REMOTE_PEER_00, SID_00, SID_01);  // remote peer has SID_00 and _01

        // expecting only one presence (online, SID_00)

        ArgumentCaptor<IEvent> evcaptor = ArgumentCaptor.forClass(IEvent.class);
        verify(_q).enqueueBlocking(evcaptor.capture(), any(Prio.class));

        EIPresence presenceEvent = (EIPresence) evcaptor.getValue();
        verifyPresenceEvent(presenceEvent, REMOTE_PEER_00, true, SID_00);
    }

    @Test
    public void shouldNotifyCoreWhenARPEntryIsDeletedAndPrefixSendingPeerHasInterestingStores()
    {
        _arp.put(REMOTE_PEER_00, REMOTE_PEER_00_ADDRESS, true);

        _stores.updateStores(new SID[]{SID_00, SID_01}, new SID[]{});

        updatePrefixesForRemotePeer_(REMOTE_PEER_00, SID_00, SID_01);

        _arp.remove(REMOTE_PEER_00);

        ArgumentCaptor<IEvent> delEventCaptor = ArgumentCaptor.forClass(IEvent.class); // only captures arg for last call
        verify(_q, times(2)).enqueueBlocking(delEventCaptor.capture(), any(Prio.class));

        EIPresence presenceEvent = (EIPresence) delEventCaptor.getValue();
        verifyPresenceEvent(presenceEvent, REMOTE_PEER_00, false, SID_00, SID_01);
    }

    private void updateFilterForRemotePeer_(DID remote, SID... sids)
    {
        updateFilterForRemotePeer_(remote, 1, sids);
    }

    private void updateFilterForRemotePeer_(DID remote, int filterSeqnum, SID... sids)
    {
        BFSID filter = new BFSID();
        for (SID sid : sids) {
            filter.add_(sid);

        }
        filter.finalize_();

        PBTCPStores pbstores = PBTCPStores
                .newBuilder()
                .setFilter(PBTCPFilterAndSeq
                        .newBuilder()
                        .setFilter(filter.toPB())
                        .setSequence(filterSeqnum))
                .build();

        _stores.storesReceived(remote, pbstores);
    }

    private void updatePrefixesForRemotePeer_(DID remote, SID... sids)
    {
        PBTCPStores.Builder bd = PBTCPStores .newBuilder();
        for (SID sid : sids) {
            bd.addPrefix(ByteString.copyFrom(sid.getBytes(), 0, 8)); // see Stores.Prefix.PREFIX_LENGTH
        }

        _stores.storesReceived(remote, bd.build());
    }

    private void verifyPresenceNotification_(InOrder presenceOrder, DID did, boolean online, SID... sids)
    {
        ArgumentCaptor<IEvent> presenceCaptor = ArgumentCaptor.forClass(IEvent.class);
        presenceOrder.verify(_q, calls(1)).enqueueBlocking(presenceCaptor.capture(), eq(Prio.LO));
        verifyPresenceEvent((EIPresence)presenceCaptor.getValue(), did, online, sids);
    }

    private void verifyPresenceEvent(EIPresence presenceEvent, DID did, boolean online, SID... sids)
    {
        assertEquals(_tcp, presenceEvent._tp);
        assertEquals(online, presenceEvent._online);
        assertTrue(presenceEvent._did2sids.containsKey(did));

        Collection<SID> presenceSids = presenceEvent._did2sids.get(did);
        assertEquals(sids.length, presenceSids.size());

        for (SID sid : sids) {
            assertTrue(presenceSids.contains(sid));
        }
    }
}
