/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.restless.util;

import com.google.common.collect.Range;

import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

public class ContentRange
{
    private final static String ANY = "*";
    private final static Pattern specPattern =
            Pattern.compile("bytes (\\*|[0-9]+-[0-9]+)/(\\*|[0-9]+)");

    // null when given "*"
    private final @Nullable Range<Long> _range;
    private final @Nullable Long _length;

    public ContentRange(String s)
    {
        Matcher m = specPattern.matcher(s);
        checkArgument(m.matches());
        _range = range(m.group(1));
        _length = instanceLength(m.group(2));
    }

    public @Nullable Range<Long> range()
    {
        return _range;
    }

    public @Nullable Long totalLength()
    {
        return _length;
    }

    private @Nullable Long instanceLength(String l)
    {
        return l.equals(ANY) ? null : Long.parseLong(l);
    }

    private static Range<Long> range(String r)
    {
        if (r.equals(ANY)) return null;
        String[] tmp = r.split("-");
        checkArgument(tmp.length == 2);
        return Range.closedOpen(Long.parseLong(tmp[0]), Long.parseLong(tmp[1]) + 1);
    }

    @Override
    public String toString()
    {
        return (_range != null ? _range : "*") + "/" + (_length != null ? _length : "*");
    }
}
