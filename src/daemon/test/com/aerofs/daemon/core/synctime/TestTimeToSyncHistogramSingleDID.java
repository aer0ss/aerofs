/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.id.OID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.DiscreteDomains;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Ranges;
import com.google.common.collect.Sets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

@RunWith(value = Parameterized.class)
public class TestTimeToSyncHistogramSingleDID extends AbstractTest
{
    TimeToSyncHistogramSingleDID histogram = new TimeToSyncHistogramSingleDID();

    private final OID oid = OID.generate();
    private final TimeToSync tts;

    public TestTimeToSyncHistogramSingleDID(long syncTimeMillis)
    {
        tts = new TimeToSync(syncTimeMillis);
    }

    @Parameters
    public static Collection<Object[]> data()
    {
        return Arrays.asList(new Object[][] {
                {1},                       // sync time below 1 second
                {Integer.MAX_VALUE},       // sync time in the maximum bin
                {10000},                   // a non-boundary value
        });
    }

    @Test
    public void whenAddingOneSyncTime_ShouldHaveOneElementInAppropriateBin()
    {
        histogram.update_(oid, tts);

        assertEquals(1, histogram.frequencyAtBin(tts.toBinIndex()));

        assertAllBinsEmptyExcluding(tts.toBinIndex());
    }

    @Test
    public void whenAddingTwoIdenticalSyncTimesWithDifferentOIDs_ShouldHaveTwoElementsInBin()
    {
        OID oid2 = OID.generate();

        histogram.update_(oid, tts);
        histogram.update_(oid2, tts);

        assertEquals(2, histogram.frequencyAtBin(tts.toBinIndex()));
        assertAllBinsEmptyExcluding(tts.toBinIndex());
    }

    @Test
    public void whenAddingTwoDifferingSyncTimes_ShouldHaveOneElementInEachBin()
    {
        // Test with a TimeToSync not equal to the parameterized value
        TimeToSync tts2 = new TimeToSync(1000000);
        assertNotEquals(tts.toBinIndex(), tts2.toBinIndex());

        histogram.update_(oid, tts);
        histogram.update_(oid, tts2);

        for (TimeToSync t : ImmutableSet.of(tts, tts2)) {
            assertEquals(1, histogram.frequencyAtBin(t.toBinIndex()));
        }

        assertAllBinsEmptyExcluding(tts.toBinIndex(), tts2.toBinIndex());
    }

    private void assertAllBinsEmptyExcluding(Integer ... bins)
    {
        for (Integer b : allBinsExcluding(bins)) {
            assertEquals(0, histogram.frequencyAtBin(b));
        }
    }

    private Set<Integer> allBinsExcluding(Integer... bins)
    {
        Set<Integer> excluded = ImmutableSet.copyOf(Iterators.forArray(bins));
        return Sets.difference(
                Ranges.closedOpen(0, TimeToSync.TOTAL_BINS).asSet(DiscreteDomains.integers()),
                excluded);
    }

}
