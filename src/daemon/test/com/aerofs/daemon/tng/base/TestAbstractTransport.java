/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.net.tng.Preference;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.DropDelayedInlineEventLoop;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.id.SID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.ExecutionException;

import static org.mockito.Mockito.*;
import static com.aerofs.testlib.FutureAssert.assertCompletionFutureChainedProperly;
import static org.junit.Assert.*;

public class TestAbstractTransport extends AbstractTest
{
    private static final String TRANSPORT_ID = "TEST";
    private static final Preference TRANSPORT_PREF = new Preference(0);

    private final IMaxcastService _maxcastService = mock(IMaxcastService.class);
    private final IUnicastService _unicastService = mock(IUnicastService.class);
    private final IPresenceService _presenceService = mock(IPresenceService.class);
    private final IEventLoop _eventLoop = spy(new DropDelayedInlineEventLoop());
    private final TestTransport _transport = new TestTransport(TRANSPORT_ID, TRANSPORT_PREF,
            _eventLoop, _presenceService, _unicastService, _maxcastService);

    private class TestTransport extends AbstractTransport
    {
        private TestTransport(String id,
                Preference pref,
                IEventLoop executor,
                IPresenceService presenceService,
                IUnicastService unicastService,
                IMaxcastService maxcastService)
        {
            super(id, pref, executor, presenceService, unicastService, maxcastService);
        }
    }

    @Test
    public void shouldForwardSendPacketCallToMaxcastService()
    {
        UncancellableFuture<Void> serviceFuture = UncancellableFuture.create();
        when(_maxcastService.sendDatagram_(anyInt(), any(SID.class), any(byte[].class),
                any(Prio.class))).thenReturn(serviceFuture);

        final int MAXCAST_ID = 0;
        final SID MAXCAST_SID = new SID(SID.ZERO);
        final byte[] MAXCAST_DATA = new byte[]{0};
        final Prio MAXCAST_PRIO = Prio.LO;

        ListenableFuture<Void> returned = _transport.sendDatagram_(MAXCAST_ID, MAXCAST_SID,
                MAXCAST_DATA, MAXCAST_PRIO);

        verify(_eventLoop).execute(any(Runnable.class), eq(MAXCAST_PRIO));
        verify(_maxcastService).sendDatagram_(MAXCAST_ID, MAXCAST_SID, MAXCAST_DATA, MAXCAST_PRIO);

        assertCompletionFutureChainedProperly(serviceFuture, returned);
    }

    @Test
    public void shouldForwardUpdateLocalStoreInterestCallToMaxcastService()
    {
        UncancellableFuture<Void> serviceFuture = UncancellableFuture.create();
        when(_maxcastService.updateLocalStoreInterest_(Mockito.<ImmutableSet<SID>>any(),
                Mockito.<ImmutableSet<SID>>any())).thenReturn(serviceFuture);

        final ImmutableSet<SID> ADDED = ImmutableSet.of(new SID(SID.ZERO));
        final ImmutableSet<SID> REMOVED = ImmutableSet.of();

        ListenableFuture<Void> returned = _transport.updateLocalStoreInterest_(ADDED, REMOVED);

        verify(_eventLoop).execute(any(Runnable.class), eq(Prio.LO));

        assertCompletionFutureChainedProperly(serviceFuture, returned);
    }

    @Test
    public void shouldForwardMaxcastUnreachableOnlineDevicesCallToMaxcastService()
            throws ExecutionException, InterruptedException
    {
        UncancellableFuture<ImmutableSet<DID>> serviceFuture = UncancellableFuture.create();
        when(_maxcastService.getMaxcastUnreachableOnlineDevices_()).thenReturn(serviceFuture);

        ListenableFuture<ImmutableSet<DID>> returned = _transport.getMaxcastUnreachableOnlineDevices_();

        verify(_eventLoop).execute(any(Runnable.class), eq(Prio.LO));

        assertFalse(returned.isDone());

        final ImmutableSet<DID> UNREACHABLE = ImmutableSet.of(new DID(DID.ZERO));
        serviceFuture.set(UNREACHABLE);

        assertTrue(returned.isDone());
        assertEquals(UNREACHABLE, returned.get());
    }

    @Test
    public void shouldForwardSendPacketCallToUnicastService()
    {
        UncancellableFuture<Void> serviceFuture = UncancellableFuture.create();
        when(_unicastService.sendDatagram_(any(DID.class), any(SID.class), any(byte[].class),
                any(Prio.class))).thenReturn(serviceFuture);

        final DID UNICAST_DID = new DID(DID.ZERO);
        final SID UNICAST_SID = new SID(SID.ZERO);
        final byte[] UNICAST_DATA = new byte[]{0};
        final Prio UNICAST_PRIO = Prio.LO;

        ListenableFuture<Void> returned = _transport.sendDatagram_(UNICAST_DID, UNICAST_SID,
                UNICAST_DATA, UNICAST_PRIO);

        verify(_eventLoop).execute(any(Runnable.class), eq(UNICAST_PRIO));
        verify(_unicastService).sendDatagram_(UNICAST_DID, UNICAST_SID, UNICAST_DATA,
                UNICAST_PRIO);

        assertCompletionFutureChainedProperly(serviceFuture, returned);
    }

    @Test
    public void shouldForwardBeginStreamCallToUnicastService()
            throws ExecutionException, InterruptedException
    {
        UncancellableFuture<IOutgoingStream> serviceFuture = UncancellableFuture.create();
        when(_unicastService.beginStream_(any(StreamID.class), any(DID.class), any(SID.class),
                any(Prio.class))).thenReturn(serviceFuture);

        final StreamID UNICAST_STREAMID = new StreamID(0);
        final DID UNICAST_DID = new DID(DID.ZERO);
        final SID UNICAST_SID = new SID(SID.ZERO);
        final Prio UNICAST_PRIO = Prio.LO;

        ListenableFuture<IOutgoingStream> returned = _transport.beginStream_(UNICAST_STREAMID,
                UNICAST_DID, UNICAST_SID, UNICAST_PRIO);

        verify(_eventLoop).execute(any(Runnable.class), eq(UNICAST_PRIO));
        verify(_unicastService).beginStream_(UNICAST_STREAMID, UNICAST_DID, UNICAST_SID,
                UNICAST_PRIO);

        assertFalse(returned.isDone());

        final IOutgoingStream UNICAST_RETURNED_STREAM = mock(IOutgoingStream.class);
        serviceFuture.set(UNICAST_RETURNED_STREAM);

        assertTrue(returned.isDone());

        IOutgoingStream returnedStream = returned.get();
        assertEquals(UNICAST_RETURNED_STREAM, returnedStream);
    }

    @Test
    public void shouldForwardStartPulseCallToUnicastService()
    {
        UncancellableFuture<Void> serviceFuture = UncancellableFuture.create();
        when(_unicastService.pulse_(any(DID.class), any(Prio.class))).thenReturn(
                serviceFuture);

        final DID UNICAST_DID = new DID(DID.ZERO);
        final Prio UNICAST_PRIO = Prio.LO;

        ListenableFuture<Void> returned = _transport.pulse_(UNICAST_DID,
            UNICAST_PRIO);

        verify(_eventLoop).execute(any(Runnable.class), eq(UNICAST_PRIO));
        verify(_unicastService).pulse_(UNICAST_DID, UNICAST_PRIO);

        assertCompletionFutureChainedProperly(serviceFuture, returned);
    }
}
