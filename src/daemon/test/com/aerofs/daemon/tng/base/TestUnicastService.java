/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.IUnicastListener;
import com.aerofs.daemon.tng.ImmediateInlineExecutor;
import com.aerofs.daemon.tng.base.pipeline.IPipelineFactory;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.id.SID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.net.NetworkInterface;

import static com.aerofs.testlib.FutureAssert.assertNoThrow;
import static org.mockito.Mockito.*;

public class TestUnicastService extends AbstractTest
{
    final DID did = new DID(DID.ZERO);
    UnicastService unicastService;
    ISingleThreadedPrioritizedExecutor executor;

    @Mock ILinkStateService networkLinkStateService;
    @Mock IPresenceService presenceService;
    @Mock IUnicastConnectionService unicastConnectionService;
    @Mock IUnicastListener unicastListener;
    @Mock IPipelineFactory pipelineFactory;
    @Mock IUnicastConnection unicastConnection;
    @Mock PeerFactory peerFactory;
    @Mock Peer peer;

    @Captor ArgumentCaptor<byte[][]> bssCaptor;
    @Captor ArgumentCaptor<Prio> prioCaptor;

    @Before
    public void setUp() throws Exception
    {
        when(peerFactory.getInstance_(did)).thenReturn(peer);

        executor = new ImmediateInlineExecutor();
        unicastService = new UnicastService(unicastConnectionService, peerFactory);

        unicastService.start_();
        verify(unicastConnectionService).start_();
    }

    @Test
    public void shouldCreateNewPeerAndSendPacketWhenCallToSendPacketIsMade() throws Exception
    {
        when(peer.sendDatagram_(any(byte[].class), any(Prio.class)))
                .thenReturn(UncancellableFuture.<Void>createSucceeded(null));

        final byte[] payload = "Hello AeroFS!".getBytes();
        ListenableFuture<Void> future = unicastService.sendDatagram_(did, new SID(SID.ZERO), payload, Prio.LO);

        assertNoThrow(future);

        InOrder ordered = inOrder(peerFactory, peer);
        ordered.verify(peerFactory).getInstance_(did);
        ordered.verify(peer).sendDatagram_(payload, Prio.LO);
    }

    @Test
    public void shouldCreateNewPeerOnceWhenSendPacketIsCalledTwice() throws Exception
    {
        when(peer.sendDatagram_(any(byte[].class), any(Prio.class)))
                .thenReturn(UncancellableFuture.<Void>createSucceeded(null));

        final byte[] payload = "Hello AeroFS!".getBytes();
        ListenableFuture<Void> future = unicastService.sendDatagram_(did, new SID(SID.ZERO), payload, Prio.LO);

        assertNoThrow(future);

        ListenableFuture<Void> secondFuture = unicastService.sendDatagram_(did, new SID(SID.ZERO), payload, Prio.LO);

        assertNoThrow(secondFuture);

        InOrder ordered = inOrder(peerFactory, peer);
        ordered.verify(peerFactory).getInstance_(did);
        ordered.verify(peer, times(2)).sendDatagram_(payload, Prio.LO);
    }

    @Test
    public void shouldCreateNewPeerAndBeginStreamWhenCallToBeginStreamIsMade() throws Exception
    {
        IOutgoingStream outgoingStream = mock(IOutgoingStream.class);

        when(peer.beginStream_(any(StreamID.class), any(Prio.class))).thenReturn(UncancellableFuture.createSucceeded(outgoingStream));

        ListenableFuture<IOutgoingStream> future = unicastService.beginStream_(new StreamID(1), did, new SID(SID.ZERO), Prio.LO);

        assertNoThrow(future);

        InOrder ordered = inOrder(peerFactory, peer);
        ordered.verify(peerFactory).getInstance_(did);
        ordered.verify(peer).beginStream_(new StreamID(1), Prio.LO);
    }

    @Test
    public void shouldCreateNewPeerOnceWhenBeginStreamIsCalledTwice() throws Exception
    {
        IOutgoingStream outgoingStream = mock(IOutgoingStream.class);

        when(peer.beginStream_(any(StreamID.class), any(Prio.class))).thenReturn(UncancellableFuture.createSucceeded(outgoingStream));

        ListenableFuture<IOutgoingStream> future = unicastService.beginStream_(new StreamID(1), did, new SID(SID.ZERO), Prio.LO);

        assertNoThrow(future);

        ListenableFuture<IOutgoingStream> secondFuture = unicastService.beginStream_(new StreamID(2), did, new SID(SID.ZERO), Prio.LO);

        assertNoThrow(secondFuture);

        InOrder ordered = inOrder(peerFactory, peer);
        ordered.verify(peerFactory).getInstance_(did);
        ordered.verify(peer).beginStream_(new StreamID(1), Prio.LO);
        ordered.verify(peer).beginStream_(new StreamID(2), Prio.LO);
    }

    @Test
    public void shouldCreateNewPeerAndStartPulseWhenCallToStartPulseIsMade() throws Exception
    {
        when(peer.pulse_(any(Prio.class))).thenReturn(UncancellableFuture.<Void>createSucceeded(null));

        ListenableFuture<Void> future = unicastService.pulse_(did, Prio.HI);

        assertNoThrow(future);

        InOrder ordered = inOrder(peerFactory, peer);
        ordered.verify(peerFactory).getInstance_(did);
        ordered.verify(peer).pulse_(Prio.HI);
    }

    @Test
    public void shouldCreateNewPeerOnceWhenStartPulseIsCalledTwice() throws Exception
    {
        when(peer.pulse_(any(Prio.class))).thenReturn(UncancellableFuture.<Void>createSucceeded(null));

        ListenableFuture<Void> future = unicastService.pulse_(did, Prio.HI);

        assertNoThrow(future);

        ListenableFuture<Void> secondFuture = unicastService.pulse_(did,
            Prio.LO);

        assertNoThrow(secondFuture);

        InOrder ordered = inOrder(peerFactory, peer);
        ordered.verify(peerFactory).getInstance_(did);
        ordered.verify(peer).pulse_(Prio.HI);
        ordered.verify(peer).pulse_(Prio.LO);
    }

    @Test
    public void shouldCreatePeerWhenSendPacketIsCalledThenDestroyPeerWhenPeerIsOfflineAndCreateNewPeerWhenPeerIsBackOnline() throws Exception
    {
        when(peer.sendDatagram_(any(byte[].class), any(Prio.class)))
                .thenReturn(UncancellableFuture.<Void>createSucceeded(null));

        final byte[] payload = "Hello AeroFS!".getBytes();
        ListenableFuture<Void> future = unicastService.sendDatagram_(did, new SID(SID.ZERO), payload, Prio.LO);

        assertNoThrow(future);

        unicastService.onPeerOffline_(did);

        InOrder ordered = inOrder(peerFactory, peer);
        ordered.verify(peerFactory).getInstance_(did);
        ordered.verify(peer).sendDatagram_(payload, Prio.LO);
        ordered.verify(peer).destroy_(any(Exception.class));

        unicastService.onPeerOnline_(did);

        ListenableFuture<Void> secondFuture = unicastService.sendDatagram_(did, new SID(SID.ZERO), payload, Prio.LO);

        assertNoThrow(secondFuture);

        ordered.verify(peerFactory).getInstance_(did);
        ordered.verify(peer).sendDatagram_(payload, Prio.LO);
    }

    @Test
    public void shouldCreatePeerOnceWhenNewIncomingConnectionCallbackHappensFollowedByACallToSendPacket() throws Exception
    {
        when(peer.sendDatagram_(any(byte[].class), any(Prio.class)))
                .thenReturn(UncancellableFuture.<Void>createSucceeded(null));

        IUnicastConnection connection = mock(IUnicastConnection.class);
        unicastService.onNewIncomingConnection_(did, connection);

        final byte[] payload = "Hello AeroFS!".getBytes();
        ListenableFuture<Void> future = unicastService.sendDatagram_(did, new SID(SID.ZERO), payload, Prio.LO);

        assertNoThrow(future);

        InOrder ordered = inOrder(peerFactory, peer);
        ordered.verify(peerFactory).getInstance_(did);
        ordered.verify(peer).onIncomingConnection_(connection);
        ordered.verify(peer).sendDatagram_(payload, Prio.LO);
    }

    @Test
    public void shouldDestroyAllPeersWhenPresenceServiceDisconnects() throws Exception
    {
        DID didA = mock(DID.class);
        Peer peerA = mock(Peer.class);
        DID didB = mock(DID.class);
        Peer peerB = mock(Peer.class);
        DID didC = mock(DID.class);
        Peer peerC = mock(Peer.class);

        when(peerFactory.getInstance_(didA)).thenReturn(peerA);
        when(peerFactory.getInstance_(didB)).thenReturn(peerB);
        when(peerFactory.getInstance_(didC)).thenReturn(peerC);

        when(peerA.pulse_(any(Prio.class))).thenReturn(UncancellableFuture.<Void>createSucceeded(null));
        when(peerB.pulse_(any(Prio.class))).thenReturn(UncancellableFuture.<Void>createSucceeded(null));
        when(peerC.pulse_(any(Prio.class))).thenReturn(UncancellableFuture.<Void>createSucceeded(null));

        unicastService.pulse_(didA, Prio.LO);
        unicastService.pulse_(didB, Prio.HI);
        unicastService.pulse_(didC, Prio.HI);

        unicastService.onPresenceServiceDisconnected_();

        verify(peerFactory, times(3)).getInstance_(any(DID.class));
        verify(peerA).pulse_(Prio.LO);
        verify(peerB).pulse_(Prio.HI);
        verify(peerC).pulse_(Prio.HI);

        verify(peerA).destroy_(any(Exception.class));
        verify(peerB).destroy_(any(Exception.class));
        verify(peerC).destroy_(any(Exception.class));

        // Make sure the peers were removed. Calling startpulse should ask for another instance from the PeerFactory
        unicastService.pulse_(didA, Prio.LO);
        unicastService.pulse_(didB, Prio.HI);
        unicastService.pulse_(didC, Prio.HI);

        verify(peerFactory, times(6)).getInstance_(any(DID.class));
    }

    @Test
    public void shouldDestroyAllPeersWhenNetworkLinksAreDown() throws Exception
    {
        DID didA = mock(DID.class);
        Peer peerA = mock(Peer.class);
        DID didB = mock(DID.class);
        Peer peerB = mock(Peer.class);
        DID didC = mock(DID.class);
        Peer peerC = mock(Peer.class);

        when(peerFactory.getInstance_(didA)).thenReturn(peerA);
        when(peerFactory.getInstance_(didB)).thenReturn(peerB);
        when(peerFactory.getInstance_(didC)).thenReturn(peerC);

        when(peerA.pulse_(any(Prio.class))).thenReturn(UncancellableFuture.<Void>createSucceeded(null));
        when(peerB.pulse_(any(Prio.class))).thenReturn(UncancellableFuture.<Void>createSucceeded(null));
        when(peerC.pulse_(any(Prio.class))).thenReturn(UncancellableFuture.<Void>createSucceeded(null));

        unicastService.pulse_(didA, Prio.LO);
        unicastService.pulse_(didB, Prio.HI);
        unicastService.pulse_(didC, Prio.HI);

        unicastService.onLinkStateChanged_(ImmutableSet.<NetworkInterface>of(), ImmutableSet.<NetworkInterface>of(),
                ImmutableSet.<NetworkInterface>of(), ImmutableSet.<NetworkInterface>of());

        verify(peerFactory, times(3)).getInstance_(any(DID.class));
        verify(peerA).pulse_(Prio.LO);
        verify(peerB).pulse_(Prio.HI);
        verify(peerC).pulse_(Prio.HI);

        verify(peerA).destroy_(any(Exception.class));
        verify(peerB).destroy_(any(Exception.class));
        verify(peerC).destroy_(any(Exception.class));

        // Make sure the peers were removed. Calling startpulse should ask for another instance from the PeerFactory
        unicastService.pulse_(didA, Prio.LO);
        unicastService.pulse_(didB, Prio.HI);
        unicastService.pulse_(didC, Prio.HI);

        verify(peerFactory, times(6)).getInstance_(any(DID.class));
    }

    @Test
    @Ignore("DumpStat not implemented")
    public void shouldFillInTemplateDumpStatMessageWithMinimalRequiredInformation() throws Exception
    {

    }

    @Test
    @Ignore("DumpStatMisc not implemented")
    public void shouldPrintToThePrintStreamDebugInformation() throws Exception
    {

    }

}