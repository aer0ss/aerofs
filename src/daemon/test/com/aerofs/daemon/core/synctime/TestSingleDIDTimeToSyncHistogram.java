/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.id.OID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Range;
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
public class TestSingleDIDTimeToSyncHistogram extends AbstractTest
{
    SingleDIDTimeToSyncHistogram histogram = new SingleDIDTimeToSyncHistogram();

    private final OID oid = OID.generate();
    private final TimeToSync tts;

    public TestSingleDIDTimeToSyncHistogram(long syncTimeMillis)
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

    /**
     * A shortcoming of this test is that it doesn't verify that the OIDs were correctly merged
     * for TTS values above the resolution threshold.
     */
    @Test
    public void whenMergingTwoIdenticalHistograms_FrequencyAtEachBinShouldDouble()
    {
        SingleDIDTimeToSyncHistogram histogram2 = new SingleDIDTimeToSyncHistogram();

        // Insert a number of sync times in each bin for both histograms
        for (int millis = 1000; millis < 10000 * 1000; millis <<= 1) {
            histogram.update_(oid, new TimeToSync(millis));
            histogram2.update_(oid, new TimeToSync(millis));
        }

        // Merge histogram2 into histogram
        histogram.mergeWith_(histogram2);

        for (int bin = 0; bin < histogram.size(); bin++) {
            assertEquals(2 * histogram2.frequencyAtBin(bin), histogram.frequencyAtBin(bin));
        }
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
        return Sets.difference(ContiguousSet.create(Range.closedOpen(0, TimeToSync.TOTAL_BINS),
                DiscreteDomain.integers()),
                excluded);
    }

}
