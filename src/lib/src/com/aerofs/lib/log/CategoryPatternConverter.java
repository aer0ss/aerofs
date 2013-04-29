/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.log;

import org.apache.log4j.helpers.FormattingInfo;
import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.spi.LoggingEvent;

class CategoryPatternConverter extends PatternConverter
{
    private static final String[] PKG_SUFFIXES = new String[] {
        "com.aerofs.daemon.core.",
        "com.aerofs.daemon.",
        "com.aerofs.",
    };
    private static final String[] PKG_SUFFIX_REPLACEMENTS = new String[] {
        "~",
        "!",
        "@",
    };

    private final int _precision;

    CategoryPatternConverter(FormattingInfo formattingInfo, int precision)
    {
        super(formattingInfo);
        this._precision = precision;
    }

    String getFullyQualifiedName(LoggingEvent event)
    {
        String name = event.getLoggerName();
        return makeShortName(name);
    }

    @Override
    public String convert(LoggingEvent event)
    {
        String n = getFullyQualifiedName(event);
        if (_precision <= 0) {
            return n;
        } else {
            int len = n.length();

            // We substract 1 from 'len' when assigning to 'end' to avoid out of
            // bounds exception in return r.substring(end+1, len). This can happen if
            // precision is 1 and the category name ends with a dot.
            int end = len - 1;
            for (int i = _precision; i > 0; i--) {
                end = n.lastIndexOf('.', end - 1);
                if (end == -1) return n;
            }
            return n.substring(end + 1, len);
        }
    }

    private static String makeShortName(String name)
    {
        for (int i = 0; i < PKG_SUFFIXES.length; i++) {
            if (name.startsWith(PKG_SUFFIXES[i])) {
                name = PKG_SUFFIX_REPLACEMENTS[i] +
                    name.substring(PKG_SUFFIXES[i].length());
                break;
            }
        }
        return name;
    }
}
