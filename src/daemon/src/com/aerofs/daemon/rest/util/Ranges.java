package com.aerofs.daemon.rest.util;

import com.aerofs.base.ex.ExBadArgs;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Byterange header parsing, as per RFC 2616
 */
public class Ranges
{
    private static final String BYTES_UNIT = "bytes=";
    private static final String RANGESET_SEP = ",";
    private static Pattern specPattern = Pattern.compile("([0-9]*)-([0-9]*)");

    public static RangeSet<Long> parse(String rangeSet, long length) throws ExBadArgs
    {
        if (!rangeSet.startsWith(BYTES_UNIT)) throw new ExBadArgs("Unsupported range unit");
        RangeSet<Long> ranges = TreeRangeSet.create();
        String[] rangeSpecs = rangeSet.substring(BYTES_UNIT.length()).split(RANGESET_SEP);
        for (String spec : rangeSpecs) {
            ranges.add(range(spec, length));
        }
        return ranges;
    }

    private static Range<Long> range(String rangeSpec, long length) throws ExBadArgs
    {
        Matcher m = specPattern.matcher(rangeSpec);
        if (!m.matches()) throw new ExBadArgs("Invalid range spec");
        String start = m.group(1);
        String end = m.group(2);
        long low, high;

        if (start.isEmpty()) {
            if (end.isEmpty()) throw new ExBadArgs("Invalid range spec");
            // suffix range
            low = length - Long.parseLong(end);
            high = length - 1;
        } else {
            low = Long.parseLong(start);
            // empty range to avoid polluting the range set
            if (low >= length) return Range.closedOpen(0L, 0L);

            high = end.isEmpty() ? length - 1 : bound(end, length);
        }

        if (low > high) throw new ExBadArgs("Invalid range spec");

        return Range.closed(low, high);
    }

    private static long bound(String num, long length)
    {
        return Math.max(0, Math.min(length - 1, Long.parseLong(num)));
    }
}
