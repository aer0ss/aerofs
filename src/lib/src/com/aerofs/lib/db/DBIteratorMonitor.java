package com.aerofs.lib.db;

import java.util.Map;

import com.aerofs.labeling.L;
import com.aerofs.lib.Util;
import com.google.common.collect.Maps;

public class DBIteratorMonitor
{
    static int s_count;

    static Map<AbstractDBIterator<?>, Exception> s_iters;

    public static void removeActiveIterator_(AbstractDBIterator<?> iter)
    {
        assert s_count > 0;
        s_count--;

        if (L.get().isStaging()) Util.verify(s_iters.remove(iter));
    }

    public static void addActiveIterator_(AbstractDBIterator<?> iter)
    {
        s_count++;

        if (L.get().isStaging()) {
            if (s_iters == null) s_iters = Maps.newHashMap();
            Exception e = new Exception();
            e.fillInStackTrace();
            Util.verify(s_iters.put(iter, e) == null);
        }
    }

    public static void assertNoActiveIterators_()
    {
        if (L.get().isStaging() && s_iters != null) {
            for (Exception e : s_iters.values()) {
                Util.l().warn("unclosed db iterator created at:\n" + Util.e(e));
            }
        }

        assert s_count == 0;
    }
}
