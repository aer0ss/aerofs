/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.device;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDevice extends AbstractTest
{
    private final Device _dev = new Device(new DID(DID.generate()));

    private final ITransport _tp1 = mock(ITransport.class);
    private final ITransport _tp2 = mock(ITransport.class);
    private final ITransport _tp3 = mock(ITransport.class);

    private final ImmutableSet<SIndex> _tp1sids = ImmutableSet.of(new SIndex(0), new SIndex(1));
    private final ImmutableSet<SIndex> _tp2sids = ImmutableSet.of(new SIndex(1), new SIndex(2));

    @Before
    public void setUp()
            throws Exception
    {
        when(_tp1.rank()).thenReturn(1);
        when(_tp1.id()).thenReturn("tp1");

        when(_tp2.rank()).thenReturn(2);
        when(_tp2.id()).thenReturn("tp2");

        when(_tp3.rank()).thenReturn(3);
        when(_tp3.id()).thenReturn("tp3");
    }

    @Test
    public void shouldReturnBestTransportAsPreferredTransportIfBothAreOnline()
    {
        putTp1AndTp2Online();

        assertEquals("checking tp1 is the best", _tp1, _dev.getPreferredTransport_());
    }

    @Test
    public void shouldReturnBestTransportAsPreferredTransportAfterMultipleStateChanges()
    {
        putTp1AndTp2Online();

        _dev.offline_(_tp1);
        _dev.online_(_tp1, _tp1sids);

        assertEquals("checking tp1 is the best", _tp1, _dev.getPreferredTransport_());
    }

    @Test
    public void shouldReturnCorrectSetOfOnlineSidcsWhenMultipleTransportsGoOnline()
    {
        // since tp1 is the 1st transport to come online, we expect to see both its sids come online

        Collection<SIndex> sidcsOnlineAfterTp1 = _dev.online_(_tp1, _tp1sids);
        assertTrue(sidCollectionsIdentical(sidcsOnlineAfterTp1, _tp1sids));

        l.info("after tp1:" + sidcsOnlineAfterTp1);

        // tp2 shares some sids with tp1, so we only expect the _difference_ to come online now

        Collection<SIndex> sidcsOnlineAfterTp2 = _dev.online_(_tp2, _tp2sids);
        assertTrue(sidCollectionsIdentical(sidcsOnlineAfterTp2, ImmutableSet.of(new SIndex(2))));

        l.info("after tp2:" + sidcsOnlineAfterTp2);
    }

    @Test
    public void shouldReturnCorrectSetOfOnlineSidcsWhenMultipleTransportsGoCompletelyOffline()
    {
        putTp1AndTp2Online();

        // tp2 shares some sids with tp1, we only expect 1 sid to go completely offline

        Collection<SIndex> sidcsOfflineAfterTp2 = _dev.offline_(_tp2);
        assertTrue(sidCollectionsIdentical(sidcsOfflineAfterTp2, ImmutableSet.of(new SIndex(2))));

        l.info("after tp2:" + sidcsOfflineAfterTp2);

        assertTrue(_dev.isOnline_());

        // now that tp1 goes offline, the remaining sids go completely offline

        Collection<SIndex> sidcsOfflineAfterTp1 = _dev.offline_(_tp1);
        assertTrue(sidCollectionsIdentical(sidcsOfflineAfterTp1, _tp1sids));

        l.info("after tp1:" + sidcsOfflineAfterTp1);

        assertFalse(_dev.isOnline_());
    }

    /**
     * @return {@code true} if both collections have the exact same length and the same {@code SIndex}
     * objects
     */
    private boolean sidCollectionsIdentical(Collection<SIndex> sidcsToCheck, ImmutableSet<SIndex> sidcs)
    {
        // lazy check:
        //
        // 1. duplicates in sidcsToCheck will return 'false'
        // 2. duplicates and _missing_ elements (that somehow result in the correct number)
        //    will be caught by the block below

        if (sidcsToCheck.size() != sidcs.size()) return false;

        ImmutableSet<SIndex> sidsToCheckSet = ImmutableSet.copyOf(sidcsToCheck);
        return sidcs.equals(sidsToCheckSet);
    }

    /**
     * put both transports online; assume this works
     */
    private void putTp1AndTp2Online()
    {
        _dev.online_(_tp1, _tp1sids); // ignore return
        _dev.online_(_tp2, _tp2sids); // ignore return
    }
}
