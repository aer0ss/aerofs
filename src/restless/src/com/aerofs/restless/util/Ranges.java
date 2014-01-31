package com.aerofs.restless.util;

import com.aerofs.base.ex.ExBadArgs;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

import javax.annotation.Nullable;
import javax.ws.rs.core.EntityTag;
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

    public static @Nullable RangeSet<Long> parseRanges(@Nullable String rangeset,
            @Nullable EntityTag ifRange, EntityTag etag, long length)
    {
        if (rangeset == null) return null;
        if (ifRange == null || etag.equals(ifRange)) {
            try {
                return Ranges.parse(rangeset, length);
            } catch (ExBadArgs e) {
                // RFC 2616: MUST ignore Range header if any range spec is syntactically invalid
            }
        }
        return null;
    }

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

    static Range<Long> range(String rangeSpec, long length) throws ExBadArgs
    {
        Matcher m = specPattern.matcher(rangeSpec);
        if (!m.matches()) throw new ExBadArgs("Invalid range spec: " + rangeSpec);
        String start = m.group(1);
        String end = m.group(2);
        long low, high;

        if (start.isEmpty()) {
            if (end.isEmpty()) throw new ExBadArgs("Invalid range spec: " + rangeSpec);
            // suffix range
            low = length - Long.parseLong(end);
            high = length;
        } else {
            low = Long.parseLong(start);
            if (end.isEmpty()) {
                high = length;
            } else {
                high = Long.parseLong(end) + 1;
                if (low >= high) throw new ExBadArgs("Invalid range spec: " + rangeSpec);
            }
        }

        // empty range to avoid polluting the range set
        if (low >= length) {
            return Range.closedOpen(0L, 0L);
        }

        return Range.closedOpen(low, high);
    }
}
