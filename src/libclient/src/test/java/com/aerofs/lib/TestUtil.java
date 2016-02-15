/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.lib.FileUtil.FileName;
import org.junit.Ignore;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class TestUtil
{
    @Test
    public void shouldSplitFilename()
    {
        FileName result;

        // standard case
        result = FileName.fromBaseName("abc.def");
        assertEquals(result.base, "abc");
        assertEquals(result.extension, ".def");

        // no extension
        result = FileName.fromBaseName("abcdef");
        assertEquals(result.base, "abcdef");
        assertEquals(result.extension, "");

        // no name (.file)
        result = FileName.fromBaseName(".def");
        assertEquals(result.base, ".def");
        assertEquals(result.extension, "");

        // corner case: just a dot
        result = FileName.fromBaseName(".");
        assertEquals(result.base, ".");
        assertEquals(result.extension, "");

        // corner case: empty name
        result = FileName.fromBaseName("");
        assertEquals(result.base, "");
        assertEquals(result.extension, "");

        // several dots in name
        result = FileName.fromBaseName("ab.cd.ef");
        assertEquals(result.base, "ab.cd");
        assertEquals(result.extension, ".ef");

        // counter-intuitive result, but technically correct
        result = FileName.fromBaseName("..abc");
        assertEquals(result.base, ".");
        assertEquals(result.extension, ".abc");
    }

    // powermock doesn't like java 8
    @Ignore
    @Test
    public void shouldFormatTimeBasedOnLocalTimezone()
    {
        TimeZone timeZone = TimeZone.getTimeZone("GMT-8:00");
        Calendar cal = Calendar.getInstance(timeZone, Locale.US); // PST
        cal.set(2013, Calendar.JULY, 13, 23, 59);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long now = cal.getTimeInMillis();

        //PowerMockito.mockStatic(TimeZone.class);
        //when(TimeZone.getDefault()).thenReturn(timeZone);

        //PowerMockito.mockStatic(System.class);
        //when(System.currentTimeMillis()).thenReturn(now);

        SimpleDateFormat tomorrowFormatter = new SimpleDateFormat("MMM d, yyyy h:mm a");
        SimpleDateFormat todayFormatter = new SimpleDateFormat("h:mm a");
        SimpleDateFormat yesterdayFormatter = new SimpleDateFormat("'Yesterday' h:mm a");
        SimpleDateFormat dayBeforeYesterdayFormatter = new SimpleDateFormat("MMM d h:mm a");

        for (int i = 0; i < 23; i++) {
            long tomorrow = getLocalTime(cal, 2013, 6, 14, i, 30);
            assertEquals(tomorrowFormatter.format(tomorrow),
                    Util.formatAbsoluteTime(tomorrow));

            long today = getLocalTime(cal, 2013, 6, 13, i, 30);
            assertEquals(todayFormatter.format(today),
                    Util.formatAbsoluteTime(today));

            long yesterday = getLocalTime(cal, 2013, 6, 12, i, 30);
            assertEquals(yesterdayFormatter.format(yesterday),
                    Util.formatAbsoluteTime(yesterday));

            long dayBeforeYesterday = getLocalTime(cal, 2013, 6, 11, i, 30);
            assertEquals(dayBeforeYesterdayFormatter.format(dayBeforeYesterday),
                    Util.formatAbsoluteTime(dayBeforeYesterday));
        }
    }

    private long getLocalTime(Calendar cal, int year, int month, int day, int hour, int minute)
    {
        cal.set(year, month, day, hour, minute);
        return cal.getTimeInMillis();
    }
}
