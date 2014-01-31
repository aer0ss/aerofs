/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.restless.util;

import com.aerofs.base.ex.ExBadArgs;
import com.google.common.collect.Range;

import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContentRange
{
    private final static String ANY = "*";
    private final static Pattern specPattern =
            Pattern.compile("bytes (\\*|[0-9]*-[0-9]*)/(\\*|[0-9]*)");

    private boolean _valid;

    // null when given "*"
    private @Nullable Range<Long> _range;
    private @Nullable Long _length;

    public ContentRange(String s)
    {
        Matcher m = specPattern.matcher(s);
        _valid = m.matches();
        if (!_valid) return;

        String l = m.group(2);
        String r = m.group(1);
        try {
            _length = l.equals(ANY) ? null : Long.valueOf(l);
        } catch (NumberFormatException e) {
            _valid = false;
            return;
        }

        try {
            _range = r.equals(ANY) ? null : Ranges.range(r, _length);
        } catch (ExBadArgs e) {
            _valid = false;
        }
    }

    public boolean isValid()
    {
        return _valid;
    }

    public @Nullable Range<Long> range()
    {
        return _range;
    }

    public @Nullable Long totalLength()
    {
        return _length;
    }
}
