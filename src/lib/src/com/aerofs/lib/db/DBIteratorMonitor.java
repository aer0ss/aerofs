package com.aerofs.lib.db;

import java.util.Map;

import com.aerofs.base.Loggers;
import com.aerofs.lib.Util;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

public class DBIteratorMonitor
{
    private static final Logger l = Loggers.getLogger(DBIteratorMonitor.class);

    static int s_count;

    static Map<AbstractDBIterator<?>, Exception> s_iters;

    // this flag can be enabled to have the client track where each active database iterator
    //   is created. It's an expensive operation used for consistency check and debugging.
    private static final boolean TRACKING_ENABLED = false;

    public static void removeActiveIterator_(AbstractDBIterator<?> iter)
    {
        assert s_count > 0;
        s_count--;

        if (TRACKING_ENABLED) checkNotNull(s_iters.remove(iter));
    }

    public static void addActiveIterator_(AbstractDBIterator<?> iter)
    {
        s_count++;

        if (TRACKING_ENABLED) {
            if (s_iters == null) s_iters = Maps.newHashMap();
            Exception e = new Exception();
            e.fillInStackTrace();
            Util.verify(s_iters.put(iter, e) == null);
        }
    }

    public static void assertNoActiveIterators_()
    {
        if (TRACKING_ENABLED) {
            if (s_iters != null) {
                for (Exception e : s_iters.values()) {
                    l.warn("unclosed db iterator created at:" + e);
                }
            }
        }

        assert s_count == 0;
    }
}
