package com.aerofs.lib;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.aerofs.base.BaseUtil;
import org.junit.Assert;

import org.junit.Test;

import com.aerofs.testlib.AbstractTest;

public class TestExternalSorter extends AbstractTest
{
    @Test
    public void shouldSortMockedDateSeqNameItems() throws Exception
    {
        ExternalSorter<Item> sorter = new ExternalSorter<Item>(Item.COMPARATOR);
        sorter.setMaxSize(1 << 8);
        final long count = 1 << 10;
        try {
            for (long i = 0; i < count; ++i) {
                Item item = newRandomItem();
                sorter.add(item);
            }
            ExternalSorter.Input<Item> it = sorter.sort();
            Item old = null;
            while (it.hasNext()) {
                Item item = it.next();
                if (old != null) {
                    Assert.assertTrue(Item.COMPARATOR.compare(old, item) <= 0);
                }
                old = item;
            }
        } finally {
            sorter.close();
        }
    }

    Random _random = Util.rand();
    Date _date = new Date();
    long _seq;

    public Item newRandomItem()
    {
        Item item = new Item();
        item._name = newRandomString(_random, 10);
        item._date = new Date(_date.getTime() - _random.nextInt((int)TimeUnit.DAYS.toMillis(7)));
        item._seq = _seq++;
        return item;
    }

    /**
     * A small class used as the element type for testing ExternalSorter
     */
    public static class Item implements Serializable
    {
        private static final long serialVersionUID = 1L;

        String _name;
        Date _date;
        long _seq;

        public static final Comparator<Item> COMPARATOR = new Comparator<Item>() {
            @Override
            public int compare(Item o1, Item o2)
            {
                int c;
                if ((c = BaseUtil.compare(o1._date, o2._date)) != 0) return c;
                if ((c = BaseUtil.compare(o1._name, o2._name)) != 0) return c;
                if ((c = BaseUtil.compare(o1._seq, o2._seq)) != 0) return c;
                return 0;
            }
        };
    }

    private static String newRandomString(Random r, int len)
    {
        char[] chars = new char[len];
        for (int i = 0; i < len; ++i) {
            chars[i] = (char)('a' + r.nextInt('z' - 'a' + 1));
        }
        return String.valueOf(chars);
    }
}
